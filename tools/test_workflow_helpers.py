"""Regression tests for release guardrails and temporary-artifact selection."""
import datetime as dt
import json
import io
import hashlib
import os
import subprocess
import textwrap
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import cleanup_artifacts
import dependency_audit
import release_preflight


class ReleasePreflightTests(unittest.TestCase):
    def test_strict_stable_semver(self):
        self.assertEqual((1, 10, 2), release_preflight.parse_tag('v1.10.2'))
        for value in ('1.0.0', 'v01.0.0', 'v1.0', 'v1.0.0-rc1', 'v1.0.0;echo x'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                release_preflight.parse_tag(value)

    def test_version_metadata_is_unambiguous_and_android_safe(self):
        self.assertEqual(('1.2.3', 4), release_preflight.parse_gradle('versionName = "1.2.3"\nversionCode = 4\n'))
        for text in ('versionName = "1.0.0"\nversionCode = 0',
                     'versionName = "1.0.0"\nversionCode = 2100000001',
                     'versionName = "1.0.0"\nversionCode = 1\nversionCode = 2'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release_preflight.parse_gradle(text)

    def test_previous_apk_metadata_requires_consistent_identity_and_version(self):
        metadata = {'applicationId': 'com.armadillo.game', 'elements': [{'versionName': '1.0.0', 'versionCode': 1}]}
        self.assertEqual(('1.0.0', 1), release_preflight.parse_published_metadata(metadata))
        metadata['elements'].append({'versionName': '1.0.1', 'versionCode': 2})
        with self.assertRaises(ValueError):
            release_preflight.parse_published_metadata(metadata)
        with self.assertRaises(ValueError):
            release_preflight.parse_published_metadata({'applicationId': 'other', 'elements': []})

    def test_only_owner_manual_dispatch_from_main_is_allowed(self):
        release_preflight.validate_dispatch('workflow_dispatch', 'refs/heads/main', 'Owner', 'owner', 'owner')
        for event, ref, actor, triggering in (
            ('push', 'refs/heads/main', 'owner', 'owner'),
            ('workflow_dispatch', 'refs/heads/other', 'owner', 'owner'),
            ('workflow_dispatch', 'refs/heads/main', 'other', 'owner'),
            ('workflow_dispatch', 'refs/heads/main', 'owner', 'other'),
        ):
            with self.subTest(event=event, ref=ref, actor=actor, triggering=triggering), self.assertRaises(ValueError):
                release_preflight.validate_dispatch(event, ref, actor, triggering, 'owner')

    def test_preflight_checks_history_and_rejects_duplicate_or_old_codes(self):
        settings = {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_REPOSITORY_OWNER': 'owner',
                    'RELEASE_TAG': 'v1.1.0', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                    'GITHUB_REF': 'refs/heads/main', 'RELEASE_ACTOR': 'owner',
                    'RELEASE_TRIGGERING_ACTOR': 'owner', 'GITHUB_SHA': 'samecommit'}
        with tempfile.TemporaryDirectory() as tmp:
            settings['GITHUB_OUTPUT'] = str(Path(tmp) / 'output')
            releases = [{'tag_name': 'v1.0.0'}]
            metadata = {'applicationId': 'com.armadillo.game', 'elements': [{'versionName': '1.0.0', 'versionCode': 1}]}

            def fake_command(*args):
                if args[:4] == ('gh', 'api', '--paginate', '--slurp'):
                    return json.dumps([releases])
                if args[:2] == ('git', 'rev-parse'):
                    return 'samecommit'
                if args[:3] == ('git', 'tag', '--list'):
                    return ''
                if args[:2] == ('git', 'show'):
                    return 'versionName = "1.0.0"\nversionCode = 1\n'
                if args[:3] == ('gh', 'release', 'download'):
                    Path(args[-1], 'output-metadata.json').write_text(json.dumps(metadata))
                    return ''
                raise AssertionError(args)

            with patch.dict('os.environ', settings), patch('release_preflight.command', side_effect=fake_command), \
                 patch('release_preflight.parse_gradle', side_effect=[('1.1.0', 2), ('1.0.0', 1), ('1.1.0', 2), ('1.0.0', 1), ('1.1.0', 1), ('1.0.0', 1)]):
                release_preflight.main()
                releases.append({'tag_name': 'v1.1.0'})
                with self.assertRaises(ValueError):
                    release_preflight.main()
                releases.pop()
                with self.assertRaises(ValueError):
                    release_preflight.main()
            self.assertIn('version=1.1.0\ncode=2\ncommit=samecommit', Path(settings['GITHUB_OUTPUT']).read_text())

    def test_preflight_rejects_non_owner_existing_tag_and_stale_main(self):
        settings = {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_REPOSITORY_OWNER': 'owner',
                    'RELEASE_TAG': 'v1.1.0', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                    'GITHUB_REF': 'refs/heads/main', 'RELEASE_ACTOR': 'owner',
                    'RELEASE_TRIGGERING_ACTOR': 'owner', 'GITHUB_SHA': 'commit'}
        with patch.dict('os.environ', settings, clear=True):
            with patch('release_preflight.command', side_effect=['commit', 'different']):
                with self.assertRaisesRegex(ValueError, 'latest'):
                    release_preflight.main()
            with patch('release_preflight.command', side_effect=['commit', 'commit', 'v1.1.0']):
                with self.assertRaisesRegex(ValueError, 'already exists'):
                    release_preflight.main()
            with patch.dict('os.environ', {'RELEASE_TRIGGERING_ACTOR': 'other'}), \
                 patch('release_preflight.command') as command:
                with self.assertRaisesRegex(ValueError, 'owner'):
                    release_preflight.main()
                command.assert_not_called()


class SignedVerificationTests(unittest.TestCase):
    def run_verification(self, failing=''):
        workflow = (Path(__file__).resolve().parent.parent / '.github/workflows/release.yml').read_text()
        section = workflow.split('      - name: Verify APK metadata and original signing certificate', 1)[1]
        section = section.split('      - name: Upload verified signed files for publishing', 1)[0]
        script = textwrap.dedent(section.split('        run: |', 1)[1].lstrip('\n'))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ('app/build/outputs/apk/release/app-release.apk',
                         'app/build/outputs/bundle/release/app-release.aab'):
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('sample')
            if failing == 'missing':
                (root / 'app/build/outputs/apk/release/app-release.apk').unlink()
            build_tools = root / 'android/build-tools/37.0.0'
            build_tools.mkdir(parents=True)
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            stub = r"""#!/usr/bin/env python3
import os, sys
name = os.path.basename(sys.argv[0])
with open(os.environ['CALL_LOG'], 'a') as log:
    log.write(name + '\n')
if os.environ['FAILING'] == name:
    print('diagnostic from ' + name)
    sys.exit(1)
cert = '4f405db1954c1740566536b8568c4bae56d5a1e6184c2110cd3239af529826b8'
if os.environ['FAILING'] == 'mismatch':
    cert = '0' * 64
if name == 'apksigner':
    print('Signer #1 certificate SHA-256 digest: ' + cert)
elif name == 'aapt':
    print("package: name='com.armadillo.game' versionCode='2' versionName='1.1.0'")
elif name == 'keytool':
    print('SHA256: ' + ':'.join(cert[i:i+2] for i in range(0, len(cert), 2)))
else:
    print('jar verified')
"""
            for path in (build_tools / 'apksigner', build_tools / 'aapt', bin_dir / 'jarsigner', bin_dir / 'keytool'):
                path.write_text(stub)
                path.chmod(0o755)
            env = os.environ.copy()
            env.update({'PATH': str(bin_dir) + os.pathsep + env['PATH'], 'RUNNER_TEMP': str(root),
                        'ANDROID_HOME': str(root / 'android'), 'VERSION': '1.1.0', 'VERSION_CODE': '2',
                        'FAILING': failing, 'CALL_LOG': str(root / 'calls')})
            if failing == 'missing_sdk':
                env['ANDROID_HOME'] = str(root / 'missing-sdk')
            result = subprocess.run(['bash', '-e', '-c', script], cwd=root, env=env,
                                    text=True, capture_output=True)
            calls = (root / 'calls').read_text().splitlines() if (root / 'calls').exists() else []
            return result, calls

    def test_signed_verification_accepts_matching_fake_outputs(self):
        result, calls = self.run_verification()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(['apksigner', 'aapt', 'jarsigner', 'keytool'], calls)
        self.assertIn('signatures, application ID and APK version verified', result.stdout)

    def test_tool_failures_report_diagnostics_and_stop(self):
        for name in ('apksigner', 'aapt', 'jarsigner', 'keytool'):
            with self.subTest(name=name):
                result, calls = self.run_verification(name)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('::error::', result.stdout)
                self.assertIn('diagnostic from ' + name, result.stdout)
                self.assertEqual(name, calls[-1])

    def test_missing_output_and_wrong_signer_fail_closed(self):
        result, calls = self.run_verification('missing')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing or empty', result.stdout)
        self.assertEqual([], calls)
        result, calls = self.run_verification('missing_sdk')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('build-tools directory is missing', result.stdout)
        self.assertEqual([], calls)
        result, calls = self.run_verification('mismatch')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('original release signing certificate', result.stderr)
        self.assertEqual(['apksigner', 'aapt', 'jarsigner', 'keytool'], calls)


class WorkflowPublicationTests(unittest.TestCase):
    def run_publisher(self, mode):
        workflow = (Path(__file__).resolve().parent.parent / '.github/workflows/release.yml').read_text()
        section = workflow.split('      - name: Verify checksums and publish a new release', 1)[1]
        script = textwrap.dedent(section.split('        run: |', 1)[1].lstrip('\n'))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            outputs = root / 'release-files'
            for name in ('apk/release/app-release.apk', 'bundle/release/app-release.aab',
                         'apk/release/output-metadata.json'):
                path = outputs / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            checksums = []
            for name in ('apk/release/app-release.apk', 'bundle/release/app-release.aab'):
                digest = hashlib.sha256((outputs / name).read_bytes()).hexdigest()
                checksums.append(f'{digest}  {name}\n')
            (outputs / 'SHA256SUMS').write_text(''.join(checksums))
            if mode == 'checksum':
                (outputs / 'apk/release/app-release.apk').write_bytes(b'tampered')
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            gh = bin_dir / 'gh'
            gh.write_text(r"""#!/usr/bin/env python3
import os, sys
args = sys.argv[1:]
with open(os.environ['CALL_LOG'], 'a') as log:
    log.write(' '.join(args) + '\n')
if args[:3] == ['api', '--method', 'POST']:
    sys.exit(1 if os.environ['MODE'] == 'existing' else 0)
if args[:2] == ['release', 'create']:
    sys.exit(0 if '--verify-tag' in args and '--draft' in args else 1)
if args[:2] == ['release', 'view']:
    print('app-release.apk\napp-release.aab\nSHA256SUMS\noutput-metadata.json')
elif args and args[0] == 'api':
    calls = open(os.environ['CALL_LOG']).read().splitlines()
    checks = sum(call.startswith('api repos/') for call in calls)
    mismatch = os.environ['MODE'] == 'changed' or (os.environ['MODE'] == 'changed_late' and checks > 1)
    print('wrongcommit' if mismatch else 'commit')
elif args[:2] == ['release', 'edit']:
    sys.exit(0)
""")
            gh.chmod(0o755)
            env = os.environ.copy()
            env.update({'PATH': str(bin_dir) + os.pathsep + env['PATH'], 'CALL_LOG': str(root / 'calls'),
                        'MODE': mode, 'RUNNER_TEMP': str(root), 'GH_REPO': 'owner/repo',
                        'RELEASE_TAG': 'v1.1.0', 'RELEASE_COMMIT': 'commit'})
            result = subprocess.run(['bash', '-e', '-c', script], cwd=root, env=env,
                                    text=True, capture_output=True)
            calls = (root / 'calls').read_text().splitlines() if (root / 'calls').exists() else []
            return result.returncode, calls

    def test_publisher_creates_new_ref_before_draft_and_verifies_before_publish(self):
        status, calls = self.run_publisher('success')
        self.assertEqual(0, status)
        self.assertTrue(calls[0].startswith('api --method POST '))
        self.assertIn('--verify-tag --draft', calls[1])
        self.assertEqual(2, sum(call.startswith('api repos/') for call in calls))
        self.assertTrue(calls[-1].startswith('release edit '))

    def test_publisher_never_publishes_existing_or_changed_tag(self):
        for mode in ('existing', 'changed', 'changed_late', 'checksum'):
            with self.subTest(mode=mode):
                status, calls = self.run_publisher(mode)
                self.assertNotEqual(0, status)
                self.assertFalse(any(call.startswith('release edit ') for call in calls))
                if mode in ('existing', 'checksum'):
                    self.assertFalse(any(call.startswith('release create ') for call in calls))
                if mode == 'checksum':
                    self.assertEqual([], calls)


class DependencyAuditTests(unittest.TestCase):
    def test_resolved_versions_and_project_dependencies(self):
        report = r"""
+--- com.example:library:1.0 -> 2.0
+--- androidx.compose.ui:ui -> 1.10.0
|    +--- org.sample:transitive:3.0 (*)
|    \--- org.example:old:1.0 -> org.example:new:2.1
+--- project :game-core
+--- org.test:constraint:1.0 (c)
\--- org.sample:replaced:1.0 -> project :replacement
"""
        self.assertEqual({('com.example', 'library', '2.0'), ('androidx.compose.ui', 'ui', '1.10.0'), ('org.sample', 'transitive', '3.0'),
                          ('org.example', 'new', '2.1')}, dependency_audit.parse_dependencies(report))

    def test_unresolved_dependencies_cannot_pass(self):
        for line in ('+--- com.example:broken:1.0 FAILED', '+--- com.example:broken:1.0 (n)'):
            with self.subTest(line=line), self.assertRaises(ValueError):
                dependency_audit.parse_dependencies(line)

    def test_license_lookup_reads_pom_and_inherits_parent_license(self):
        dependency_audit.pom_licenses.cache_clear()
        child = b'<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent></project>'
        parent = b'<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>Apache-2.0</name><url>https://example.test/license</url></license></licenses></project>'
        with patch('dependency_audit.urllib.request.urlopen', side_effect=[io.BytesIO(child), io.BytesIO(parent)]):
            self.assertEqual(('Apache-2.0 https://example.test/license',),
                             dependency_audit.pom_licenses(('org.example', 'child', '1.0')))

    def test_osv_findings_and_incomplete_responses(self):
        coords = [('com.example', 'library', '2.0'), ('org.sample', 'safe', '3.0')]
        with patch('dependency_audit.get_json', return_value={'results': [{'vulns': [{'id': 'GHSA-test'}]}, {}]}):
            self.assertEqual([('com.example:library:2.0', 'GHSA-test')], dependency_audit.find_vulnerabilities(coords))
        with patch('dependency_audit.get_json', return_value={'results': []}):
            with self.assertRaises(ValueError):
                dependency_audit.find_vulnerabilities(coords)


class CleanupTests(unittest.TestCase):
    def test_only_known_artifacts_strictly_older_than_one_day(self):
        now = dt.datetime(2026, 11, 6, 12, tzinfo=dt.timezone.utc)
        pages = [{'artifacts': [
            {'id': 1, 'name': 'armadillo-ci-debug-apk', 'created_at': '2026-11-05T11:59:59Z'},
            {'id': 2, 'name': 'armadillo-ci-debug-apk', 'created_at': '2026-11-05T12:00:00Z'},
            {'id': 3, 'name': 'official-release', 'created_at': '2020-01-01T00:00:00Z'},
            {'id': 4, 'name': 'armadillo-ci-signed-release', 'created_at': '2026-11-06T10:00:00Z'},
        ]}, {'artifacts': [{'id': 5, 'name': 'debug-check-log', 'created_at': '2020-01-01T00:00:00Z'}]}]
        self.assertEqual([1, 5], cleanup_artifacts.stale_artifact_ids(pages, now))

    def test_cleanup_uses_only_actions_artifact_endpoint(self):
        now = dt.datetime(2026, 11, 6, 12, tzinfo=dt.timezone.utc)
        page = '{"artifacts":[{"id":7,"name":"armadillo-ci-debug-apk","created_at":"2020-01-01T00:00:00Z"}]}'
        with patch.dict('os.environ', {'GITHUB_REPOSITORY': 'owner/repo'}), \
             patch('subprocess.check_output', return_value='[' + page + ']'), \
             patch('subprocess.run') as run:
            cleanup_artifacts.main()
        self.assertEqual(['gh', 'api', '--method', 'DELETE', 'repos/owner/repo/actions/artifacts/7'], run.call_args.args[0])


if __name__ == '__main__':
    unittest.main()
