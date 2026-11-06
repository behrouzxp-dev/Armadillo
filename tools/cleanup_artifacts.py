#!/usr/bin/env python3
"""Clean only this project's known temporary Actions artifacts, never release assets."""
from __future__ import annotations

import datetime as dt
import json
import os
import subprocess

TEMPORARY_NAMES = frozenset({
    'armadillo-ci-debug-apk', 'armadillo-ci-signed-release',
    'armadillo-debug-apk', 'debug-check-log', 'release-build-log',
})
SIGNED_TRANSFER_NAME = 'armadillo-ci-signed-release'
DEBUG_RETENTION = dt.timedelta(days=1)
SIGNED_TRANSFER_RETENTION = dt.timedelta(hours=1)


def stale_artifact_ids(pages: list[dict], now: dt.datetime) -> list[int]:
    result = []
    for page in pages:
        for artifact in page['artifacts']:
            if artifact.get('expired') or artifact['name'] not in TEMPORARY_NAMES:
                continue
            created = dt.datetime.fromisoformat(artifact['created_at'].replace('Z', '+00:00'))
            retention = SIGNED_TRANSFER_RETENTION if artifact['name'] == SIGNED_TRANSFER_NAME else DEBUG_RETENTION
            if created < now - retention:
                result.append(int(artifact['id']))
    return result


def main() -> None:
    repo = os.environ['GITHUB_REPOSITORY']
    raw = subprocess.check_output(['gh', 'api', '--paginate', '--slurp',
                                   f'repos/{repo}/actions/artifacts?per_page=100'], text=True)
    for artifact_id in stale_artifact_ids(json.loads(raw), dt.datetime.now(dt.timezone.utc)):
        print(f'Deleting temporary Actions artifact {artifact_id}', flush=True)
        try:
            subprocess.run(['gh', 'api', '--method', 'DELETE',
                            f'repos/{repo}/actions/artifacts/{artifact_id}'], check=True)
        except subprocess.CalledProcessError:
            # The artifact can expire between listing and deletion; that is not a failure.
            print(f'Skipping artifact {artifact_id}: already removed', flush=True)


if __name__ == '__main__':
    main()
