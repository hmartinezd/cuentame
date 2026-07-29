import os
import re
import glob

def find_test_files():
    files = []
    for root, dirs, filenames in os.walk("app/src"):
        for filename in filenames:
            if filename.endswith(".kt") or filename.endswith(".java"):
                filepath = os.path.join(root, filename)
                if "test" in filepath.split(os.sep):
                    files.append(filepath)
    return sorted(files)

def parse_test_file(filepath):
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    lines = content.splitlines()
    
    # Check if there's any @Test
    if "@Test" not in content and "@org.junit.Test" not in content:
        return None

    # Subsystem
    is_instrumentation = "src/androidTest" in filepath
    test_type = "Instrumentation" if is_instrumentation else "JVM"
    
    # Class name
    class_match = re.search(r'(?:class|object)\s+([A-Za-z0-9_]+)', content)
    class_name = class_match.group(1) if class_match else os.path.splitext(os.path.basename(filepath))[0]

    # Package / subsystem
    pkg_match = re.search(r'package\s+([A-Za-z0-9_\.]+)', content)
    pkg = pkg_match.group(1) if pkg_match else ""
    
    subsystem = "Core"
    if "feature/waste" in filepath or ".feature.waste" in pkg:
        subsystem = "Feature: Waste"
    elif "feature/purchases" in filepath or ".feature.purchases" in pkg:
        subsystem = "Feature: Purchases"
    elif "feature/counts" in filepath or ".feature.counts" in pkg:
        subsystem = "Feature: Stock Counts"
    elif "feature/onboarding" in filepath or ".feature.onboarding" in pkg:
        subsystem = "Feature: Onboarding"
    elif "feature/reports" in filepath or ".feature.reports" in pkg:
        subsystem = "Feature: Reports"
    elif "feature/settings" in filepath or ".feature.settings" in pkg:
        subsystem = "Feature: Settings"
    elif "feature/ingredients" in filepath or ".feature.ingredients" in pkg:
        subsystem = "Feature: Ingredients"
    elif "feature/home" in filepath or ".feature.home" in pkg:
        subsystem = "Feature: Home"
    elif "core/backup" in filepath or ".core.backup" in pkg:
        subsystem = "Core: Backup"
    elif "core/database" in filepath or ".core.database" in pkg:
        subsystem = "Core: Database"
    elif "core/domain" in filepath or ".core.domain" in pkg:
        subsystem = "Core: Domain"

    # Find test methods
    test_methods = []
    
    # Look line by line for @Test and method signature
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith("@Test") or "@Test" in line:
            is_ignored = False
            # Check adjacent lines for @Ignore or @Disabled
            j = max(0, i - 3)
            k = min(len(lines), i + 4)
            for check_idx in range(j, k):
                if "@Ignore" in lines[check_idx] or "@Disabled" in lines[check_idx]:
                    is_ignored = True
                    break
            
            # Find next method header
            method_name = None
            for m_idx in range(i, min(len(lines), i + 10)):
                m_line = lines[m_idx].strip()
                # e.g. fun `test name`() or fun testName()
                m = re.search(r'fun\s+(`[^`]+`|[A-Za-z0-9_]+)\s*\(', m_line)
                if m:
                    method_name = m.group(1)
                    break
                # Java method: public void testName()
                m_java = re.search(r'(?:public|private|protected)?\s*void\s+([A-Za-z0-9_]+)\s*\(', m_line)
                if m_java:
                    method_name = m_java.group(1)
                    break
            
            if method_name:
                test_methods.append({
                    "name": method_name,
                    "ignored": is_ignored
                })
        i += 1

    return {
        "path": filepath,
        "class_name": class_name,
        "type": test_type,
        "subsystem": subsystem,
        "methods": test_methods
    }

def generate_markdown(test_data, output_file):
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(f"# Baseline Test Inventory ({os.path.basename(output_file)})\n\n")
        f.write("This document provides a complete, non-truncated inventory of every test file, class, `@Test` method, execution type, and subsystem.\n\n")
        
        total_classes = len(test_data)
        total_methods = sum(len(td["methods"]) for td in test_data)
        ignored_methods = sum(sum(1 for m in td["methods"] if m["ignored"]) for td in test_data)
        
        f.write(f"- **Total Test Classes**: {total_classes}\n")
        f.write(f"- **Total Test Methods**: {total_methods}\n")
        f.write(f"- **Ignored / Disabled Methods**: {ignored_methods}\n\n")
        
        f.write("--- \n\n")
        f.write("| Subsystem | Type | Class Name | Test Method | Ignored |\n")
        f.write("| --- | --- | --- | --- | --- |\n")
        
        for td in test_data:
            for m in td["methods"]:
                ignored_str = "YES" if m["ignored"] else "NO"
                f.write(f"| {td['subsystem']} | {td['type']} | `{td['class_name']}` | `{m['name']}` | {ignored_str} |\n")

        f.write("\n\n## Complete Class Inventory\n\n")
        for td in test_data:
            f.write(f"### `{td['class_name']}`\n")
            f.write(f"- **Source Path**: `{td['path']}`\n")
            f.write(f"- **Subsystem**: {td['subsystem']}\n")
            f.write(f"- **Type**: {td['type']}\n")
            f.write(f"- **Method Count**: {len(td['methods'])}\n")
            f.write("#### Methods:\n")
            for m in td["methods"]:
                status = " (IGNORED)" if m["ignored"] else ""
                f.write(f"- `{m['name']}`{status}\n")
            f.write("\n")

if __name__ == "__main__":
    files = find_test_files()
    data = []
    for f in files:
        parsed = parse_test_file(f)
        if parsed and parsed["methods"]:
            data.append(parsed)
    
    generate_markdown(data, "docs/TEST_BASELINE_BEFORE_FINAL_GATE.md")
    print(f"Generated docs/TEST_BASELINE_BEFORE_FINAL_GATE.md with {len(data)} classes and {sum(len(d['methods']) for d in data)} methods.")
