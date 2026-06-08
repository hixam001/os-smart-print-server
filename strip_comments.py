import os
import re
import sys

def strip_comments(code):
    # This regex matches:
    # 1. Single-line comments: // ...
    # 2. Multi-line comments: /* ... */
    # 3. String literals: "..." or '...' or `...`
    # We want to remove 1 and 2, but preserve 3 so we don't accidentally remove "http://..."
    pattern = re.compile(
        r'(?://[^\n]*)|(?:/\*.*?\*/)|("(?:\\.|[^\\"])*")|(\'(?:\\.|[^\\\'])*\')|(`(?:\\.|[^\\`])*`)',
        re.DOTALL
    )
    
    def replacer(match):
        # If any of the string groups matched, return the string exactly as is
        if match.group(1) is not None:
            return match.group(1)
        if match.group(2) is not None:
            return match.group(2)
        if match.group(3) is not None:
            return match.group(3)
        # Otherwise, it was a comment -> return empty string to remove it
        return ""
    
    stripped = pattern.sub(replacer, code)
    
    # Cleanup trailing whitespaces on lines
    stripped = re.sub(r'[ \t]+$', '', stripped, flags=re.MULTILINE)
    # Condense multiple empty lines (3 or more) to max 2 empty lines
    stripped = re.sub(r'\n{3,}', '\n\n', stripped)
    
    return stripped

def process_directory(directory, extensions):
    count = 0
    for root, dirs, files in os.walk(directory):
        for file in files:
            if any(file.endswith(ext) for ext in extensions):
                filepath = os.path.join(root, file)
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                new_content = strip_comments(content)
                
                if new_content != content:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    count += 1
    return count

if __name__ == "__main__":
    print("Stripping backend...")
    backend_count = process_directory("backend/src/main/java", [".java"])
    print(f"Updated {backend_count} Java files.")

    print("Stripping frontend...")
    frontend_count = process_directory("frontend/src", [".js", ".jsx", ".css"])
    print(f"Updated {frontend_count} Frontend files.")
