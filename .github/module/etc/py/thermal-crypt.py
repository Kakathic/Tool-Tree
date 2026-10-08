#!/data/data/com.tool.tree/files/home/termux/bin/python

import argparse
import subprocess
import os

def main():
    parser = argparse.ArgumentParser(description='Encrypt/decrypt mi_thermald configs')
    parser.add_argument('-i', '--infile', required=True, help='Input filename')
    parser.add_argument('-o', '--outfile', required=True, help='Output filename')
    parser.add_argument('-e', '--encrypt', action='store_true',
                        help='Encrypt input plain text file to output file (default: decrypt)')
    args = parser.parse_args()

    # Key và IV, chuyển thành hex để dùng cho openssl
    key = "746865726d616c6f70656e73736c2e68"  # "thermalopenssl.h".encode().hex()
    iv = "746865726d616c6f70656e73736c2e68"

    if args.encrypt:
        cmd = [
            "openssl", "enc", "-aes-128-cbc",
            "-K", key, "-iv", iv,
            "-in", args.infile, "-out", args.outfile
        ]
    else:
        cmd = [
            "openssl", "enc", "-aes-128-cbc", "-d",
            "-K", key, "-iv", iv,
            "-in", args.infile, "-out", args.outfile
        ]

    # Chạy openssl qua subprocess
    result = subprocess.run(cmd, capture_output=True)
    if result.returncode != 0:
        print("Error:", result.stderr.decode())
        exit(1)
    else:
        print(f"Done: {args.outfile}")

if __name__ == '__main__':
    main()