#!/usr/bin/env python3
"""Resolve and verify the genuine 26.3 optional compile APIs."""
from pathlib import Path
import hashlib
import json
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'build/diagnostic/target-api'
DEST.mkdir(parents=True, exist_ok=True)
manifest = json.loads((ROOT / 'diagnostics/target-compile-apis.json').read_text())
for artifact in manifest['artifacts']:
    path = DEST / artifact['file']
    if not path.exists():
        request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'Sable-26.3-source-port/1.0'})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != artifact['sha256']:
            raise SystemExit('Unexpected artifact checksum: ' + artifact['coordinate'])
        path.write_bytes(data)
    if hashlib.sha256(path.read_bytes()).hexdigest() != artifact['sha256']:
        raise SystemExit('Unexpected local artifact checksum: ' + str(path))
    with zipfile.ZipFile(path) as archive:
        classes = sum(name.endswith('.class') for name in archive.namelist())
        if not classes: raise SystemExit('No real classes in ' + str(path))
    print(artifact['coordinate'] + ': ' + str(classes) + ' genuine API classes')
