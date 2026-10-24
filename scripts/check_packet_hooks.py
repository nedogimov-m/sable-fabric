#!/usr/bin/env python3
"""Apply real production packet mixins with Fabric Loader and test their wire codecs.

This isolated loader check is not a full Sable startup. It compiles the four
production packet mixins, their extension/codec helper and a test initializer.
Minecraft and Fabric/Mixin bytecode are genuine; no replacement game classes.
The initializer exits before opening a server socket or creating a world.
"""
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / '.gradle/caches'
MODULES = CACHE / 'modules-2/files-2.1'
WORK = ROOT / 'build/packet-hook-check'
WORK.mkdir(parents=True, exist_ok=True)
JAVA_HOME = Path(os.environ['JAVA_HOME'])

def artifact(notation):
    group, name, version = notation.split(':')
    return next((MODULES / group / name / version).rglob(name + '-' + version + '.jar'))

loader = artifact('net.fabricmc:fabric-loader:0.19.5')
with zipfile.ZipFile(loader) as jar:
    installer = json.loads(jar.read('fabric-installer.json'))
loader_libraries = [artifact(item['name']) for scope in ['common', 'development']
                    for item in installer['libraries'][scope]]
mc = CACHE / 'fabric-loom/26.3/minecraft-merged.jar'
mc_libraries = []
for item in json.loads((CACHE / 'fabric-loom/26.3/mojang_minecraft_info.json').read_text())['libraries']:
    if len(item['name'].split(':')) != 3:
        continue
    try:
        mc_libraries.append(artifact(item['name']))
    except StopIteration:
        pass
cp = os.pathsep.join(map(str, [loader, *loader_libraries, mc, *mc_libraries]))
classes = WORK / 'classes'
classes.mkdir(exist_ok=True)
main = ROOT / 'common/src/main/java/dev/ryanhcode/sable'
sources = [main / 'mixinterface/entity/entities_stick_sublevels/packet_mixin/PacketActuallyInSubLevelExtension.java',
           main / 'networking/SubLevelPacketCodec.java']
package = 'dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.packet_mixin'
names = ['ClientboundMoveEntityPacketPosMixin', 'ClientboundMoveEntityPacketPosRotMixin',
         'ClientboundTeleportEntityPacketMixin', 'ClientboundEntityPositionSyncPacketMixin']
sources += [main / 'mixin/entity/entities_stick_sublevels/packet_mixin' / (name + '.java') for name in names]
sources += [ROOT / 'src/test/java/dev/ryanhcode/sable/network/SubLevelPacketHookCheck.java']
subprocess.run([str(JAVA_HOME / 'bin/javac'), '--release', '25', '-proc:none', '-cp', cp,
                '-d', str(classes), *map(str, sources)], check=True)
(classes / 'fabric.mod.json').write_text(json.dumps({
    'schemaVersion': 1, 'id': 'sable_packet_hook_check', 'version': '1.0.0',
    'environment': 'server', 'entrypoints': {'main': ['dev.ryanhcode.sable.network.SubLevelPacketHookCheck']},
    'mixins': ['sable-packet-hook-check.mixins.json'],
    'depends': {'fabricloader': '>=0.19.5', 'minecraft': '26.3', 'java': '>=25'}
}))
(classes / 'sable-packet-hook-check.mixins.json').write_text(json.dumps({
    'required': True, 'minVersion': '0.8', 'compatibilityLevel': 'JAVA_25',
    'package': package, 'mixins': names, 'injectors': {'defaultRequire': 1}
}))
test_jar = WORK / 'sable-packet-hook-check.jar'
with zipfile.ZipFile(test_jar, 'w') as jar:
    for path in classes.rglob('*'):
        if path.is_file():
            jar.write(path, path.relative_to(classes))
args = [str(JAVA_HOME / 'bin/java'), '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true',
        '-Dfabric.development=true', '-Dfabric.gameVersion=26.3', '-Dfabric.gameJarPath=' + str(mc),
        '-Dfabric.addMods=' + str(test_jar), '-cp', cp, 'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
result = subprocess.run(args, cwd=WORK)
raise SystemExit(result.returncode)
