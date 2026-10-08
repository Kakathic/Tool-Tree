#!/data/data/com.tool.tree/files/home/termux/bin/python
import struct
import sys

def make_smart_fake_zip(zip_path):
    with open(zip_path, 'r+b') as f:
        data = bytearray(f.read())
        
    offset = 0
    patched_count = 0
    data_len = len(data)
    
    while offset < data_len:
        # Tìm chính xác chữ ký PK\x01\x02 tiếp theo từ vị trí hiện tại
        offset = data.find(b'PK\x01\x02', offset)
        if offset == -1:
            break
            
        flag_offset = offset + 8
        if flag_offset + 2 > data_len:
            break
            
        # Bật cờ mật khẩu giả
        flag = struct.unpack('<H', data[flag_offset:flag_offset+2])[0]
        fake_flag = flag | 0x0001
        struct.pack_into('<H', data, flag_offset, fake_flag)
        
        # Dịch chuyển offset lên 1 byte để tiếp tục tìm mục kế tiếp một cách chính xác tuyệt đối
        offset += 1
        patched_count += 1

    with open(zip_path, 'wb') as f:
        f.write(data)
        
    print(f"✅ Đã tạo bẫy mật khẩu giả thành công cho toàn bộ {patched_count} mục!")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        make_smart_fake_zip(sys.argv[1])
    else:
        print("Cách dùng: python fake_zip.py <file.zip>")