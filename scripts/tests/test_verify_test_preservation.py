import unittest
import os
import sys
from pathlib import Path
import tempfile
import json
import shutil

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

    def test_path_resolution_is_root_relative(self):
        # SCRIPT_DIR should point to scripts/
        self.assertEqual(verify_test_preservation.SCRIPT_DIR.name, "scripts")
        # REPO_ROOT should be parent
        self.assertTrue((verify_test_preservation.REPO_ROOT / "app").exists())

if __name__ == "__main__":
    unittest.main()
