#!/usr/bin/env python3
"""Audit resolved Maven runtime/test dependencies with OSV and POM license metadata."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from functools import lru_cache
from urllib.parse import quote

COORD = re.compile(r'^[A-Za-z0-9_.+\-]+$')
CONFIGURATIONS = ((':app', 'debugRuntimeClasspath'), (':app', 'releaseRuntimeClasspath'),
                  (':game-core', 'testRuntimeClasspath'))


def parse_dependencies(output: str) -> set[tuple[str, str, str]]:
    result = set()
    for line in output.splitlines():
        marker = re.search(r'(?:\+---|\\---)\s+(.+)', line)
        if not marker:
            continue
        item = marker.group(1)
        if ' FAILED' in item or item.endswith(' (n)'):
            raise ValueError(f'Unresolved Gradle dependency: {item}')
        if item.endswith(' (c)') or item.startswith('project '):
            continue
        parts = item.split(' -> ', 1)
        if len(parts) > 1 and parts[1].startswith('project '):
            continue
        requested = parts[0].split(':', 2)
        # A version supplied by a platform/BOM may be omitted in the request.
        if len(requested) == 2 and len(parts) > 1:
            requested.append('')
        if len(requested) != 3:
            raise ValueError(f'Unexpected Gradle dependency: {item}')
        if len(parts) > 1:
            selected = parts[1].split()[0]
            if ':' in selected:
                requested = selected.split(':', 2)
            else:
                requested[2] = selected
        else:
            requested[2] = requested[2].split()[0]
        if len(requested) != 3 or not all(COORD.fullmatch(v) for v in requested):
            raise ValueError(f'Invalid resolved dependency coordinate: {item}')
        result.add(tuple(requested))
    return result


def get_json(url: str, payload: dict) -> dict:
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={'Content-Type': 'application/json', 'User-Agent': 'Armadillo-CI/1'},
                                     method='POST')
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def find_vulnerabilities(coords: list[tuple[str, str, str]]) -> list[tuple[str, str]]:
    findings = []
    for start in range(0, len(coords), 500):
        batch = coords[start:start + 500]
        queries = [{'package': {'name': f'{g}:{a}', 'ecosystem': 'Maven'}, 'version': v} for g, a, v in batch]
        results = get_json('https://api.osv.dev/v1/querybatch', {'queries': queries})['results']
        if len(results) != len(batch):
            raise ValueError('OSV returned an incomplete response')
        for coord, entry in zip(batch, results):
            for vuln in entry.get('vulns', []):
                findings.append((':'.join(coord), vuln['id']))
    return findings


@lru_cache(maxsize=None)
def pom_licenses(coord: tuple[str, str, str], depth: int = 0) -> tuple[str, ...]:
    if depth > 5 or not all(COORD.fullmatch(v) for v in coord):
        return ()
    group, artifact, version = coord
    path = '/'.join([group.replace('.', '/'), artifact, version, f'{artifact}-{version}.pom'])
    path = quote(path, safe='/')
    repos = ('https://dl.google.com/dl/android/maven2/', 'https://repo.maven.apache.org/maven2/')
    if not group.startswith(('androidx.', 'com.android.', 'com.google.android.')):
        repos = repos[::-1]
    for repo in repos:
        try:
            request = urllib.request.Request(repo + path, headers={'User-Agent': 'Armadillo-CI/1'})
            with urllib.request.urlopen(request, timeout=15) as response:
                root = ET.fromstring(response.read())
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                continue
            raise
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        licenses = []
        for node in root.findall('m:licenses/m:license', ns):
            name, url = node.findtext('m:name', default='', namespaces=ns), node.findtext('m:url', default='', namespaces=ns)
            if name or url:
                licenses.append(' '.join((name + ' ' + url).split()))
        if licenses:
            return tuple(licenses)
        parent = root.find('m:parent', ns)
        if parent is not None:
            parent_coord = tuple(parent.findtext(f'm:{key}', default='', namespaces=ns).strip()
                                 for key in ('groupId', 'artifactId', 'version'))
            return pom_licenses(parent_coord, depth + 1)
        return ()
    return ()


def main() -> int:
    dependencies = set()
    for module, configuration in CONFIGURATIONS:
        output = subprocess.check_output(['./gradlew', '--console=plain', '-q', f'{module}:dependencies',
                                          '--configuration', configuration], text=True)
        resolved = parse_dependencies(output)
        if not resolved:
            raise ValueError(f'No external dependencies resolved for {module}:{configuration}')
        print(f'{module}:{configuration}: {len(resolved)} external modules', flush=True)
        dependencies.update(resolved)
    coords = sorted(dependencies)
    findings = find_vulnerabilities(coords)
    print(f'OSV scanned {len(coords)} unique resolved Maven modules; {len(findings)} vulnerability matches.')
    for coord, vulnerability in findings:
        print(f'::error::{coord}: {vulnerability}')
    # Reports metadata, not a legal approval or a complete license-policy decision.
    with ThreadPoolExecutor(max_workers=8) as pool:
        license_results = list(pool.map(pom_licenses, coords))
    unknown = 0
    for coord, licenses in zip(coords, license_results):
        if not licenses:
            unknown += 1
        print(f"LICENSE {':'.join(coord)}: {'; '.join(licenses) if licenses else 'UNKNOWN - manual review required'}")
    print(f'License metadata missing for {unknown} modules; review these and all declared licenses manually.')
    return 1 if findings else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError, ET.ParseError, subprocess.CalledProcessError) as exc:
        print(f'::error::Dependency audit failed: {exc}', file=sys.stderr)
        sys.exit(1)
