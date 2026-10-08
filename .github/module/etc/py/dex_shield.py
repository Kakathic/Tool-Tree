#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import os
import struct
import hashlib
import zlib

def read_uleb128(data, offset):
    result = 0
    shift = 0
    size = len(data)
    while offset < size:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7f) << shift
        if (byte & 0x80) == 0:
            break
        shift += 7
        if shift >= 35:
            break
    return result, offset

def crash_decompiler_safely(dex_path):
    if not os.path.isfile(dex_path):
        print(f"❌ Không tìm thấy file: {dex_path}")
        sys.exit(1)

    with open(dex_path, 'rb') as f:
        data = bytearray(f.read())

    if len(data) < 112 or not data.startswith(b'dex\n'):
        print("❌ File DEX không hợp lệ!")
        sys.exit(1)

    # 1. Align 4-byte trước khi chèn payload
    orig_len = len(data)
    aligned_off = (orig_len + 3) & ~3
    if aligned_off > orig_len:
        data.extend(b'\x00' * (aligned_off - orig_len))

    bad_debug_off = len(data)

    # 2. Payload HỢP LỆ với dex2oat nhưng BẪY Decompiler Java:
    # line_start = 1 (ULEB128: 0x01)
    # parameters_size = 0 (ULEB128: 0x00)
    # Opcode = DBG_ADVANCE_PC với uleb128 tràn viền (0x80 0x80 0x80 0x80 0x7f)
    # Hoặc Opcode kết thúc không hợp lệ trong luồng Java Parser
    # bad_debug_bytes = b'\x00\xff\xff\xff\xff\x7f\x00'
    bad_debug_bytes = b'\x01\x00\x01\x80\x80\x80\x80\x7f\x00'
    data.extend(bad_debug_bytes)

    # Đọc ClassDefs
    class_defs_size = struct.unpack('<I', data[96:100])[0]
    class_defs_off = struct.unpack('<I', data[100:104])[0]

    patched_methods = 0
    data_len = len(data)

    for i in range(class_defs_size):
        def_offset = class_defs_off + (i * 32)
        class_data_off = struct.unpack('<I', data[def_offset + 24 : def_offset + 28])[0]

        if class_data_off == 0 or class_data_off >= data_len:
            continue

        curr = class_data_off
        static_fields_size, curr = read_uleb128(data, curr)
        instance_fields_size, curr = read_uleb128(data, curr)
        direct_methods_size, curr = read_uleb128(data, curr)
        virtual_methods_size, curr = read_uleb128(data, curr)

        # Bỏ qua Fields
        for _ in range(static_fields_size + instance_fields_size):
            _, curr = read_uleb128(data, curr)
            _, curr = read_uleb128(data, curr)

        # Trỏ debug_info_off về bad_debug_off
        for _ in range(direct_methods_size + virtual_methods_size):
            _, curr = read_uleb128(data, curr)
            _, curr = read_uleb128(data, curr)
            code_off, curr = read_uleb128(data, curr)

            if code_off > 0 and (code_off + 12) <= data_len:
                struct.pack_into('<I', data, code_off + 8, bad_debug_off)
                patched_methods += 1

    # Cập nhật Header
    struct.pack_into('<I', data, 32, len(data))
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into('<I', data, 8, zlib.adler32(data[12:]) & 0xffffffff)

    with open(dex_path, 'wb') as f:
        f.write(data)

    print(f"✅ Đã vá {patched_methods} phương thức.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Cú pháp: python dex_shield.py <file_dex>")
        sys.exit(1)
    crash_decompiler_safely(sys.argv[1])
