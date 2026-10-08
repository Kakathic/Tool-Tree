#!/data/data/com.tool.tree/files/home/termux/bin/python
# kakathic
import sys
import os

def merge_dtbs(output_file, dtb_files):
    with open(output_file, 'wb') as out:
        for dtb in dtb_files:
            with open(dtb, 'rb') as f:
                data = f.read()
                out.write(data)
                print(f"Merged: {dtb} ({len(data)} bytes)")
    print(f"\nDone! Output: {output_file}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 merge_dtb.py <output.dtb> <input1.dtb> [input2.dtb] ...")
        sys.exit(1)

    output = sys.argv[1]
    inputs = sys.argv[2:]
    merge_dtbs(output, inputs)