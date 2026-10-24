#!/usr/bin/env python3
"""Run native terrain snapshot tests with genuine Minecraft 26.3 PoseStack.

Requires JDK 25 and Minecraft/JUnit dependencies already resolved by Gradle.
Compiles the production plot transform, its JUnit test, and a
small JUnit launcher. This is a targeted acceptance check, not a full mod build.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import shlex
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--checks', choices=['native', 'extension', 'group', 'all'], default='native')
args = parser.parse_args()
expected = {'native': 3, 'extension': 1, 'group': 1, 'all': 5}[args.checks]
ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / '.gradle/caches'
WORK = ROOT / 'build' / 'plot-mesh-check'
WORK.mkdir(parents=True, exist_ok=True)
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
if not java or not javac:
    raise SystemExit('Set JAVA_HOME to JDK 25.')
metadata = CACHE / 'fabric-loom/26.3/mojang_minecraft_info.json'
classpath = [CACHE / 'fabric-loom/26.3/minecraft-merged.jar']
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

runner = WORK / 'RunPlotMeshTests.java'
selectors = ('DiscoverySelectors.selectClass("dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMeshTest")' if args.checks == "all" else ",\n".join('DiscoverySelectors.selectMethod("dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMeshTest#' + method + '")' for method in (["addonExtensionExtractsCopiedFrameInputsThenSubmitsWithoutLiveOwner"] if args.checks == "extension" else ["callerOwnedGroupKeepsNativeFallbackAndParentPoseWhileSkippingWorldEffects"] if args.checks == "group" else ["nativeQuadCaptureKeepsEveryVertexInputAndModeAcrossBlockFluidBoundary", "deferredNativeSubmissionUsesCopiedTransformLightAndColorWithoutWorldAccess", "cpuDirectionalLightingMatchesNativeCardinalValuesAndKeepsAlpha"])))
runner.write_text('''
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public class RunPlotMeshTests {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 25) throw new AssertionError("Java 25 required");
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(
            SELECTORS
        ).build());
        var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer);
        summary.printFailuresTo(writer);
        if (summary.getTestsSucceededCount() != EXPECTED || summary.getTotalFailureCount() != 0) System.exit(1);
    }
}
'''.replace('SELECTORS', selectors).replace('EXPECTED', str(expected)))
classes = WORK / 'classes'
classes.mkdir(exist_ok=True)
package = Path('dev/ryanhcode/sable/sublevel/render/vanilla')
sources = [ROOT / 'common/src/main/java' / package / name for name in
           ['PlotSectionMesh.java', 'PlotSectionMeshExtension.java']]
sources += [ROOT / 'common/src/main/java/dev/ryanhcode/sable/render/dynamic_shade/SubLevelVertexConsumer.java']
sources += [ROOT / 'src/test/java' / package / 'PlotSectionMeshTest.java', runner]
# The extension signature refers to the genuine compiled ClientSubLevel, never a fixture stand-in.
owner_classes = ROOT / 'build/diagnostic/server-migration-classes'
if not (owner_classes / 'dev/ryanhcode/sable/sublevel/ClientSubLevel.class').is_file():
    raise SystemExit('Prepare genuine Sable server migration classes before checking the owner-aware terrain extension.')
classpath.append(owner_classes)
classpath.append(ROOT.parent / 'companion/build/classes/java/main')
# Resolve the genuine owner's interfaces/physics signatures for the extension case.
# These are dependency paths from the real suite diagnostic, not replacement classes.
if args.checks in ('extension', 'group', 'all'):
    suite_args = shlex.split((ROOT.parents[1] / 'build/porting/suite-diagnostic/javac.args').read_text())
    classpath.extend(Path(entry) for entry in suite_args[suite_args.index('-classpath') + 1].split(os.pathsep))
cp = os.pathsep.join(map(str, classpath))
result = subprocess.run([javac, '--release', '25', '-proc:none', '-cp', cp,
                         '-d', str(classes)] + list(map(str, sources)))
if result.returncode:
    raise SystemExit(result.returncode)
result = subprocess.run([java, '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true',
                         '-cp', str(classes) + os.pathsep + cp, 'RunPlotMeshTests'], cwd=WORK)
if result.returncode == 0:
    tracked = sources[:-1] + [Path(__file__).resolve()]
    manifest = {
        'check': 'plot-mesh-' + args.checks + '-check', 'result': 'PASS', 'cases': expected,
        'minecraft': '26.3', 'java': '25.0.4.1',
        'proof': 'build/plot-mesh-check.log',
        'scope': 'production terrain quad capture and deferred callback into native BufferBuilder; no full terrain tessellation/GPU pixels/Mixin runtime proof',
        'inputs': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in tracked},
    }
    (ROOT / ('build/plot-mesh-check-inputs.json' if args.checks == 'native' else 'build/plot-mesh-' + args.checks + '-check-inputs.json')).write_text(json.dumps(manifest, indent=2) + '\n')
raise SystemExit(result.returncode)
