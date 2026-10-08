#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import os
import apex_build_info_pb2

def normalize_path(path_str):
    p = path_str.strip()
    if not p:
        return None
        
    if 'apex_payload/' in p:
        p = p.split('apex_payload/')[-1]
    elif p == 'apex_payload':
        return None
        
    p = p.lstrip('/')
    
    if not p or 'apex_manifest.json' in p or 'lost+found' in p:
        return None
        
    return p

def check_all(base_dir):
    build_info_path = os.path.join(base_dir, "apex/apex_build_info.pb")
    payload_dir = os.path.join(base_dir, "apex_payload")
    config_file = os.path.join(base_dir, "config", "apex_payload_fs_config")

    try:
        if not os.path.exists(build_info_path):
            print(f"[-] Error: apex_build_info.pb not found at {build_info_path}", file=sys.stderr)
            return False

        with open(build_info_path, 'rb') as f:
            build_info = apex_build_info_pb2.ApexBuildInfo()
            build_info.ParseFromString(f.read())
            
        print("[+] APEX Configuration & Build Info:")
        build_info_str = str(build_info)
        printed_lines = set()
        for line in build_info_str.splitlines():
            line_str = line.strip()
            line_lower = line_str.lower()
            
            if 'u:object_r:' in line_lower or 'system_file' in line_lower or 'entry' in line_lower:
                continue
            if '<manifest' in line_lower or '</manifest>' in line_lower or 'android:hasCode' in line_lower:
                continue
                
            # Giữ lại target sdk và filesystem type, lọc bỏ min_sdk_version
            if any(kw in line_lower for kw in ['target_sdk_version', 'targetsdkversion', 'fs_type']):
                parts = line_str.split(':', 1)
                if len(parts) == 2:
                    key = parts[0].strip().lower()
                    if 'target_sdk_version' in key or 'targetsdkversion' in key:
                        key_display = 'Target SDK Version'
                    elif 'payload_fs_type' in key or 'fs_type' in key:
                        key_display = 'Payload Filesystem Type'
                    else:
                        key_display = parts[0].strip().replace('_', ' ').title()
                    
                    val = parts[1].strip().replace('"', '')
                    formatted_line = f" •  {key_display}: {val}"
                else:
                    formatted_line = f" •  {line_str.replace('"', '')}"
                    
                if formatted_line not in printed_lines:
                    print(formatted_line)
                    printed_lines.add(formatted_line)

        embedded_text = build_info.canned_fs_config.decode('utf-8', errors='ignore')
        
        expected_paths = set()
        for line in embedded_text.strip().splitlines():
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split()
            if len(parts) >= 4:
                norm = normalize_path(parts[0])
                if norm:
                    expected_paths.add(norm)

        config_paths = set()
        if os.path.exists(config_file):
            with open(config_file, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#'):
                        parts = line.split()
                        if parts:
                            norm = normalize_path(parts[0])
                            if norm:
                                config_paths.add(norm)

        actual_files = set()
        if os.path.isdir(payload_dir):
            for root, dirs, files in os.walk(payload_dir):
                for file in files:
                    full_p = os.path.join(root, file)
                    rel_p = os.path.relpath(full_p, payload_dir)
                    norm = normalize_path(rel_p)
                    if norm:
                        actual_files.add(norm)
                for d in dirs:
                    full_p = os.path.join(root, d)
                    rel_p = os.path.relpath(full_p, payload_dir)
                    norm = normalize_path(rel_p)
                    if norm:
                        actual_files.add(norm)

        missing_items = (expected_paths - config_paths).union(expected_paths - actual_files)
        extra_items = (config_paths - expected_paths).union(actual_files - expected_paths)

        has_error = False

        if missing_items:
            print(f"[-] Detected {len(missing_items)} missing item(s):", file=sys.stderr)
            for item in sorted(missing_items):
                print(f"  - {item}", file=sys.stderr)
            has_error = True

        if extra_items:
            print(f"[-] Detected {len(extra_items)} extra item(s):", file=sys.stderr)
            for item in sorted(extra_items):
                print(f"  - {item}", file=sys.stderr)
            has_error = True

        return not has_error

    except Exception as e:
        print(f"[-] Error: {e}", file=sys.stderr)
        return False

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python verify_apex.py <directory>", file=sys.stderr)
        sys.exit(1)
        
    base_dir_arg = sys.argv[1]
    success = check_all(base_dir_arg)
    sys.exit(0 if success else 1)
