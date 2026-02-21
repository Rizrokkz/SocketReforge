import re
from pathlib import Path

# Path to the server.lang file
LANG_FILE = Path("src/main/resources/Server/Languages/en-US/server.lang")

# Read the current server.lang file
with open(LANG_FILE, 'r', encoding='utf-8') as f:
    content = f.read()

# Define the replacements - match Sharp/Deadly/Legendary after the "= "
# Sharp X -> X +1, Deadly X -> X +2, Legendary X -> X +3
replacements = [
    (r'= Sharp (.+)$', r'= \1 +1'),
    (r'= Deadly (.+)$', r'= \1 +2'),
    (r'= Legendary (.+)$', r'= \1 +3'),
]

# Apply replacements
new_lines = []
replacement_count = 0
for line in content.split('\n'):
    original_line = line
    if line.strip() and not line.startswith('#'):
        # Apply each replacement pattern
        for pattern, replacement in replacements:
            new_line = re.sub(pattern, replacement, line)
            if new_line != line:
                print(f"Replaced: {line.strip()}")
                print(f"     With: {new_line.strip()}")
                line = new_line
                replacement_count += 1
                break
    new_lines.append(line)

# Write the updated content back to server.lang
new_content = '\n'.join(new_lines)
with open(LANG_FILE, 'w', encoding='utf-8') as f:
    f.write(new_content)

print(f"\nDone! Updated {replacement_count} entries in server.lang with +1/+2/+3 suffix format.")
