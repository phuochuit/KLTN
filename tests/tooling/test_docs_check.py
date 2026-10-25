import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('docs_check', ROOT / 'scripts/check-docs.py')
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class DocsCheckTests(unittest.TestCase):
    def setUp(self):
        base = ROOT / '.cache/test-temp'
        base.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=base, prefix='docs-check-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def document(self, name, text):
        path = self.root / name
        path.write_text(text, encoding='utf-8')
        return path

    def test_local_only_and_secrets_blocked(self):
        for path in ['AGENTS.md', '.opencode/skills/a.md', '.artifacts/a', 'tasks/a',
                     '.cache/a', '.codegraph/a', 'a.pem', 'a.key', '.env.local', 'a/.env']:
            self.assertTrue(check.blocked(path), path)

    def test_source_and_examples_allowed(self):
        for path in ['docs/README.md', 'scripts/test.ps1', '.env.local.example',
                     'spring-web/src/main/resources/application.properties']:
            self.assertFalse(check.blocked(path), path)

    def test_heading_duplicates_unicode_and_code(self):
        self.assertEqual(check.anchors('# Cấu hình `API`\n# Cấu hình `API`\n'),
                         {'cấu-hình-api', 'cấu-hình-api-1'})

    def test_fenced_headings_excluded(self):
        self.assertEqual(check.anchors('```text\n# hidden\n```\n# Visible\n'), {'visible'})

    def test_encoded_relative_target_and_anchor(self):
        self.document('target space.md', '# Section\n')
        source = self.document('README.md', '[link](target%20space.md#section)\n')
        self.assertEqual(check.check_file(source), [])

    def test_missing_target_and_anchor_detected(self):
        self.document('target.md', '# Section\n')
        source = self.document('README.md', '[x](missing.md) [y](target.md#absent)\n')
        self.assertEqual(len(check.check_file(source)), 2)

    def test_external_links_not_requested(self):
        source = self.document('README.md', '[x](https://example.invalid/a) [m](mailto:test@example.invalid)\n')
        self.assertEqual(check.check_file(source), [])

    def test_whitespace_fence_and_conflict_detected(self):
        source = self.document('README.md', 'trailing  \n<<<<<<< conflict\n```text\n')
        self.assertEqual(len(check.check_file(source)), 3)


if __name__ == '__main__':
    unittest.main()
