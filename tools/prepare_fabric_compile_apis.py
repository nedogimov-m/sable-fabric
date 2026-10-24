#!/usr/bin/env python3
"""Map genuine optional Fabric API class descriptors for compile-only use on 26.3.

Older Fabric APIs use intermediary class names. The implementations are never
bundled or executed: only class descriptors are translated for Java compilation.
Official Minecraft mappings and Fabric intermediary mappings define every rename.
"""
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/compile-api'


def download(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Fabric26.3-compile-API'}), timeout=45) as response:
        return response.read()


def rewrite_class(data, mappings):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return data
    count = struct.unpack_from('>H', data, 8)[0]
    output = bytearray(data[:10]); offset = 10; index = 1
    while index < count:
        tag = data[offset]; output.append(tag); offset += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, offset)[0]; offset += 2
            value = data[offset:offset + length]; offset += length
            value = re.sub(rb'net/minecraft/class_\d+(?:\$class_\d+)*', lambda match: mappings.get(match[0], match[0]), value)
            output.extend(struct.pack('>H', len(value))); output.extend(value)
        else:
            length = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag]
            output.extend(data[offset:offset + length]); offset += length
            if tag in (5, 6): index += 1
        index += 1
    output.extend(data[offset:])
    return bytes(output)


def main():
    manifest = json.loads((ROOT / 'tools/fabric-compile-apis.json').read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(Path(__file__).read_bytes() + (ROOT / 'tools/fabric-compile-apis.json').read_bytes()).hexdigest()
    stamp = OUT / 'prepared.json'
    if stamp.exists():
        previous = json.loads(stamp.read_text())
        if previous.get('input') == key and all((OUT / filename).is_file() and hashlib.sha256((OUT / filename).read_bytes()).hexdigest() == digest for filename, digest in previous.get('outputs', {}).items()):
            print('Genuine Fabric compile API descriptors already prepared'); return
    mapping_file = OUT / 'minecraft-1.21.1-client-mappings.txt'
    if not mapping_file.exists():
        raw = download(manifest['minecraftMappings']['url'])
        assert hashlib.sha1(raw).hexdigest() == manifest['minecraftMappings']['sha1']
        mapping_file.write_bytes(raw)
    else:
        assert hashlib.sha1(mapping_file.read_bytes()).hexdigest() == manifest['minecraftMappings']['sha1']
    named = {}
    for line in mapping_file.read_text().splitlines():
        if line and not line.startswith((' ', '#')) and ' -> ' in line and line.endswith(':'):
            name, obfuscated = line[:-1].split(' -> ')
            named[obfuscated.replace('.', '/')] = name.replace('.', '/')
    intermediary = OUT / 'intermediary-1.21.1-v2.jar'
    if not intermediary.exists(): intermediary.write_bytes(download(manifest['intermediary']['url']))
    assert hashlib.sha256(intermediary.read_bytes()).hexdigest() == manifest['intermediary']['sha256']
    mapping = {}
    with zipfile.ZipFile(intermediary) as jar:
        for line in jar.read('mappings/mappings.tiny').decode().splitlines():
            if not line.startswith('c\t'): continue
            _, obfuscated, source = line.split('\t')
            if obfuscated not in named: continue
            target = named[obfuscated].replace('net/minecraft/resources/ResourceLocation', 'net/minecraft/resources/Identifier')
            mapping[source.encode()] = target.encode()
    for name, artifact in manifest['artifacts'].items():
        source = OUT / (name + '-fabric-original.jar')
        if not source.exists(): source.write_bytes(download(artifact['url']))
        assert hashlib.sha256(source.read_bytes()).hexdigest() == artifact['sha256'], name
        with zipfile.ZipFile(source) as original, zipfile.ZipFile(OUT / f'{name}-api.jar', 'w', zipfile.ZIP_DEFLATED) as output:
            for entry in original.infolist():
                if not entry.filename.endswith('.class'): continue
                output.writestr(entry.filename, rewrite_class(original.read(entry), mapping))
            output.writestr('compile-api-provenance.json', json.dumps({'source': artifact, 'role': 'compile-only class descriptors; never a runtime mod'}, indent=2))
        print('Prepared genuine Fabric compile API:', name)
    stamp.write_text(json.dumps({'input': key, 'outputs': {f'{name}-api.jar': hashlib.sha256((OUT / f'{name}-api.jar').read_bytes()).hexdigest() for name in manifest['artifacts']}}, indent=2) + '\n')


if __name__ == '__main__':
    main()
