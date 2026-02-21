#!/usr/bin/env python3
"""
External Third Party Asset Patcher for SocketReforge

This script patches weapons from external asset sources:
1. Opens Assets.zip and grabs all weapon_.json files
2. Opens each jar in the MOD_ROOT and grabs all json treated as weapons
3. Clones each item to WEAPONS_PATH directory + subfolders per weapon type
4. Generates all weapon upgrade clones (levels 1-3)
5. Generates server.lang file in LANG_PATH
6. Generates manifest.json in MANIFEST_PATH
"""

import os
import sys
import json
import zipfile
import re
import argparse
from pathlib import Path
from typing import Dict, Set, Optional

# ============================================================================
# PATH CONFIGURATION
# ============================================================================

MOD_ROOT = "./mods/irai.mod.reforge.SocketReforge"
WEAPONS_PATH = MOD_ROOT + "/Server/Item/Items/Weapon"
LANG_PATH = MOD_ROOT + "/Server/Languages/en-US/server.lang"
MANIFEST_PATH = MOD_ROOT + "/manifest.json"

MAX_UPGRADE_LEVEL = 3

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def ensure_directory(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def is_actual_weapon(content: str, filename: str) -> bool:
    """Check if the JSON content is actually a weapon."""
    if filename.lower().startswith("weapon_"):
        # Check Categories
        cat_idx = content.find('"Categories"')
        if cat_idx >= 0:
            arr_open = content.find('[', cat_idx)
            if arr_open >= 0:
                arr_close = content.find(']', arr_open)
                if arr_close >= 0 and 'Items.Weapons' in content[arr_open:arr_close+1]:
                    return True
        
        # Check weapon properties
        props = ['"WeaponStats"', '"DamageProperties"', '"AttackProperties"', '"WeaponType"', '"RangedWeaponStats"']
        for prop in props:
            if prop in content:
                return True
    return False


def extract_weapon_id(content: str, filename: str) -> Optional[str]:
    """Extract weapon ID from JSON content."""
    patterns = [
        ('"server.items.', '.name"', 14),
        ('"item.', '.name"', 6),
        ('"items.', '.name"', 7),
    ]
    
    translation_start = content.find('"TranslationProperties"')
    search_from = translation_start if translation_start >= 0 else 0
    name_idx = content.find('"Name"', search_from)
    if name_idx < 0:
        name_idx = 0
    
    for prefix, suffix, skip in patterns:
        start = content.find(prefix, name_idx)
        if start < 0:
            start = content.find(prefix)
        if start < 0:
            continue
        
        end = content.find(suffix, start + skip)
        if end <= start:
            continue
        
        candidate = content[start + skip:end]
        if candidate.strip():
            return candidate
    
    if filename:
        return filename.replace('.json', '')
    return None


def build_friendly_name(item_id: str) -> str:
    """Build friendly name from weapon ID."""
    parts = item_id.split('_')
    if len(parts) < 3:
        return item_id
    
    weapon_type = parts[1].capitalize()
    last_part = parts[-1]
    
    if last_part[-1].isdigit():
        i = len(last_part) - 1
        while i >= 0 and last_part[i].isdigit():
            i -= 1
        level = last_part[i+1:]
        material = last_part[:i+1].capitalize()
    else:
        level = ""
        material = last_part.capitalize()
    
    if level:
        return f"{material} {weapon_type} +{level}"
    return f"{material} {weapon_type}"


# ============================================================================
# SCANNING FUNCTIONS
# ============================================================================

def scan_jar(jar_path: str) -> Dict[str, str]:
    """Scan a JAR for weapon JSON files."""
    weapons = {}
    
    try:
        with zipfile.ZipFile(jar_path, 'r') as zf:
            for name in zf.namelist():
                if name.endswith('.json'):
                    lower_name = name.lower()
                    if '/item/items/weapon/' not in lower_name:
                        continue
                    
                    filename = name.split('/')[-1]
                    if not filename.lower().startswith('weapon_'):
                        continue
                    
                    content = zf.read(name).decode('utf-8')
                    
                    if is_actual_weapon(content, filename):
                        weapon_id = extract_weapon_id(content, filename)
                        if weapon_id and not weapon_id[-1].isdigit():
                            weapons[weapon_id] = content
    except Exception as e:
        print(f"  [WARN] Error scanning {jar_path}: {e}")
    
    return weapons


def scan_folder(folder_path: str) -> Dict[str, str]:
    """Scan a folder for weapon JSON files."""
    weapons = {}
    
    weapon_path = os.path.join(folder_path, 'Server', 'Item', 'Items', 'Weapon')
    if not os.path.exists(weapon_path):
        weapon_path = os.path.join(folder_path, 'Item', 'Items', 'Weapon')
    
    if not os.path.exists(weapon_path):
        return weapons
    
    for root, dirs, files in os.walk(weapon_path):
        for file in files:
            if file.endswith('.json'):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                if is_actual_weapon(content, file):
                    weapon_id = extract_weapon_id(content, file)
                    if weapon_id and not weapon_id[-1].isdigit():
                        weapons[weapon_id] = content
    
    return weapons


def scan_mods(mods_path: str) -> Dict[str, str]:
    """Scan all mods in the mods folder."""
    all_weapons = {}
    
    if not os.path.exists(mods_path):
        print(f"[ERROR] Mods path not found: {mods_path}")
        return all_weapons
    
    for file in os.listdir(mods_path):
        if file.endswith('.jar') or file.endswith('.zip'):
            file_lower = file.lower()
            if file_lower == 'assets.zip':
                continue
            if 'irai.mod.reforge.socketreforge' in file_lower:
                continue
            
            jar_path = os.path.join(mods_path, file)
            print(f"  [*] Scanning: {file}")
            weapons = scan_jar(jar_path)
            print(f"    -> Found {len(weapons)} weapons")
            all_weapons.update(weapons)
    
    return all_weapons


# ============================================================================
# OUTPUT FUNCTIONS
# ============================================================================

def clone_weapons(weapons: Dict[str, str], output_path: str) -> int:
    """Clone base weapons to output path."""
    count = 0
    weapons_path = os.path.join(output_path, 'Server', 'Item', 'Items', 'Weapon')
    ensure_directory(weapons_path)
    
    for weapon_id, content in weapons.items():
        parts = weapon_id.split('_')
        weapon_type = parts[1] if len(parts) > 1 else 'Weapon'
        subfolder = weapon_type.capitalize()
        
        target_dir = os.path.join(weapons_path, subfolder)
        ensure_directory(target_dir)
        
        target_file = os.path.join(target_dir, f"{weapon_id}.json")
        with open(target_file, 'w', encoding='utf-8') as f:
            f.write(content)
        count += 1
    
    return count


def create_upgrades(weapons: Dict[str, str], output_path: str) -> int:
    """Create upgrade clones for all base weapons."""
    count = 0
    weapons_path = os.path.join(output_path, 'Server', 'Item', 'Items', 'Weapon')
    
    for weapon_id, content in weapons.items():
        parts = weapon_id.split('_')
        weapon_type = parts[1] if len(parts) > 1 else 'Weapon'
        subfolder = weapon_type.capitalize()
        
        target_dir = os.path.join(weapons_path, subfolder)
        ensure_directory(target_dir)
        
        for level in range(1, MAX_UPGRADE_LEVEL + 1):
            upgrade_id = f"{weapon_id}{level}"
            
            # Modify content
            result = content
            for prefix in ['server.items.', 'item.', 'items.']:
                old_key = f'"{prefix}{weapon_id}.name"'
                new_key = f'"{prefix}{upgrade_id}.name"'
                if old_key in result:
                    result = result.replace(old_key, new_key)
                    break
            
            # Remove Recipe block
            result = remove_recipe_block(result)
            
            output_file = os.path.join(target_dir, f"{upgrade_id}.json")
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(result)
            count += 1
    
    return count


def remove_recipe_block(json_str: str) -> str:
    """Remove Recipe block from JSON."""
    key_start = json_str.find('"Recipe"')
    if key_start < 0:
        return json_str
    
    brace_start = json_str.find('{', key_start)
    if brace_start < 0:
        return json_str
    
    depth = 0
    brace_end = -1
    for i in range(brace_start, len(json_str)):
        c = json_str[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                brace_end = i
                break
    
    if brace_end < 0:
        return json_str
    
    before = json_str[:key_start]
    after = json_str[brace_end + 1:]
    after = re.sub(r'^\s*,', '', after)
    
    if re.match(r'(?s)^\s*\}', after):
        before = re.sub(r',\s*$', '', before)
    
    return before + after


def generate_server_lang(weapon_ids: Set[str], lang_path: str) -> int:
    """Generate server.lang entries."""
    ensure_directory(os.path.dirname(lang_path))
    
    all_ids = set(weapon_ids)
    for base_id in weapon_ids:
        for level in range(1, MAX_UPGRADE_LEVEL + 1):
            all_ids.add(f"{base_id}{level}")
    
    lines = ["# Auto-patched weapon entries\n"]
    for weapon_id in sorted(all_ids):
        lines.append(f"items.{weapon_id}.name = {build_friendly_name(weapon_id)}\n")
    
    with open(lang_path, 'w', encoding='utf-8') as f:
        f.writelines(lines)
    
    return len(all_ids)


def generate_manifest(manifest_path: str) -> bool:
    """Generate manifest.json if it doesn't exist."""
    if os.path.exists(manifest_path):
        return False
    
    ensure_directory(os.path.dirname(manifest_path))
    
    manifest = {
        "Group": "irai.mod.reforge",
        "Name": "SocketReforge",
        "Version": "1.0.0",
        "Description": "Weapon upgrade and reforge system - Patched with External Asset Patcher",
        "Authors": [{"Name": "iRaiden", "Email": "animus0416@gmail.com", "Url": "https://github.com/Rizrokkz"}],
        "Website": "",
        "Dependencies": {},
        "OptionalDependencies": {},
        "LoadBefore": {},
        "DisabledByDefault": False,
        "IncludesAssetPack": True,
        "SubPlugins": [],
        "Main": "irai.mod.reforge.ReforgePlugin"
    }
    
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2)
    
    return True


# ============================================================================
# MAIN FUNCTION
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="External Third Party Asset Patcher for SocketReforge"
    )
    
    parser.add_argument(
        'assets_path',
        nargs='?',
        default='Assets.zip',
        help='Path to Assets.zip or Assets folder (default: Assets.zip)'
    )
    
    parser.add_argument(
        '-o', '--output',
        default=None,
        help=f'Custom MOD_ROOT path (default: {MOD_ROOT})'
    )
    
    args = parser.parse_args()
    
    mod_root = args.output if args.output else MOD_ROOT
    weapons_path = os.path.join(mod_root, "Server/Item/Items/Weapon")
    lang_path = os.path.join(mod_root, "Server/Languages/en-US/server.lang")
    manifest_path = os.path.join(mod_root, "manifest.json")
    
    print("=" * 60)
    print("External Third Party Asset Patcher for SocketReforge")
    print("=" * 60)
    print(f"[*] Mods Path: mods")
    print(f"[*] Output Path: {mod_root}")
    print("=" * 60)
    
    # Scan mods for weapons
    print("\n[STEP 1] Scanning mod JARs...")
    all_weapons = scan_mods('mods')
    print(f"[*] Total weapons found: {len(all_weapons)}")
    
    if not all_weapons:
        print("\n[ERROR] No weapons found!")
        sys.exit(1)
    
    # Create output directory
    ensure_directory(mod_root)
    
    # Clone base weapons
    print("\n[STEP 2] Cloning base weapons...")
    base_count = clone_weapons(all_weapons, mod_root)
    print(f"[*] Cloned {base_count} base weapons")
    
    # Create upgrades
    print("\n[STEP 3] Creating weapon upgrades...")
    upgrade_count = create_upgrades(all_weapons, mod_root)
    print(f"[*] Created {upgrade_count} upgrade files")
    
    # Generate server.lang
    print("\n[STEP 4] Generating server.lang...")
    lang_count = generate_server_lang(set(all_weapons.keys()), lang_path)
    print(f"[*] Added {lang_count} entries to server.lang")
    
    # Generate manifest
    print("\n[STEP 5] Generating manifest.json...")
    manifest_created = generate_manifest(manifest_path)
    
    # Summary
    print("\n" + "=" * 60)
    print("PATCH SUMMARY")
    print("=" * 60)
    print(f"Base weapons:   {base_count}")
    print(f"Upgrades:      {upgrade_count}")
    print(f"Lang entries:  {lang_count}")
    print(f"Manifest:      {'created' if manifest_created else 'already exists'}")
    print(f"Output dir:    {mod_root}")
    print("=" * 60)
    print("\n[SUCCESS] Asset patching complete!")


if __name__ == "__main__":
    main()
