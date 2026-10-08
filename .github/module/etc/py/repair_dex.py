#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import os
import struct
import hashlib
import zlib

def read_uleb128(data, offset):
    """Safely read ULEB128 data"""
    result = 0
    shift = 0
    while True:
        if offset >= len(data):
            return 0, offset
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7f) << shift
        if (byte & 0x80) == 0:
            break
        shift += 7
        if shift >= 35:
            break
    return result, offset

def is_map_list_valid(data, map_off):
    """Check if the current map_list is valid (Smart Detection)"""
    # 1. map_off must be within the allowed range
    if map_off < 112 or map_off + 4 > len(data):
        return False

    # 2. Read the number of items in map_list
    size = struct.unpack('<I', data[map_off : map_off + 4])[0]
    
    # A standard DEX file typically contains between 7 and ~20 map_item types
    if size == 0 or size > 50:
        return False

    # 3. Check if the total size of map_list exceeds the file length
    expected_map_end = map_off + 4 + (size * 12)
    if expected_map_end > len(data):
        return False

    return True

def rebuild_map_list(data):
    """Rebuild standard map_list structure based on Header parameters"""
    # Read section parameters from Header
    sections = [
        (0x0000, 1, 0),                                                    # TYPE_HEADER_ITEM
        (0x0001, struct.unpack('<I', data[56:60])[0], struct.unpack('<I', data[60:64])[0]),   # TYPE_STRING_ID_ITEM
        (0x0002, struct.unpack('<I', data[64:68])[0], struct.unpack('<I', data[68:72])[0]),   # TYPE_TYPE_ID_ITEM
        (0x0003, struct.unpack('<I', data[72:76])[0], struct.unpack('<I', data[76:80])[0]),   # TYPE_PROTO_ID_ITEM
        (0x0004, struct.unpack('<I', data[80:84])[0], struct.unpack('<I', data[84:88])[0]),   # TYPE_FIELD_ID_ITEM
        (0x0005, struct.unpack('<I', data[88:92])[0], struct.unpack('<I', data[92:96])[0]),   # TYPE_METHOD_ID_ITEM
        (0x0006, struct.unpack('<I', data[96:100])[0], struct.unpack('<I', data[100:104])[0]),# TYPE_CLASS_DEF_ITEM
    ]

    # Filter out sections that actually exist (size > 0 and valid offset)
    valid_items = []
    for type_code, size, offset in sections:
        if size > 0 and (offset < len(data) or type_code == 0x0000):
            valid_items.append((type_code, size, offset))

    # Place new map_list at the end of the file (requires 4-byte alignment)
    new_map_off = len(data)
    if new_map_off % 4 != 0:
        padding = 4 - (new_map_off % 4)
        data.extend(b'\x00' * padding)
        new_map_off = len(data)

    # Add TYPE_MAP_LIST itself to the list of items
    valid_items.append((0x1000, 1, new_map_off))

    # Sort items by ascending offset
    valid_items.sort(key=lambda x: x[2])

    # Pack new map_list data
    map_bytes = bytearray()
    map_bytes.extend(struct.pack('<I', len(valid_items)))  # Item count
    for type_code, size, offset in valid_items:
        # DexMapItem structure: type (2B), unused (2B), size (4B), offset (4B)
        map_bytes.extend(struct.pack('<HHII', type_code, 0, size, offset))

    # Append map_list to the end of the DEX file
    data.extend(map_bytes)

    # Update map_off in Header (Offset 0x34)
    struct.pack_into('<I', data, 52, new_map_off)
    return new_map_off

def repair_dex(dex_path, output_path=None):
    if not os.path.isfile(dex_path):
        raise FileNotFoundError(f"File not found: '{dex_path}'!")

    if output_path is None:
        output_path = dex_path

    print(f"Repairing DEX file: {dex_path}")

    with open(dex_path, 'rb') as f:
        data = bytearray(f.read())

    if len(data) < 112:
        raise ValueError("File is too small, not a valid DEX structure!")

    # 1. Safely restore Magic Header
    if not data.startswith(b'dex\n'):
        ver = data[4:8]
        if not (ver[:3].isdigit() and ver[3] == 0):
            ver = b'035\0'
        data[0:8] = b'dex\n' + ver
        print(f"-> Restored Magic Header to 'dex\\n{ver[:3].decode('ascii')}\\0'")

    # Read Header parameters
    string_ids_size = struct.unpack('<I', data[56:60])[0]
    class_defs_size = struct.unpack('<I', data[96:100])[0]
    class_defs_off = struct.unpack('<I', data[100:104])[0]
    data_size = struct.unpack('<I', data[104:108])[0]
    data_off = struct.unpack('<I', data[108:112])[0]
    map_off = struct.unpack('<I', data[52:56])[0]

    # 2. Remove Overlay Junk (Trim extra data at the end of the file)
    expected_size = data_off + data_size
    if 112 < expected_size < len(data):
        print(f"-> Trimmed Overlay Junk: Reduced from {len(data):,} bytes to {expected_size:,} bytes.")
        data = data[:expected_size]

    # 3. Fix corrupted ClassDefs (source_file_idx) & Debug Info
    fixed_source_idx = 0
    fixed_debug_info = 0

    if 0 < class_defs_off < len(data):
        for i in range(class_defs_size):
            def_offset = class_defs_off + (i * 32)
            if def_offset + 32 > len(data):
                break

            source_file_idx = struct.unpack('<I', data[def_offset + 16 : def_offset + 20])[0]
            if source_file_idx != 0xFFFFFFFF and source_file_idx >= string_ids_size:
                struct.pack_into('<I', data, def_offset + 16, 0xFFFFFFFF)
                fixed_source_idx += 1

            class_data_off = struct.unpack('<I', data[def_offset + 24 : def_offset + 28])[0]
            if 0 < class_data_off < len(data):
                curr = class_data_off
                s_fields, curr = read_uleb128(data, curr)
                i_fields, curr = read_uleb128(data, curr)
                d_methods, curr = read_uleb128(data, curr)
                v_methods, curr = read_uleb128(data, curr)

                for _ in range(s_fields + i_fields):
                    if curr >= len(data): break
                    _, curr = read_uleb128(data, curr)
                    _, curr = read_uleb128(data, curr)

                for _ in range(d_methods + v_methods):
                    if curr >= len(data): break
                    _, curr = read_uleb128(data, curr)
                    _, curr = read_uleb128(data, curr)
                    code_off, curr = read_uleb128(data, curr)

                    if code_off > 0 and code_off + 12 <= len(data):
                        debug_off = struct.unpack('<I', data[code_off + 8 : code_off + 12])[0]
                        if debug_off >= len(data) or (0 < debug_off < 112):
                            struct.pack_into('<I', data, code_off + 8, 0)
                            fixed_debug_info += 1

    if fixed_source_idx > 0:
        print(f"-> Restored {fixed_source_idx} invalid source_file_idx.")
    if fixed_debug_info > 0:
        print(f"-> Fixed {fixed_debug_info} corrupted debug_info_off entries back to 0.")

    # 4. Smart Detection & Rebuild map_list if corrupted
    if not is_map_list_valid(data, map_off):
        new_map_off = rebuild_map_list(data)
        print(f"-> Corrupted/missing map_list detected (old map_off: {hex(map_off)}). Rebuilt map_list at offset: {hex(new_map_off)}")
    else:
        print(f"-> Valid map_list (map_off: {hex(map_off)}). Skipping rebuild.")

    # 5. Update Header file_size
    new_size = len(data)
    struct.pack_into('<I', data, 32, new_size)

    # 6. Recalculate SHA-1 Signature (Offset 12 - 31)
    sha1_hash = hashlib.sha1(data[32:]).digest()
    data[12:32] = sha1_hash

    # 7. Recalculate Adler-32 Checksum (Offset 8 - 11)
    adler32_val = zlib.adler32(data[12:]) & 0xffffffff
    struct.pack_into('<I', data, 8, adler32_val)

    # 8. Write repaired file
    with open(output_path, 'wb') as f:
        f.write(data)

    print(f"=> Repair complete! Saved output to: '{output_path}'")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python repair_dex.py <dex_file> [output_file]")
        print("Example: python repair_dex.py classes.dex")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else input_file
    try:
        repair_dex(input_file, output_file)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)
