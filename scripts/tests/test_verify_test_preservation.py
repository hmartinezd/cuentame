import unittest
import os
import sys
from pathlib import Path
import json

# Add parent dir to path to import script
sys.path.append(str(Path(__file__).resolve().parent.parent))
import verify_test_preservation

class TestVerifyTestPreservation(unittest.TestCase):

    def test_scan_content_recognizes_annotations(self):
        content = """
        class MyTest {
            @Test
            fun normalName() {}

            @org.junit.Test
            fun fullyQualified() {}

            @org.junit.jupiter.api.Test
            fun junit5() {}

            @Ignore
            @Test
            fun ignored() {}

            @Test
            fun `backtick name`() {}
        }
        """
        inventory = verify_test_preservation.scan_content(content.splitlines(), "Test.kt")
        methods = [t['method'] for t in inventory]
        self.assertIn("normalName", methods)
        self.assertIn("fullyQualified", methods)
        self.assertIn("junit5", methods)
        self.assertIn("ignored", methods)
        self.assertIn("backtick name", methods)

        self.assertTrue(next(t for t in inventory if t['method'] == "ignored")['disabled'])

    def test_scan_content_skips_false_positives(self):
        content = """
        @TestInstallIn(components = [SingletonComponent::class])
        object TestModule {}

        @TestOnly
        fun helper() {}
        """
        inventory = verify_test_preservation.scan_content(content.splitlines(), "Module.kt")
        self.assertEqual(len(inventory), 0)

    def test_scan_content_fails_on_unknown_class(self):
        content = """
        // No class definition here
        @Test
        fun looseTest() {}
        """
        with self.assertRaises(verify_test_preservation.PreservationError):
            verify_test_preservation.scan_content(content.splitlines(), "Loose.kt")

    def test_scan_content_handles_multiple_classes(self):
        content = """
        class A {
            @Test
            fun testA() {}
        }
        class B {
            @Test
            fun testB() {}
        }
        """
        inventory = verify_test_preservation.scan_content(content.splitlines(), "File.kt")
        self.assertEqual(len(inventory), 2)
        self.assertEqual(next(t for t in inventory if t['method'] == "testA")['class'], "A")
        self.assertEqual(next(t for t in inventory if t['method'] == "testB")['class'], "B")

    def test_perform_verification_detects_removed_test(self):
        baseline = [{"file": "f1.kt", "class": "C1", "method": "m1", "disabled": False}]
        current = []
        errors, _, _, _ = verify_test_preservation.perform_verification(baseline, current, [])
        self.assertEqual(len(errors), 1)
        self.assertIn("REMOVED: C1.m1", errors[0])

    def test_perform_verification_detects_newly_disabled_test(self):
        baseline = [{"file": "f1.kt", "class": "C1", "method": "m1", "disabled": False}]
        current = [{"file": "f1.kt", "class": "C1", "method": "m1", "disabled": True}]
        errors, _, _, _ = verify_test_preservation.perform_verification(baseline, current, [])
        self.assertEqual(len(errors), 1)
        self.assertIn("DISABLED: C1.m1", errors[0])

    def test_perform_verification_allows_replacement(self):
        baseline = [{"file": "f1.kt", "class": "C1", "method": "m1", "disabled": False}]
        current = [{"file": "f2.kt", "class": "C2", "method": "m2", "disabled": False}]
        replacements = [{
            "previous_file": "f1.kt", "previous_class": "C1", "previous_method": "m1",
            "replacement_file": "f2.kt", "replacement_class": "C2", "replacement_method": "m2"
        }]
        errors, _, _, _ = verify_test_preservation.perform_verification(baseline, current, replacements)
        self.assertEqual(len(errors), 0)

    def test_path_resolution_is_root_relative(self):
        self.assertEqual(verify_test_preservation.SCRIPT_DIR.name, "scripts")
        self.assertTrue((verify_test_preservation.REPO_ROOT / "app").exists())

if __name__ == "__main__":
    unittest.main()
