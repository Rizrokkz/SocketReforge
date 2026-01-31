import os
import json
import re
from pathlib import Path

# Paths
WEAPON_DIR = Path("src/main/resources/Server/Item/Items/Weapon")
LANG_FILE = Path("src/main/resources/Server/Languages/en-US/server.lang")

def parse_lang_file(lang_path):
    """Parse the server.lang file and extract upgrade entries"""
    upgrades = {}  # base_name -> {1: "Sharp Crude Sword", 2: "Deadly Crude Sword", 3: "Legendary Crude Sword"}
    
    with open(lang_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            
            # Match pattern: items.Weapon_Sword_Crude1.name = Sharp Crude Sword
            match = re.match(r'items\.(Weapon_\w+)(\d+)\.name\s*=\s*(.+)', line)
            if match:
                base_name = match.group(1)
                level = int(match.group(2))
                display_name = match.group(3)
                
                if base_name not in upgrades:
                    upgrades[base_name] = {}
                upgrades[base_name][level] = display_name
    
    return upgrades

def find_weapon_json_files(weapon_dir):
    """Find all weapon JSON files in the Weapon directory"""
    weapon_files = {}
    
    for root, dirs, files in os.walk(weapon_dir):
        for file in files:
            if file.endswith('.json'):
                full_path = Path(root) / file
                # Get the base name without .json
                base_name = file[:-5]
                weapon_files[base_name] = full_path
    
    return weapon_files

def create_upgraded_weapon(base_json_path, base_name, level, new_name_key):
    """Create an upgraded version of a weapon JSON file"""
    # Read the base JSON
    with open(base_json_path, 'r', encoding='utf-8') as f:
        weapon_data = json.load(f)
    
    # Update the TranslationProperties.Name
    if 'TranslationProperties' in weapon_data and 'Name' in weapon_data['TranslationProperties']:
        old_name = weapon_data['TranslationProperties']['Name']
        # Replace the base name with the upgraded name (e.g., server.items.Weapon_Sword_Crude.name -> server.items.Weapon_Sword_Crude1.name)
        new_name = old_name.replace(f".{base_name}.", f".{new_name_key}.")
        weapon_data['TranslationProperties']['Name'] = new_name
    
    # Determine the output path
    output_dir = base_json_path.parent
    output_name = f"{base_name}{level}.json"
    output_path = output_dir / output_name
    
    # Write the upgraded JSON
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(weapon_data, f, indent=2)
    
    return output_path

def main():
    print("Parsing server.lang file...")
    upgrades = parse_lang_file(LANG_FILE)
    print(f"Found {len(upgrades)} base weapons with upgrades")
    
    # Show some examples
    for base_name in list(upgrades.keys())[:5]:
        print(f"  {base_name}: {upgrades[base_name]}")
    
    print("\nFinding weapon JSON files...")
    weapon_files = find_weapon_json_files(WEAPON_DIR)
    print(f"Found {len(weapon_files)} weapon JSON files")
    
    # Show some examples
    for name in list(weapon_files.keys())[:5]:
        print(f"  {name}: {weapon_files[name]}")
    
    # Find matches and create upgraded files
    created_count = 0
    skipped_count = 0
    
    for base_name, levels in upgrades.items():
        # Check if the base weapon exists
        if base_name not in weapon_files:
            print(f"Warning: Base weapon '{base_name}' not found in JSON files, skipping...")
            skipped_count += len(levels)
            continue
        
        base_json_path = weapon_files[base_name]
        
        for level, display_name in levels.items():
            new_name_key = f"{base_name}{level}"
            try:
                output_path = create_upgraded_weapon(base_json_path, base_name, level, new_name_key)
                print(f"Created: {output_path}")
                created_count += 1
            except Exception as e:
                print(f"Error creating {new_name_key}: {e}")
    
    print(f"\nDone! Created {created_count} upgraded weapon files, skipped {skipped_count}")

if __name__ == "__main__":
    main()
