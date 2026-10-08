#!/data/data/com.tool.tree/files/home/termux/bin/python
# -*- coding: utf-8 -*-

"""apex_compression_tool is a tool that can compress/decompress APEX."""

from __future__ import print_function
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from zipfile import ZipFile
import apex_manifest_pb2


def RunCommand(cmd, verbose=False, env=None, expected_return_values=None):
    expected_return_values = expected_return_values or {0}
    env = env or {}
    env.update(os.environ.copy())

    if verbose:
        print('Running: ' + ' '.join(cmd))
    p = subprocess.Popen(
        cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    output, _ = p.communicate()

    if verbose or p.returncode not in expected_return_values:
        print(output.rstrip())

    assert p.returncode in expected_return_values, 'Failed to execute: ' + ' '.join(cmd)

    return output, p.returncode


def AddOriginalApexDigestToManifest(capex_manifest_path, apex_image_path, verbose=False):
    avbtool_cmd = ['avbtool', 'print_partition_digests', '--image', apex_image_path]
    root_digest = RunCommand(avbtool_cmd, verbose=verbose)[0].decode().split(': ')[1].strip()

    with open(capex_manifest_path, 'rb') as f:
        pb = apex_manifest_pb2.ApexManifest()
        pb.ParseFromString(f.read())

    assert not pb.supportsRebootlessUpdate, "Rebootless updates not supported for compressed APEXs"

    capex_metadata = apex_manifest_pb2.ApexManifest().CompressedApexMetadata()
    capex_metadata.originalApexDigest = root_digest
    pb.capexMetadata.CopyFrom(capex_metadata)

    with open(capex_manifest_path, 'wb') as f:
        f.write(pb.SerializeToString())

    return True


def SignCapex(input_capex, output_capex, key_name="testkey", verbose=False):
    key_dir = "etc/key"
    
    # Kiểm tra file .pem (hoặc .x509.pem nếu định dạng file có đuôi mở rộng này)
    pem = os.path.join(key_dir, f"{key_name}.pem")
    if not os.path.exists(pem):
        pem_alt = os.path.join(key_dir, f"{key_name}.x509.pem")
        if os.path.exists(pem_alt):
            pem = pem_alt

    pk8 = os.path.join(key_dir, f"{key_name}.pk8")

    cmd = [
        "java", "-jar",
        "lib/signapk.jar",
        "-a", "60",
        pem, pk8,
        input_capex,
        output_capex
    ]

    RunCommand(cmd, verbose=verbose)
    print(f"I: Signed APEX successfully: {output_capex}")


def RunCompress(args, work_dir):
    """Compress an uncompressed APEX into compressed APEX using 'zip'."""

    # Copy original APEX
    original_apex = os.path.join(work_dir, 'original_apex')
    shutil.copy2(args.input, original_apex)

    # Extract meta files
    extract_dir = os.path.join(work_dir, 'extract')
    os.makedirs(extract_dir, exist_ok=True)

    with ZipFile(original_apex, 'r') as zip_obj:
        for meta_file in ['apex_manifest.json', 'apex_manifest.pb',
                          'apex_pubkey', 'apex_build_info.pb',
                          'AndroidManifest.xml']:
            if meta_file in zip_obj.namelist():
                zip_obj.extract(meta_file, path=extract_dir)

        # Extract apex_payload.img for digest
        zip_obj.extract('apex_payload.img', path=work_dir)
        image_path = os.path.join(work_dir, 'apex_payload.img')

    # Update digest in apex_manifest.pb
    apex_manifest_path = os.path.join(extract_dir, 'apex_manifest.pb')
    assert AddOriginalApexDigestToManifest(apex_manifest_path, image_path, args.verbose)

    # Tạm lưu file nén chưa ký
    temp_output = args.output.replace(".capex", "_unsign.capex")

    # Create zip: include original_apex + meta files
    # Create zip using zipfile:
    # - original_apex: compressed (-9)
    # - meta files: stored (-0)
    with zipfile.ZipFile(temp_output, 'w') as z:
        # original_apex → nén tối đa
        z.write(
            original_apex,
            arcname='original_apex',
            compress_type=zipfile.ZIP_DEFLATED,
            compresslevel=9
        )
    
        # meta files → không nén
        for name in os.listdir(extract_dir):
            path = os.path.join(extract_dir, name)
            z.write(
                path,
                arcname=name,
                compress_type=zipfile.ZIP_STORED
            )

    print(f"I: Compressed (unsigned) APEX created: {temp_output}")

    # Sign và ghi đè file gốc
    SignCapex(temp_output, args.output, key_name=args.key, verbose=args.verbose)

    # Xóa file tạm _unsign
    os.remove(temp_output)

    print(f"I: Final signed APEX: {args.output}")

    return True


def ParseArgs(argv):
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(required=True, dest='cmd')

    parser_compress = subparsers.add_parser('compress', help='Compress an APEX')
    parser_compress.add_argument('-v', '--verbose', action='store_true', help='verbose execution')
    parser_compress.add_argument('-k', '--key', type=str, default='testkey', help='Key name in etc/key (default: testkey)')
    parser_compress.add_argument('--input', type=str, required=True, help='Input APEX file path')
    parser_compress.add_argument('--output', type=str, required=True, help='Output compressed APEX path')
    parser_compress.set_defaults(func=RunCompress)

    return parser.parse_args(argv)


class TempDirectory(object):
    def __enter__(self):
        self.name = tempfile.mkdtemp()
        return self.name
    def __exit__(self, *unused):
        shutil.rmtree(self.name)


def main(argv):
    args = ParseArgs(argv)
    with TempDirectory() as work_dir:
        success = args.func(args, work_dir)
    if not success:
        sys.exit(1)


if __name__ == '__main__':
    main(sys.argv[1:])
