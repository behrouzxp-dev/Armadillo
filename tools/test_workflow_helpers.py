"""Regression tests for release guardrails and temporary-artifact selection."""
import datetime as dt
import json
import io
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

    def test_owner_must_be_the_only_required_reviewer(self):
        env = {'protection_rules': [{'type': 'required_reviewers', 'reviewers': [
            {'type': 'User', 'reviewer': {'login': 'owner'}}]}]}
        release_preflight.validate_approval(env, 'owner')
        with self.assertRaises(ValueError):
            release_preflight.validate_approval(env, 'different-owner')
        env['protection_rules'][0]['reviewers'].append({'type': 'User', 'reviewer': {'login': 'other'}})
        with self.assertRaises(ValueError):
            release_preflight.validate_approval(env, 'owner')
        with self.assertRaises(ValueError):
            release_preflight.validate_approval({}, 'owner')


    def test_preflight_checks_history_and_rejects_duplicate_or_old_codes(self):
        env_data = {'protection_rules': [{'type': 'required_reviewers', 'reviewers': [
            {'type': 'User', 'reviewer': {'login': 'owner'}}]}]}
        settings = {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_REPOSITORY_OWNER': 'owner',
                    'RELEASE_TAG': 'v1.1.0', 'REF_PROTECTED': 'true'}
        with tempfile.TemporaryDirectory() as tmp:
            settings['GITHUB_OUTPUT'] = str(Path(tmp) / 'output')
            releases = [{'tag_name': 'v1.0.0'}]
            metadata = {'applicationId': 'com.armadillo.game', 'elements': [{'versionName': '1.0.0', 'versionCode': 1}]}

            def fake_command(*args):
                if args[:3] == ('gh', 'api', 'repos/owner/repo/environments/production'):
                    return json.dumps(env_data)
                if args[:4] == ('gh', 'api', '--paginate', '--slurp'):
                    return json.dumps([releases])
                if args[:2] == ('git', 'rev-parse'):
                    return 'samecommit'
                if args[:2] == ('git', 'show'):
                    return 'versionName = "1.0.0"\nversionCode = 1\n'
                if args[:3] == ('gh', 'release', 'download'):
                    Path(args[-1], 'output-metadata.json').write_text(json.dumps(metadata))
                    return ''
                raise AssertionError(args)

            with patch.dict('os.environ', settings), patch('release_preflight.command', side_effect=fake_command), \
                 patch('release_preflight.parse_gradle', side_effect=[('1.1.0', 2), ('1.0.0', 1), ('1.1.0', 2), ('1.0.0', 1), ('1.1.0', 1), ('1.0.0', 1)]), \
                 patch('release_preflight.subprocess.run'):
                release_preflight.main()
                releases.append({'tag_name': 'v1.1.0'})
                with self.assertRaises(ValueError):
                    release_preflight.main()
                releases.pop()
                with self.assertRaises(ValueError):
                    release_preflight.main()
            self.assertIn('version=1.1.0\ncode=2', Path(settings['GITHUB_OUTPUT']).read_text())

    def test_unprotected_tag_is_rejected_before_api_access(self):
        with patch.dict('os.environ', {'RELEASE_TAG': 'v1.1.0', 'GITHUB_REPOSITORY': 'owner/repo',
                                      'GITHUB_REPOSITORY_OWNER': 'owner', 'REF_PROTECTED': 'false'}), \
             patch('release_preflight.command') as command:
            with self.assertRaises(ValueError):
                release_preflight.main()
            command.assert_not_called()


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
