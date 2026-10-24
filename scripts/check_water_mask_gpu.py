#!/usr/bin/env python3
"""Run only the real native Sable water depth-pass acceptance, not the full mod."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VEIL = ROOT.parent / 'veil'
CACHE = Path.home() / '.gradle/caches'
MODULES = CACHE / 'modules-2/files-2.1'
LOOM = CACHE / 'fabric-loom'
WORK = ROOT / 'build/water-mask-gpu-check'
CLASSES = WORK / 'classes'
CLASSES.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'

def artifact(group, name, version):
    return next((MODULES / group / name / version).glob('*/' + name + '-' + version + '.jar'))

metadata = json.loads((LOOM / '26.3/mojang_minecraft_info.json').read_text())
mc = LOOM / 'minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar'
cp = [mc]
for library in metadata['libraries']:
    group, name, version, *_ = library['name'].split(':')
    matches = list((MODULES / group / name / version).glob('*/' + Path(library['downloads']['artifact']['path']).name))
    cp.extend(matches[:1])
fabric = artifact('net.fabricmc.fabric-api', 'fabric-api', '0.163.0+26.3')
cp += [artifact('net.fabricmc', 'fabric-loader', '0.19.5'), artifact('org.jetbrains', 'annotations', '26.0.2'),
       VEIL / 'local-maven/gg/moonflower/molang-compiler/3.1.1.19/molang-compiler-3.1.1.19.jar',
       VEIL / 'local-maven/io/github/ocelot/glsl-processor/0.2.3/glsl-processor-0.2.3.jar']
modules = WORK / 'fabric-api'
modules.mkdir(exist_ok=True)
with zipfile.ZipFile(fabric) as archive:
    for entry in archive.namelist():
        if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
            path = modules / Path(entry).name
            if not path.exists(): path.write_bytes(archive.read(entry))
            cp.append(path)
native = VEIL / 'build/gpu-verification/classes'
if not (native / 'foundry/veil/api/client/render/gpu/GpuShaderProgram.class').is_file():
    raise SystemExit('Compile the genuine Veil native GPU boundary first.')
sources = [ROOT / 'common/src/main/java/dev/ryanhcode/sable/render/water_occlusion/WaterMaskPass.java',
           ROOT / 'src/test/java/dev/ryanhcode/sable/render/water_occlusion/WaterMaskGpuCheck.java']
subprocess.run([str(JAVA / 'javac'), '--release', '25', '-proc:none', '-implicit:none', '-cp',
                os.pathsep.join(map(str, [native, *cp])), '-d', str(CLASSES), *map(str, sources)], check=True)
jar = WORK / 'sable-water-mask-acceptance.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as output:
    output.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'sable_water_mask_acceptance',
        'version': '1.0.0', 'name': 'Sable water mask GPU acceptance (test only)', 'environment': 'client',
        'entrypoints': {'client': ['dev.ryanhcode.sable.render.water_occlusion.WaterMaskGpuCheck']},
        'depends': {'minecraft': '26.3', 'fabricloader': '>=0.19.5', 'fabric-api': '*'}}))
    for directory in [native, CLASSES]:
        for path in directory.rglob('*.class'): output.write(path, path.relative_to(directory))
    for path in (ROOT / 'common/src/main/resources/assets/sable/shaders/core').glob('water_mask.*'):
        output.write(path, 'assets/sable/shaders/core/' + path.name)
    output.write(ROOT / 'LICENSE.md', 'LICENSE_Sable')
for name in ['asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util']:
    cp.append(artifact('org.ow2.asm', name, '9.10.1'))
cp += [artifact('net.fabricmc', 'sponge-mixin', '0.17.4+mixin.0.8.7'),
       artifact('io.github.llamalad7', 'mixinextras-fabric', '0.5.5'), fabric, jar]
run = WORK / 'run'
run.mkdir(exist_ok=True)
report = WORK / 'runtime.json'
report.unlink(missing_ok=True)
asset_index = '26.3-' + metadata['assetIndex']['id']
if not (LOOM / 'assets/indexes' / (asset_index + '.json')).is_file(): asset_index = metadata['assetIndex']['id']
args = ['-XstartOnFirstThread', '-Xmx2G', '-Dfabric.development=true',
        '-Dfabric.defaultModDistributionNamespace=official', '-Dfabric.defaultMixinRemapType=static',
        '-Dfabric.gameJarPath=' + str(mc), '-Dsable.water.mask.report=' + str(report),
        '-classpath', os.pathsep.join(map(str, cp)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--version', '26.3', '--username', 'SableWaterTest', '--accessToken', '0', '--gameDir', str(run),
        '--assetsDir', str(LOOM / 'assets'), '--assetIndex', asset_index, '--graphicsBackend', 'vulkan', '--width', '480', '--height', '320']
argfile = WORK / 'client.args'
argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in args))
print('Launching native water-mask component check on the real 26.3 Vulkan client; full Sable is not loaded.', flush=True)
subprocess.run([str(JAVA / 'java'), '@' + str(argfile)], cwd=run, timeout=180, check=True)
result = json.loads(report.read_text())
if result.get('status') != 'passed': raise SystemExit('Water mask GPU check failed: ' + str(result))
inputs = sources + [Path(__file__).resolve()] + list((ROOT / 'common/src/main/resources/assets/sable/shaders/core').glob('water_mask.*'))
manifest = {'check': 'water-mask-gpu-check', 'result': 'PASS', 'cases': 3, 'minecraft': '26.3',
            'scope': 'actual native Vulkan depth-only mask passes; not full water/world shader integration or full Sable startup',
            'proof': 'build/water-mask-gpu-check.log', 'runtime': result,
            'inputs': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}}
(ROOT / 'build/water-mask-gpu-check-inputs.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Actual Sable water mask GPU check passed:', json.dumps(result), flush=True)
