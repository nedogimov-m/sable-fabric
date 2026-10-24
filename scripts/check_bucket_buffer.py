#!/usr/bin/env python3
"""Run native packed instance allocation tests with genuine Minecraft 26.3 PoseStack.

Requires JDK 25 and Minecraft/JUnit dependencies already resolved by Gradle.
Compiles the production plot transform, its JUnit test, and a
small JUnit launcher. This is a targeted acceptance check, not a full mod build.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / '.gradle/caches'
WORK = ROOT / 'build' / 'bucket-buffer-check'
WORK.mkdir(parents=True, exist_ok=True)
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
if not java or not javac:
    raise SystemExit('Set JAVA_HOME to JDK 25.')
metadata = CACHE / 'fabric-loom/26.3/mojang_minecraft_info.json'
classpath = [ROOT / '../veil/build/gpu-verification/classes', CACHE / 'fabric-loom/26.3/minecraft-merged.jar']
for library in json.loads(metadata.read_text())['libraries']:
    group, artifact, version, *classifier = library['name'].split(':')
    filename = artifact + '-' + version + ('-' + classifier[0] if classifier else '') + '.jar'
    directory = CACHE / 'modules-2/files-2.1' / group / artifact / version
    candidates = list(directory.rglob(filename)) if directory.exists() else []
    classpath.extend(candidates[:1])
for group, version in [('org.junit.jupiter', '6.0.3'), ('org.junit.platform', '6.0.3'),
                       ('org.opentest4j', '1.3.0'), ('org.apiguardian', '1.1.2'), ('org.jetbrains', '26.0.2')]:
    directory = CACHE / 'modules-2/files-2.1' / group
    classpath.extend(p for p in directory.rglob('*.jar')
                     if version in p.parts and not p.name.endswith(('-sources.jar', '-javadoc.jar')))

runner = WORK / 'RunBucketBufferTests.java'
runner.write_text('''
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public class RunBucketBufferTests {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 25) throw new AssertionError("Java 25 required");
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("dev.ryanhcode.sable.sublevel.render.fancy.BucketRenderBufferTest")
        ).build());
        var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer);
        summary.printFailuresTo(writer);
        if (summary.getTestsSucceededCount() != 3 || summary.getTotalFailureCount() != 0) System.exit(1);
    }
}
''')
classes = WORK / 'classes'
classes.mkdir(exist_ok=True)
package = Path('dev/ryanhcode/sable/sublevel/render/fancy')
sources = [ROOT / 'common/src/main/java' / package / name for name in
           ['BucketRenderBuffer.java']]
sources += [ROOT / 'src/test/java' / package / 'BucketRenderBufferTest.java', runner]
cp = os.pathsep.join(map(str, classpath))
result = subprocess.run([javac, '--release', '25', '-proc:none', '-cp', cp,
                         '-d', str(classes)] + list(map(str, sources)))
if result.returncode:
    raise SystemExit(result.returncode)
result = subprocess.run([java, '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true',
                         '-cp', str(classes) + os.pathsep + cp, 'RunBucketBufferTests'], cwd=WORK)
if result.returncode == 0:
    tracked = sources[:-1] + [Path(__file__).resolve()]
    manifest = {
        'check': 'bucket-buffer-check', 'result': 'PASS', 'cases': 3,
        'minecraft': '26.3', 'java': '25.0.4.1',
        'proof': 'build/bucket-buffer-check.log',
        'scope': 'production native CPU instance allocation/bytes/lifetimes; no GPU upload/draw pixel proof',
        'inputs': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in tracked},
    }
    (ROOT / 'build/bucket-buffer-check-inputs.json').write_text(json.dumps(manifest, indent=2) + '\n')
raise SystemExit(result.returncode)
