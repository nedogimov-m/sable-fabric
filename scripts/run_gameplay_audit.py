#!/usr/bin/env python3
"""Run native Sable/Veil/ImGuiMC checks against complete production JARs, one client at a time."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
AERO = WORKSPACE / 'Aeronautics'
CASES = ('imgui', 'imgui-components', 'editor', 'graphics', 'veil-passes', 'physics', 'restore', 'dimensions')
SOURCES = {
    'imgui': ['imguimc/port-tests/FoundationImGuiChecks.java'],
    'imgui-components': ['imguimc/port-tests/FoundationImGuiComponentChecks.java',
                         'imguimc/port-tests/NativeRendererAcceptance.java',
                         'imguimc/port-tests/NativeSdlPlatformAcceptance.java'],
    'editor': ['veil/port-tests/FoundationVeilChecks.java', 'veil/port-tests/FoundationVeilGpuChecks.java',
               'veil/port-tests/NativeFramebufferOwnerAcceptance.java'],
    'veil-passes': ['veil/port-tests/FoundationVeilPassChecks.java', 'veil/port-tests/NativeLightAcceptance.java',
                    'veil/port-tests/NativeParticleAcceptance.java', 'veil/port-tests/NativeBloomAcceptance.java',
                    'veil/port-tests/NativeEditorTextureAcceptance.java'],
    'physics': ['sable/port-tests/FoundationSableChecks.java'],
}
SOURCES['graphics'] = SOURCES['editor']
SOURCES['restore'] = SOURCES['physics']
SOURCES['dimensions'] = SOURCES['physics']
GPU_RESOURCES = ('pinwheel/shaders/program/native_voxel_test.json',
                 'pinwheel/shaders/program/native_voxel_test.fsh', 'shaders/core/native_bloom_emission.fsh')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client-args', type=Path, required=True,
                        help='Working native Minecraft 26.3/Fabric GameTest launch arguments for this machine.')
    parser.add_argument('--mod', type=Path, action='append', required=True,
                        help='Exact production input JAR; repeat for Create, Sable, Aeronautics Bundle and Fabric API.')
    parser.add_argument('--case', choices=CASES, action='append', help='Repeat to select cases; default: all eight.')
    parser.add_argument('--name', default='foundation-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f'),
                        help='Unique report prefix; existing profiles are never reused.')
    parser.add_argument('--restore-from', type=Path,
                        help='Accepted physics runtime.json for a restore-only run; the world is copied before use.')
    parser.add_argument('--property', action='append', default=[], help='Additional external-driver JVM key=value.')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.name):
        parser.error('--name must contain only letters, digits, underscores and hyphens')
    selected = [case for case in CASES if case in (args.case or CASES)]
    if 'restore' in selected and 'physics' not in selected and args.restore_from is None:
        parser.error('restore needs physics in the same run or --restore-from runtime.json')
    if args.restore_from and ('physics' in selected or 'restore' not in selected):
        parser.error('--restore-from is only for a restore run without physics')
    for prop in args.property:
        if '=' not in prop or not prop.split('=', 1)[0] or prop.startswith('-') or prop.startswith('foundation.case='):
            parser.error('--property must be key=value and cannot override foundation.case')
    launcher = AERO / 'tools/run_packaged_smoke.py'
    client_args = args.client_args.resolve()
    mods = [p.resolve() for p in args.mod]
    sources = {case: [ROOT / 'port-tests/FoundationClientAudit.java',
                     *(WORKSPACE / p for p in SOURCES[case])] for case in selected}
    for path in [launcher, client_args, *mods, *(p for group in sources.values() for p in group)]:
        if not path.is_file():
            parser.error('Missing input: ' + str(path))
    if args.restore_from and not args.restore_from.is_file():
        parser.error('Missing restore report: ' + str(args.restore_from))
    output = ROOT / 'build/gameplay-audit' / args.name
    if output.exists():
        parser.error('Choose a new --name to preserve existing evidence: ' + str(output))
    profiles = {case: AERO / 'build/porting/packaged-smoke' / (args.name + '-' + case) / 'client'
                for case in selected}
    for profile in profiles.values():
        if profile.exists():
            parser.error('Existing native profile cannot be reused: ' + str(profile))
    output.mkdir(parents=True)
    summary_file = output / 'summary.json'
    report = {'scope': 'Selected native gameplay/rendering checks; one machine, not every possible mod interaction.',
              'scriptSha256': sha(Path(__file__)), 'clientArgs': str(client_args),
              'mods': {str(p): sha(p) for p in mods}, 'cases': {}}
    write(summary_file, report)
    resource_root = output / 'gpu-resources'
    if 'veil-passes' in selected:
        for relative in GPU_RESOURCES:
            source = WORKSPACE / 'veil/port-tests/resources/assets/veil' / relative
            target = resource_root / 'assets/veil' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    physics_report = args.restore_from.resolve() if args.restore_from else None
    for case in selected:
        row = {'profile': str(profiles[case]), 'status': 'starting'}
        report['cases'][case] = row
        command = [sys.executable, str(launcher), '--client-args', str(client_args), '--side', 'client',
                   '--name', args.name + '-' + case, '--skip-activation', '--driver-entrypoint',
                   'FoundationClientAudit', '--driver-property', 'foundation.case=' + case]
        if case == 'restore':
            prior = json.loads(physics_report.read_text()) if physics_report and physics_report.is_file() else {}
            fixture = physics_report.with_name('world-fixture.json') if physics_report else None
            if prior.get('status') != 'passed' or prior.get('selection') != 'physics' or not fixture or not fixture.is_file():
                row.update(status='skipped', error='No accepted physics world/expectation; independent cases still run.')
                write(summary_file, report)
                continue
            command += ['--resume-world', prior['savedWorld'], '--driver-property', 'foundation.expected=' + str(fixture)]
        for source in sources[case]:
            command += ['--driver-source', str(source)]
        if case == 'veil-passes':
            command += ['--driver-resources', str(resource_root)]
        for mod in mods:
            command += ['--mod', str(mod)]
        for prop in args.property:
            command += ['--driver-property', prop]
        row['command'] = command
        write(summary_file, report)
        print('Running native ' + case + '; evidence: ' + str(profiles[case]), flush=True)
        result = subprocess.run(command, cwd=AERO)
        runtime_file = profiles[case] / 'runtime.json'
        runtime = json.loads(runtime_file.read_text()) if runtime_file.is_file() else {}
        row.update(exitCode=result.returncode, runtime=str(runtime_file),
                   status='passed' if result.returncode == 0 and runtime.get('status') == 'passed' else 'failed')
        if runtime.get('error'):
            row['error'] = runtime['error']
        if case == 'physics' and row['status'] == 'passed':
            physics_report = runtime_file
        write(summary_file, report)
    report['status'] = 'passed' if all(row['status'] == 'passed' for row in report['cases'].values()) else 'failed'
    write(summary_file, report)
    print(str(summary_file), flush=True)
    return 0 if report['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
