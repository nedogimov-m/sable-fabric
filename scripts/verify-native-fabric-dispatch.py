#!/usr/bin/env python3
"""Run actual Fabric26.3 direct-dispatch acceptance without a GPU or production substitutes."""
from pathlib import Path
import hashlib
import json
import os
import shlex
import subprocess

root = Path(__file__).resolve().parents[1]
repo = root.parents[1]
work = root/'build/native-fabric-dispatch-verification'
work.mkdir(parents=True, exist_ok=True)
args = shlex.split((repo/'build/porting/suite-diagnostic/javac.args').read_text())
classpath = args[args.index('-classpath')+1]
source = root/'port-tests/NativeFabricDispatchAcceptance.java'
java = Path(os.environ['JAVA_HOME'])/'bin'
classes = work/'classes'
classes.mkdir(exist_ok=True)
result = subprocess.run([str(java/'javac'), '--release', '25', '-proc:none', '-sourcepath', '',
                         '-cp', classpath, '-d', str(classes), str(source)], capture_output=True, text=True)
(work/'compile.log').write_text(result.stdout+result.stderr)
if result.returncode:
    raise SystemExit(result.stdout+result.stderr)
result = subprocess.run([str(java/'java'), '-cp', str(classes)+os.pathsep+classpath,
                         'NativeFabricDispatchAcceptance'], capture_output=True, text=True)
(work/'runtime.log').write_text(result.stdout+result.stderr)
print(result.stdout+result.stderr)
manifest = {
    'result': 'PASS' if result.returncode == 0 else 'FAIL',
    'scope': 'Actual unchanged Fabric26.3 ClientPlayNetworkAddon.receive dispatch only, with real addon/context/client classes and fixture payloads; no connection, world, mixin application, or GPU. Genuine addon/client endpoints are allocated without their constructors and unused by the tested native receive method.',
    'fixture': str(source.relative_to(repo)),
    'fixtureSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'classpath': {p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in classpath.split(os.pathsep)
                  if Path(p).is_file() and ('fabric-networking' in p or 'minecraft-fabric-sable' in p)}
}
(work/'inputs.json').write_text(json.dumps(manifest, indent=2)+'\n')
raise SystemExit(result.returncode)
