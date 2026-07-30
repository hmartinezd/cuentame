import os
import re
import json
import sys

# Configuration
TEST_DIRS = ["app/src/test", "app/src/androidTest"]
BASELINE_FILE = "docs/TEST_BASELINE_PARENT_REVISION.json"
REPLACEMENT_MAP_FILE = "docs/TEST_REPLACEMENT_MAP.json"

# Regex patterns
TEST_ANNOTATION_PATTERN = re.compile(r"@(org\.junit\.)?Test")
IGNORE_ANNOTATION_PATTERN = re.compile(r"@(org\.junit\.)?Ignore|@Disabled")
FUN_PATTERN = re.compile(r"fun\s+([a-zA-Z0-9_` ]+)\s*\(")
CLASS_PATTERN = re.compile(r"(class|object)\s+([a-zA-Z0-9_]+)")

def scan_tests():
    inventory = []
    for root_dir in TEST_DIRS:
        if not os.path.exists(root_dir):
            continue
        for root, _, files in os.walk(root_dir):
            for file in files:
                if not (file.endswith(".kt") or file.endswith(".java")):
                    continue

                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    lines = f.readlines()

                current_class = "Unknown"
                for i, line in enumerate(lines):
                    class_match = CLASS_PATTERN.search(line)
                    if class_match:
                        current_class = class_match.group(2)

                    if TEST_ANNOTATION_PATTERN.search(line):
                        # Look for method name in next few lines
                        method_name = "Unknown"
                        is_ignored = False

                        # Check if preceded by ignore
                        if i > 0 and IGNORE_ANNOTATION_PATTERN.search(lines[i-1]):
                            is_ignored = True

                        for j in range(i + 1, min(i + 5, len(lines))):
                            fun_match = FUN_PATTERN.search(lines[j])
                            if fun_match:
                                method_name = fun_match.group(1).strip().strip('`')
                                break
                            if IGNORE_ANNOTATION_PATTERN.search(lines[j]):
                                is_ignored = True

                        inventory.append({
                            "file": path,
                            "class": current_class,
                            "method": method_name,
                            "disabled": is_ignored
                        })
    return inventory

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "verify"

    current_inventory = scan_tests()

    if mode == "generate":
        os.makedirs(os.path.dirname(BASELINE_FILE), exist_ok=True)
        with open(BASELINE_FILE, 'w') as f:
            json.dump(current_inventory, f, indent=2)
        print(f"Generated baseline with {len(current_inventory)} tests.")
        return

    if not os.path.exists(BASELINE_FILE):
        print(f"Error: Baseline file {BASELINE_FILE} not found. Run with 'generate' first.")
        sys.exit(1)

    with open(BASELINE_FILE, 'r') as f:
        baseline = json.load(f)

    replacements = []
    if os.path.exists(REPLACEMENT_MAP_FILE):
        with open(REPLACEMENT_MAP_FILE, 'r') as f:
            replacements = json.load(f)

    # Check for removals
    errors = []
    current_map = {(t['class'], t['method']): t for t in current_inventory}

    for b_test in baseline:
        key = (b_test['class'], b_test['method'])
        if key not in current_map:
            # Check replacements
            replaced = False
            for r in replacements:
                if r['previous_class'] == b_test['class'] and r['previous_method'] == b_test['method']:
                    # Verify replacement exists
                    r_key = (r['replacement_class'], r['replacement_method'])
                    if r_key in current_map:
                        replaced = True
                        break

            if not replaced:
                errors.append(f"Test removed: {b_test['class']}.{b_test['method']} in {b_test['file']}")
        else:
            c_test = current_map[key]
            if c_test['disabled'] and not b_test['disabled']:
                errors.append(f"Test disabled: {b_test['class']}.{b_test['method']} in {b_test['file']}")

    if errors:
        print("\n".join(errors))
        print(f"\nFailed: {len(errors)} test preservation errors found.")
        sys.exit(1)
    else:
        print(f"Success: All {len(baseline)} baseline tests preserved. (Current total: {len(current_inventory)})")

if __name__ == "__main__":
    main()
