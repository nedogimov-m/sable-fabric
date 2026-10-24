#!/usr/bin/env python3
"""Exercise the upstream bundled JNI library on Java 25 without launching Minecraft.

This checks only the native ABI and a falling rigid body. It does not establish
Minecraft, Fabric, mixin, sub-level, rendering, or multiplayer compatibility.
The temporary JNI declarations are copied from the real Rapier3D source.
Requires the lz4 CLI to decode the original upstream archive.
"""
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / 'build' / 'native-check'
SOURCE = ROOT / 'sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/Rapier3D.java'
ARCHIVE = ROOT / 'sable_rapier/src/main/resources/natives/sable_rapier/sable_rapier_binaries.zip.l4z'
WORK.mkdir(parents=True, exist_ok=True)
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
if not java or not javac or not shutil.which('lz4'):
    raise SystemExit('Set JAVA_HOME to JDK 25 and install the lz4 command.')
arch = {'arm64': 'aarch64', 'aarch64': 'aarch64', 'AMD64': 'x86_64', 'x86_64': 'x86_64'}.get(platform.machine())
os_name, extension = {'Darwin': ('macos', 'dylib'), 'Linux': ('linux', 'so'), 'Windows': ('windows', 'dll')}[platform.system()]
if arch is None:
    raise SystemExit(f'Unsupported native architecture: {platform.machine()}')
native_name = f'sable_rapier_{arch}_{os_name}.{extension}'
subprocess.run(['lz4', '-f', '-d', str(ARCHIVE), str(WORK / 'natives.zip')], check=True)
with zipfile.ZipFile(WORK / 'natives.zip') as archive:
    (WORK / native_name).write_bytes(archive.read(native_name))

# JNI binds by class and method name; use declarations from production source,
# with a separate test main and no Minecraft-dependent Java implementation.
names = ['initialize', 'step', 'createBox', 'removeBox', 'getPose', 'dispose']
text = SOURCE.read_text()
declarations = []
for name in names:
    match = re.search(r'(?:public\s+)?static native [^;]+\b' + name + r'\([^;]*?\);', text)
    if not match:
        raise SystemExit(f'Production native declaration missing: {name}')
    declarations.append(match.group(0))
bridge = WORK / 'src/dev/ryanhcode/sable/physics/impl/rapier/Rapier3D.java'
bridge.parent.mkdir(parents=True, exist_ok=True)
bridge.write_text('''package dev.ryanhcode.sable.physics.impl.rapier;
public final class Rapier3D {
''' + '\n'.join(declarations) + '''
    public static void main(String[] args) {
        if (Runtime.version().feature() != 25) throw new AssertionError("Java 25 required");
        System.load(args[0]);
        long scene = initialize(0.0, -9.81, 0.0, 0.0);
        if (scene == 0) throw new AssertionError("Null native scene");
        try {
            createBox(scene, 17, 1.0, 0.5, 0.5, 0.5, new double[]{0, 10, 0, 0, 0, 0, 1});
            double[] pose = new double[7];
            getPose(scene, 17, pose);
            if (Math.abs(pose[1] - 10.0) > 1e-5) throw new AssertionError("Initial pose");
            for (int i = 0; i < 20; i++) step(scene, 0.05);
            getPose(scene, 17, pose);
            if (!Double.isFinite(pose[1]) || pose[1] < 4.0 || pose[1] > 6.0)
                throw new AssertionError("Gravity did not produce expected fall: " + pose[1]);
            if (Math.abs(pose[0]) > 1e-5 || Math.abs(pose[2]) > 1e-5)
                throw new AssertionError("Unexpected lateral motion");
            removeBox(scene, 17);
            System.out.println("PASS Java 25 JNI load, scene, box pose, gravity, removal; y=" + pose[1]);
        } finally {
            dispose(scene);
        }
    }
}
''')
classes = WORK / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run([javac, '--release', '25', '-d', str(classes), str(bridge)], check=True)
subprocess.run([java, '--enable-native-access=ALL-UNNAMED', '-cp', str(classes),
                'dev.ryanhcode.sable.physics.impl.rapier.Rapier3D', str(WORK / native_name)], check=True)
print('archive sha256:', hashlib.sha256(ARCHIVE.read_bytes()).hexdigest())
print('native sha256:', hashlib.sha256((WORK / native_name).read_bytes()).hexdigest())
