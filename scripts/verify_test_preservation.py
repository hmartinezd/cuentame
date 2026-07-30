import os
import re
import json
import sys
import subprocess
from pathlib import Path

# Configuration
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
TEST_PATHS = ["app/src/test", "app/src/androidTest"]
BASELINE_FILE = REPO_ROOT / "docs" / "TEST_BASELINE_PARENT_REVISION.json"
FINAL_INVENTORY_FILE = REPO_ROOT / "docs" / "TEST_BASELINE_FINAL_REVISION.json"
REPORT_FILE = REPO_ROOT / "docs" / "TEST_PRESERVATION_REPORT.md"
REPLACEMENT_MAP_FILE = REPO_ROOT / "docs" / "TEST_REPLACEMENT_MAP.json"

# Regex patterns
TEST_ANNOTATION_PATTERNS = [
    re.compile(r"^\s*@Test\b", re.MULTILINE),
    re.compile(r"^\s*@org\.junit\.Test\b", re.MULTILINE),
    re.compile(r"^\s*@org\.junit\.jupiter\.api\.Test\b", re.MULTILINE),
]
IGNORE_ANNOTATION_PATTERNS = [
    re.compile(r"^\s*@Ignore\b", re.MULTILINE),
    re.compile(r"^\s*@org\.junit\.Ignore\b", re.MULTILINE),
    re.compile(r"^\s*@Disabled\b", re.MULTILINE),
    re.compile(r"^\s*@org\.junit\.jupiter\.api\.Disabled\b", re.MULTILINE),
]
# Only count supported test annotations
SKIP_ANNOTATIONS = re.compile(r"@TestInstallIn|@TestOnly|@TestFactory|@TestConfiguration")

FUN_PATTERN = re.compile(r"fun\s+([a-zA-Z0-9_` \-\.]+)\s*\(")
CLASS_PATTERN = re.compile(r"^\s*(?:private\s+|internal\s+)?(?:class|object)\s+([a-zA-Z0-9_]+)", re.MULTILINE)

def get_git_revision(ref):
    try:
        return subprocess.check_output(["git", "rev-parse", ref], cwd=REPO_ROOT).decode().strip()
    except:
        return None

def list_files_in_git(ref, paths):
    files = []
    for path in paths:
        try:
            output = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", ref, path], cwd=REPO_ROOT).decode()
            files.extend(output.splitlines())
        except:
            pass
    return [f for f in files if f.endswith(".kt") or f.endswith(".java")]

def read_file_from_git(ref, path):
    return subprocess.check_output(["git", "show", f"{ref}:{path}"], cwd=REPO_ROOT).decode(errors='replace').splitlines()

def scan_content(lines, file_path):
    inventory = []
    current_class = "Unknown"

    for i, line in enumerate(lines):
        class_match = CLASS_PATTERN.search(line)
        if class_match:
            current_class = class_match.group(1)

        is_test = any(p.search(line) for p in TEST_ANNOTATION_PATTERNS)
        if is_test and not SKIP_ANNOTATIONS.search(line):
            method_name = "Unknown"
            is_ignored = False

            # Check for ignore before
            if i > 0 and any(p.search(lines[i-1]) for p in IGNORE_ANNOTATION_PATTERNS):
                is_ignored = True

            for j in range(i + 1, min(i + 10, len(lines))):
                if any(p.search(lines[j]) for p in IGNORE_ANNOTATION_PATTERNS):
                    is_ignored = True
                fun_match = FUN_PATTERN.search(lines[j])
                if fun_match:
                    method_name = fun_match.group(1).strip().strip('`')
                    break

            if method_name == "Unknown":
                print(f"Error: Failed to identify test method at {file_path}:{i+1}", file=sys.stderr)
                sys.exit(1)
            if current_class == "Unknown":
                # Some files might not have a class (top-level fun, though rare for @Test)
                # But for consistency, let's keep it or fail.
                pass

            inventory.append({
                "file": str(file_path),
                "class": current_class,
                "method": method_name,
                "disabled": is_ignored
            })
    return inventory

def scan_working_tree():
    inventory = []
    for test_path in TEST_PATHS:
        full_path = REPO_ROOT / test_path
        if not full_path.exists():
            continue
        for path in full_path.rglob("*"):
            if path.is_file() and (path.suffix == ".kt" or path.suffix == ".java"):
                with open(path, 'r', encoding='utf-8', errors='replace') as f:
                    lines = f.readlines()
                inventory.extend(scan_content(lines, path.relative_to(REPO_ROOT)))
    return inventory

def scan_git_tree(ref):
    inventory = []
    files = list_files_in_git(ref, TEST_PATHS)
    for f in files:
        lines = read_file_from_git(ref, f)
        inventory.extend(scan_content(lines, f))
    return inventory

def main():
    if len(sys.argv) > 1:
        mode = sys.argv[1]
    else:
        mode = "verify"

    parent_ref = "HEAD^"
    parent_sha = get_git_revision(parent_ref)

    if mode == "generate-baseline":
        if not parent_sha:
            print("Error: Could not find parent revision.", file=sys.stderr)
            sys.exit(1)
        inventory = scan_git_tree(parent_ref)
        data = {
            "base_revision": parent_sha,
            "generated_from_git": True,
            "tests": inventory
        }
        os.makedirs(BASELINE_FILE.parent, exist_ok=True)
        with open(BASELINE_FILE, 'w') as f:
            json.dump(data, f, indent=2)
        print(f"Generated baseline from {parent_sha} with {len(inventory)} tests.")
        return

    current_inventory = scan_working_tree()

    if mode == "generate-final":
        data = {
            "current_revision": get_git_revision("HEAD") or "working_tree",
            "tests": current_inventory
        }
        with open(FINAL_INVENTORY_FILE, 'w') as f:
            json.dump(data, f, indent=2)
        print(f"Generated final inventory with {len(current_inventory)} tests.")
        return

    # Verify mode
    if not BASELINE_FILE.exists():
        print(f"Error: Baseline {BASELINE_FILE} missing. Run 'generate-baseline' first.", file=sys.stderr)
        sys.exit(1)

    with open(BASELINE_FILE, 'r') as f:
        baseline_data = json.load(f)
    baseline = baseline_data["tests"]

    replacements = []
    if REPLACEMENT_MAP_FILE.exists():
        with open(REPLACEMENT_MAP_FILE, 'r') as f:
            replacements = json.load(f)

    current_map = {(t['file'], t['class'], t['method']): t for t in current_inventory}
    errors = []
    added = []
    removed = []
    disabled = []

    # Check baseline against current
    for b_test in baseline:
        key = (b_test['file'], b_test['class'], b_test['method'])
        if key not in current_map:
            # Check replacement
            replaced = False
            for r in replacements:
                if (r['previous_file'] == b_test['file'] and
                    r['previous_class'] == b_test['class'] and
                    r['previous_method'] == b_test['method']):
                    r_key = (r['replacement_file'], r['replacement_class'], r['replacement_method'])
                    if r_key in current_map:
                        replaced = True
                        break
            if not replaced:
                errors.append(f"REMOVED: {b_test['class']}.{b_test['method']} in {b_test['file']}")
                removed.append(b_test)
        else:
            c_test = current_map[key]
            if c_test['disabled'] and not b_test['disabled']:
                errors.append(f"DISABLED: {b_test['class']}.{b_test['method']} in {b_test['file']}")
                disabled.append(b_test)

    # Detect added
    baseline_keys = {(t['file'], t['class'], t['method']) for t in baseline}
    for c_test in current_inventory:
        key = (c_test['file'], c_test['class'], c_test['method'])
        if key not in baseline_keys:
            # check if it is a replacement target
            is_replacement = False
            for r in replacements:
                if (r['replacement_file'] == c_test['file'] and
                    r['replacement_class'] == c_test['class'] and
                    r['replacement_method'] == c_test['method']):
                    is_replacement = True
                    break
            if not is_replacement:
                added.append(c_test)

    # Write report
    with open(REPORT_FILE, 'w') as f:
        f.write("# Test Preservation Report\n\n")
        f.write(f"- **Base Revision**: {baseline_data.get('base_revision', 'Unknown')}\n")
        f.write(f"- **Current Revision**: {get_git_revision('HEAD') or 'Working Tree'}\n")
        f.write(f"- **Baseline Count**: {len(baseline)}\n")
        f.write(f"- **Current Count**: {len(current_inventory)}\n")
        f.write(f"- **Added**: {len(added)}\n")
        f.write(f"- **Removed**: {len(removed)}\n")
        f.write(f"- **Disabled**: {len(disabled)}\n")
        f.write(f"- **Mapped Replacements**: {len(replacements)}\n")
        f.write(f"- **Result**: {'FAIL' if errors else 'PASS'}\n\n")

        if errors:
            f.write("## Errors\n")
            for e in errors:
                f.write(f"- {e}\n")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"\nVerification FAILED. See {REPORT_FILE}", file=sys.stderr)
        sys.exit(1)
    else:
        print(f"Verification PASSED. {len(baseline)} tests preserved. See {REPORT_FILE}")

if __name__ == "__main__":
    main()
