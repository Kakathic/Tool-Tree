#!/data/data/com.tool.tree/files/home/termux/bin/python
# kakathic
import os
import struct
import sys

MAGIC = 0xd00dfeed

def read_u32_be(f):
    data = f.read(4)
    if len(data) < 4:
        return None
    return struct.unpack(">I", data)[0]

def split_dtb(filename):
    with open(filename, "rb") as f:
        index = 0
        while True:
            pos = f.tell()
            magic = read_u32_be(f)
            if magic is None:
                break
            if magic != MAGIC:
                print(f"Not a valid DTB magic at offset 0x{pos:X}")
                break

            totalsize = read_u32_be(f)
            if totalsize is None or totalsize == 0:
                break

            f.seek(pos)
            blob = f.read(totalsize)
            outname = f"{os.path.splitext(filename)[0]}.{index}"
            with open(outname, "wb") as out:
                out.write(blob)
            print(f"Wrote {outname} (size: {totalsize} bytes)")
            index += 1
            f.seek(pos + totalsize)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 split_dtb.py <multi.dtb>")
        sys.exit(1)
    split_dtb(sys.argv[1])