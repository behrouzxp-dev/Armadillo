#!/usr/bin/env python3
"""Fail closed before a tag can access signing secrets or publish a release."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

SEMVER = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")


def parse_tag(tag: str) -> tuple[int, int, int]:
    match = SEMVER.fullmatch(tag)
    if not match:
        raise ValueError(f"Invalid stable release tag: {tag!r}")
    return tuple(int(part) for part in match.groups())


def parse_gradle(text: str) -> tuple[str, int]:
    names = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.MULTILINE)
    codes = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.MULTILINE)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError('Expected exactly one literal versionName and versionCode in app/build.gradle.kts')
    code = int(codes[0])
    if not 1 <= code <= 2_100_000_000:
        raise ValueError('versionCode must be between 1 and 2100000000')
    return names[0], code


def command(*args: str) -> str:
    return subprocess.check_output(args, text=True).strip()


def parse_published_metadata(metadata: dict) -> tuple[str, int]:
    if metadata.get('applicationId') != 'com.armadillo.game':
        raise ValueError('Prior release metadata has an unexpected application ID')
    elements = metadata.get('elements')
    if not isinstance(elements, list) or not elements:
        raise ValueError('Prior release metadata has no APK outputs')
    values = {(element.get('versionName'), element.get('versionCode')) for element in elements}
    if len(values) != 1:
        raise ValueError('Prior APK outputs have inconsistent version metadata')
    name, code = values.pop()
    if not isinstance(name, str) or type(code) is not int or not 1 <= code <= 2_100_000_000:
        raise ValueError('Prior APK metadata has an invalid version')
    return name, code


def validate_dispatch(event: str, ref: str, actor: str, triggering_actor: str, owner: str) -> None:
    if event != 'workflow_dispatch' or ref != 'refs/heads/main':
        raise ValueError('Release must be manually dispatched from main')
    # Repository-owner comparison fails for organisation-owned repositories; an
    # optional variable lets the owner grant additional trusted dispatch actors.
    allowed = os.environ.get('RELEASE_ACTOR_ALLOWLIST') or owner
    actors = {value.strip().lower() for value in allowed.split(',') if value.strip()}
    if not actors or actor.lower() not in actors or triggering_actor.lower() not in actors:
        raise ValueError('Only the repository owner may start or rerun a release')


def main() -> None:
    tag, repo, owner = (os.environ[k] for k in ('RELEASE_TAG', 'GITHUB_REPOSITORY', 'GITHUB_REPOSITORY_OWNER'))
    version = parse_tag(tag)
    validate_dispatch(os.environ.get('GITHUB_EVENT_NAME', ''), os.environ.get('GITHUB_REF', ''),
                      os.environ.get('RELEASE_ACTOR', ''), os.environ.get('RELEASE_TRIGGERING_ACTOR', ''), owner)
    commit = command('git', 'rev-parse', 'HEAD')
    if commit != os.environ.get('GITHUB_SHA') or commit != command('git', 'rev-parse', 'origin/main'):
        raise ValueError('Release must use the latest checked-out main commit')
    if command('git', 'tag', '--list', tag):
        raise ValueError(f'Tag {tag} already exists; nothing will be overwritten')
    name, code = parse_gradle(Path('app/build.gradle.kts').read_text(encoding='utf-8'))
    if tag != f'v{name}':
        raise ValueError('The tag must match the configured Android versionName')
    pages = json.loads(command('gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100'))
    for release in (r for page in pages for r in page):
        old_tag = release['tag_name']
        if old_tag == tag:
            raise ValueError(f'A release or draft already exists for {tag}; nothing will be overwritten')
        try:
            old_version = parse_tag(old_tag)
        except ValueError:
            # Historical pre-release or non-version tags are not stable releases.
            continue
        old_config = command('git', 'show', f'refs/tags/{old_tag}:app/build.gradle.kts')
        old_name, source_code = parse_gradle(old_config)
        with tempfile.TemporaryDirectory(prefix='armadillo-release-history-') as directory:
            command('gh', 'release', 'download', old_tag, '--pattern', 'output-metadata.json', '--dir', directory)
            metadata = json.loads((Path(directory) / 'output-metadata.json').read_text(encoding='utf-8'))
        apk_name, old_code = parse_published_metadata(metadata)
        if old_tag != f'v{old_name}' or old_name != apk_name or old_code != source_code:
            raise ValueError(f'Existing release {old_tag} does not match its source and APK metadata')
        if version <= old_version or code <= old_code:
            raise ValueError(f'Tag and versionCode must exceed previous release {old_tag} (code {old_code})')
    print(f'Validated {tag}, versionCode {code}, owner dispatch and main commit.')
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write(f'version={name}\ncode={code}\ncommit={commit}\n')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, KeyError, json.JSONDecodeError) as exc:
        print(f'::error::Release preflight failed: {exc}', file=sys.stderr)
        sys.exit(1)
