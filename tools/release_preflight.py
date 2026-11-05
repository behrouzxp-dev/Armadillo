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


def validate_approval(environment: dict, owner: str) -> None:
    rules = [r for r in environment.get('protection_rules', []) if r.get('type') == 'required_reviewers']
    if len(rules) != 1:
        raise ValueError('The production environment must require the owner as its reviewer')
    reviewers = rules[0].get('reviewers') or []
    if len(reviewers) != 1 or reviewers[0].get('type') != 'User' or reviewers[0].get('reviewer', {}).get('login', '').lower() != owner.lower():
        raise ValueError('Only the repository owner may be configured as the required release reviewer')


def main() -> None:
    tag, repo, owner = (os.environ[k] for k in ('RELEASE_TAG', 'GITHUB_REPOSITORY', 'GITHUB_REPOSITORY_OWNER'))
    version = parse_tag(tag)
    if os.environ.get('REF_PROTECTED') != 'true':
        raise ValueError('Release tags must be protected by an active tag ruleset')
    env = json.loads(command('gh', 'api', f'repos/{repo}/environments/production'))
    validate_approval(env, owner)
    tag_commit = command('git', 'rev-parse', '--verify', f'refs/tags/{tag}^{{commit}}')
    if tag_commit != command('git', 'rev-parse', 'HEAD'):
        raise ValueError('Checked-out commit does not match the release tag')
    subprocess.run(['git', 'merge-base', '--is-ancestor', tag_commit, 'origin/main'], check=True)
    name, code = parse_gradle(Path('app/build.gradle.kts').read_text(encoding='utf-8'))
    if tag != f'v{name}':
        raise ValueError('The tag must match the configured Android versionName')
    pages = json.loads(command('gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100'))
    for release in (r for page in pages for r in page):
        old_tag = release['tag_name']
        if old_tag == tag:
            raise ValueError(f'A release or draft already exists for {tag}; nothing will be overwritten')
        old_version = parse_tag(old_tag)
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
    print(f'Validated {tag}, versionCode {code}, protected tag and owner review.')
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write(f'version={name}\ncode={code}\n')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, KeyError, json.JSONDecodeError) as exc:
        print(f'::error::Release preflight failed: {exc}', file=sys.stderr)
        sys.exit(1)
