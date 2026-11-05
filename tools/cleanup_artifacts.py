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


def stale_artifact_ids(pages: list[dict], now: dt.datetime) -> list[int]:
    cutoff = now - dt.timedelta(days=1)
    return [int(artifact['id']) for page in pages for artifact in page['artifacts']
            if artifact['name'] in TEMPORARY_NAMES
            and dt.datetime.fromisoformat(artifact['created_at'].replace('Z', '+00:00')) < cutoff]


def main() -> None:
    repo = os.environ['GITHUB_REPOSITORY']
    raw = subprocess.check_output(['gh', 'api', '--paginate', '--slurp',
                                   f'repos/{repo}/actions/artifacts?per_page=100'], text=True)
    for artifact_id in stale_artifact_ids(json.loads(raw), dt.datetime.now(dt.timezone.utc)):
        print(f'Deleting temporary Actions artifact {artifact_id}', flush=True)
        subprocess.run(['gh', 'api', '--method', 'DELETE',
                        f'repos/{repo}/actions/artifacts/{artifact_id}'], check=True)


if __name__ == '__main__':
    main()
