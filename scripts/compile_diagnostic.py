#!/usr/bin/env python3
"""Compile every Sable common/Fabric/Rapier source against genuine local APIs.

Diagnostic only: Veil's old release is a compile API until its full port builds.
Minecraft access widening uses Fabric's actual ClassTweaker implementation.
No replacement classes or source filtering are used. A successful invocation
would still not replace the Loom mod build, mixin validation or game tests.
"""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import zipfile
import json
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / '.gradle/caches'
WORK = ROOT / 'build/diagnostic'
WORK.mkdir(parents=True, exist_ok=True)
java_home = Path(os.environ['JAVA_HOME'])
java, javac = str(java_home / 'bin/java'), str(java_home / 'bin/javac')
modules = CACHE / 'modules-2/files-2.1'

def artifact(group, name, version):
    return next((modules / group / name / version).rglob(name + '-' + version + '.jar'))

mc = CACHE / 'fabric-loom/26.3/minecraft-merged.jar'
aw = ROOT / 'common/src/main/resources/sable.accesswidener'
transformed = WORK / 'minecraft-26.3-sable-access.jar'
digest = hashlib.sha256(aw.read_bytes() + str(mc.stat().st_mtime_ns).encode()).hexdigest()
cache_key = WORK / 'minecraft-access.key'
if not transformed.exists() or not cache_key.exists() or cache_key.read_text() != digest:
    driver = WORK / 'ApplySableAccess.java'
    driver.write_text('''
import java.nio.file.*;
import java.util.jar.*;
import net.fabricmc.classtweaker.api.*;
import org.objectweb.asm.*;
public class ApplySableAccess {
    public static void main(String[] args) throws Exception {
        var tweaker = ClassTweaker.newInstance();
        ClassTweakerReader.create(tweaker).read(Files.readAllBytes(Path.of(args[1])), "official");
        int changed = 0;
        try (var source = new JarFile(args[0]); var output = new JarOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var bytes = source.getInputStream(entry).readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    String owner = entry.getName().substring(0, entry.getName().length() - 6);
                    if (tweaker.getTargets().contains(owner) || tweaker.getTargets().contains(owner.replace('/', '.'))) {
                        var writer = new ClassWriter(0);
                        new ClassReader(bytes).accept(tweaker.createClassVisitor(Opcodes.ASM9, writer,
                            (name, generated) -> { throw new IllegalStateException("Unexpected generated class " + name); }), 0);
                        bytes = writer.toByteArray();
                        changed++;
                    }
                }
                output.putNextEntry(new JarEntry(entry.getName()));
                output.write(bytes);
                output.closeEntry();
            }
        }
        System.out.println("Applied actual Sable access widener to " + changed + " Minecraft classes");
    }
}
''')
    cp = [artifact('net.fabricmc', 'class-tweaker', '0.3.0')]
    cp += list((modules / 'org.ow2.asm').glob('*/9.10.1/*/*.jar'))
    cp = os.pathsep.join(map(str, cp))
    subprocess.run([javac, '--release', '25', '-proc:none', '-cp', cp, '-d', str(WORK), str(driver)], check=True)
    subprocess.run([java, '-cp', str(WORK) + os.pathsep + cp, 'ApplySableAccess', str(mc), str(aw), str(transformed)], check=True)
    cache_key.write_text(digest)

# Minecraft's exact libraries and current Fabric modules precede old optional APIs.
api = artifact('net.fabricmc.fabric-api', 'fabric-api', '0.163.0+26.3')
api_dir = WORK / 'fabric-api-26.3'
api_dir.mkdir(exist_ok=True)
with zipfile.ZipFile(api) as bundle:
    for entry in bundle.namelist():
        if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
            destination = api_dir / Path(entry).name
            if not destination.exists(): destination.write_bytes(bundle.read(entry))
mc_libraries = []
for library in json.loads((CACHE / 'fabric-loom/26.3/mojang_minecraft_info.json').read_text())['libraries']:
    group, name, version, *classifier = library['name'].split(':')
    if classifier: continue
    try: mc_libraries.append(artifact(group, name, version))
    except StopIteration: pass

# Preserve the original exact diagnostic artifact list; put the genuine target
# Minecraft and current Config API ahead of earlier cached versions.
old_args = (WORK / 'javac.args').read_text().splitlines()
previous_cp = old_args[old_args.index('-classpath') + 1].split(os.pathsep)
cp = [transformed, ROOT / '../veil/build/gpu-verification/classes', ROOT / '../veil/build/rendertype-verification/classes', ROOT / '../veil/build/core-api-verification/classes', ROOT / 'build/forgeconfigapiport-fabric-26.3.1.jar',
      ROOT / 'build/forgeconfigapiport-common-neoforgeapi-26.3.1.jar']
cp += mc_libraries + list(api_dir.glob('*.jar'))
cp += sorted((WORK / 'target-api').glob('*.jar'))
cp += sorted((WORK / 'optional-api').glob('*.jar'))
cp += [Path(p) for p in previous_cp if p != str(mc) and '/maven.modrinth/iris/' not in p]
sources = []
for directory in ['common/src/main/java', 'fabric/src/main/java', 'sable_rapier/src/main/java']:
    sources.extend(sorted((ROOT / directory).rglob('*.java')))
classes = WORK / 'server-migration-classes'
classes.mkdir(exist_ok=True)
args = ['--release', '25', '-proc:none', '-Xmaxerrs', '10000', '-d', str(classes), '-classpath',
        os.pathsep.join(map(str, cp))] + list(map(str, sources))
argfile = WORK / 'server-migration.args'
argfile.write_text('\n'.join(args))
log = WORK / (sys.argv[1] if len(sys.argv) > 1 else 'server-migration-javac.log')
run_args = log.with_suffix('.args')
run_args.write_text(argfile.read_text())

def sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def inventory(path):
    path = path.resolve()
    if path.is_file(): return {'sha256': sha256(path), 'size': path.stat().st_size}
    if path.is_dir():
        files = {str(file.relative_to(path)): sha256(file) for file in sorted(path.rglob('*.class'))}
        return {'class_count': len(files), 'classes': files}
    return {'missing': True}

def role(path):
    name = str(path)
    if path == transformed: return 'genuine Minecraft 26.3 with production access widener'
    if any('/' + boundary + '/' in name for boundary in ('gpu-verification', 'rendertype-verification', 'core-api-verification')):
        return 'genuine ported Veil native boundary classes; not a full mod artifact'
    if '/target-api/' in name: return 'genuine target-version optional compile API; runtime integration unverified'
    if '/optional-api/' in name: return 'genuine historical optional compile API; not a 26.3 integration'
    if '/fabric-api-26.3/' in name: return 'genuine Fabric API 0.163.0+26.3 module'
    if '/ports/companion/build/libs/' in name: return 'genuine built local Companion 26.3 mod'
    if 'forgeconfigapiport' in name: return 'genuine ForgeConfigAPIPort 26.3.1 compile boundary'
    if 'upstream-diagnostic-only' in name: return 'historical Veil fallback compile API; NOT a 26.3 mod'
    if path in mc_libraries: return 'genuine Minecraft 26.3 dependency'
    return 'baseline shared/historical compile dependency; provenance retained, no target integration claim'

# The live native directories are real ported outputs, not a substitute Veil mod.
classpath_inventory = {}
for path in cp:
    resolved = str(path.resolve())
    if resolved not in classpath_inventory: classpath_inventory[resolved] = inventory(path)
manifest = {
    'check': 'full-source-javac-diagnostic', 'started_utc': datetime.now(timezone.utc).isoformat(),
    'scope': 'All production sources; not a Loom mod artifact, dependency runtime proof or startup test',
    'minecraft': '26.3', 'source_count': len(sources),
    'sources': {str(path.relative_to(ROOT)): sha256(path) for path in sources},
    'args': run_args.name, 'args_sha256': sha256(run_args),
    'access_widener_sha256': sha256(aw), 'driver_sha256': sha256(Path(__file__)),
    'classpath_order': [{'path': str(path.resolve()), 'role': role(path)} for path in cp],
    'classpath_inventory': classpath_inventory, 'java_home': str(java_home),
    'java_version': subprocess.run([java, '-version'], capture_output=True, text=True).stderr.strip(),
}
with log.open('w') as output:
    result = subprocess.run([javac, '@' + str(run_args)], stdout=output, stderr=subprocess.STDOUT)
manifest['result'] = 'PASS' if result.returncode == 0 else 'FAIL'
manifest['exit_code'] = result.returncode
manifest['finished_utc'] = datetime.now(timezone.utc).isoformat()
manifest['source_changes_during_compile'] = [str(path.relative_to(ROOT)) for path in sources
    if sha256(path) != manifest['sources'][str(path.relative_to(ROOT))]]
manifest['classpath_changes_during_compile'] = [str(path.resolve()) for path in cp
    if path.is_dir() and inventory(path) != classpath_inventory[str(path.resolve())]]
log.with_suffix('.inputs.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Genuine full-source diagnostic:', len(sources), 'sources; exit', result.returncode, '; log', log)
raise SystemExit(result.returncode)
