#!/usr/bin/env python3
"""Run only Sable's real native world shader/default-resource bridge on the 26.3 client."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VEIL = ROOT.parent / 'veil'
CACHE = Path.home() / '.gradle/caches'
MODULES = CACHE / 'modules-2/files-2.1'
LOOM = CACHE / 'fabric-loom'
WORK = ROOT / 'build/native-world-shaders-gpu-check'
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
    cp.extend(list((MODULES / group / name / version).glob('*/' + Path(library['downloads']['artifact']['path']).name))[:1])
fabric = artifact('net.fabricmc.fabric-api', 'fabric-api', '0.163.0+26.3')
cp += [artifact('net.fabricmc', 'fabric-loader', '0.19.5'), artifact('org.jetbrains', 'annotations', '26.0.2'),
       VEIL / 'local-maven/io/github/ocelot/glsl-processor/0.2.3/glsl-processor-0.2.3.jar']
modules = WORK / 'fabric-api'
modules.mkdir(exist_ok=True)
with zipfile.ZipFile(fabric) as archive:
    for entry in archive.namelist():
        if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
            path = modules / Path(entry).name
            path.write_bytes(archive.read(entry))
            cp.append(path)
native = VEIL / 'build/gpu-verification/classes'
sable = ROOT / 'build/diagnostic/server-migration-classes'
if not (sable / 'dev/ryanhcode/sable/render/SableNativeWorldShaders.class').is_file():
    raise SystemExit('Run the complete Sable production diagnostic compile first; this test never substitutes game/mod classes.')
sources = [ROOT / 'src/test/java/dev/ryanhcode/sable/render/NativeWorldShaderGpuCheck.java']
subprocess.run([str(JAVA / 'javac'), '--release', '25', '-proc:none', '-implicit:none', '-cp',
                os.pathsep.join(map(str, [native, sable, *cp])), '-d', str(CLASSES), *map(str, sources)], check=True)
jar = WORK / 'sable-native-world-shaders-acceptance.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as output:
    test = 'dev.ryanhcode.sable.render.NativeWorldShaderGpuCheck'
    output.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'sable_native_world_shaders_acceptance',
        'version': '1.0.0', 'name': 'Sable native world shaders acceptance (test only)', 'environment': 'client',
        'entrypoints': {'main': [test], 'client': [test]}, 'mixins': ['native-world-bridge.mixins.json'],
        'depends': {'minecraft': '26.3', 'fabricloader': '>=0.19.5', 'fabric-api': '*'}}))
    output.writestr('native-world-bridge.mixins.json', json.dumps({'required': True, 'minVersion': '0.8',
        'package': 'foundry.veil.mixin.shader.client', 'compatibilityLevel': 'JAVA_25',
        'client': ['NativePipelineCacheMixin', 'NativeShaderManagerMixin', 'NativeFrontendRenderPassMixin'],
        'injectors': {'defaultRequire': 1}}))
    for directory in [native, sable, CLASSES]:
        for path in directory.rglob('*.class'): output.write(path, path.relative_to(directory))
    output.write(ROOT / 'LICENSE.md', 'LICENSE_Sable')
    output.write(VEIL / 'LICENSE', 'LICENSE_Veil')
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
        '-Dfabric.gameJarPath=' + str(mc), '-Dsable.world.shaders.report=' + str(report),
        '-classpath', os.pathsep.join(map(str, cp)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--version', '26.3', '--username', 'SableWorldShaderTest', '--accessToken', '0', '--gameDir', str(run),
        '--assetsDir', str(LOOM / 'assets'), '--assetIndex', asset_index, '--graphicsBackend', 'vulkan', '--width', '480', '--height', '320']
argfile = WORK / 'client.args'
argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in args))
inputs = sources + [Path(__file__).resolve()]
for name in ['SableClient.java', 'render/NativeShaderDefaults.java', 'render/SableNativeWorldShaders.java',
             'render/water_occlusion/WaterOcclusionRenderer.java', 'render/water_occlusion/SableWaterOcclusionPreProcessor.java',
             'render/sky_light_shadow/SableSkyLightShadows.java', 'render/sky_light_shadow/SableDynamicSkyLightShadowPreProcessor.java',
             'mixin/water_occlusion/GameRendererMixin.java']:
    inputs.append(ROOT / 'common/src/main/java/dev/ryanhcode/sable' / name)
record = {'check': 'native-world-shaders-gpu-check', 'minecraft': '26.3',
          'scope': 'real production Sable processors, native bridge, disabled-resource lifecycle and vanilla block/OIT pipelines; not full Sable mod startup',
          'inputs': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs},
          'compiled': {str(directory): {str(path.relative_to(directory)): hashlib.sha256(path.read_bytes()).hexdigest()
                                      for path in sorted(directory.rglob('*.class'))} for directory in [native, sable, CLASSES]},
          'classpath': {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in cp}}
print('Launching only the real production Sable native world shader/default-resource integration on Vulkan; full Sable initialization/mixins are not loaded.', flush=True)
subprocess.run([str(JAVA / 'java'), '@' + str(argfile)], cwd=run, timeout=180, check=True)
result = json.loads(report.read_text())
record['runtime'] = result
record['result'] = 'PASS' if result.get('status') == 'passed' else 'FAIL'
(WORK / 'inputs.json').write_text(json.dumps(record, indent=2) + '\n')
if result.get('status') != 'passed': raise SystemExit('Native world shader check failed: ' + str(result))
print('Actual Sable native world shader/default-resource GPU check passed:', json.dumps(result), flush=True)
