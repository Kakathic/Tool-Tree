#!/data/data/com.tool.tree/files/home/termux/bin/python
import argparse
import datetime
import fnmatch
import glob
import os
import shutil
import struct
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

CACHE_FILENAME = ".cache_zip"
SIG_BLOCK_FILENAME = ".apk_sig_block"


def bname(path):
  """Lấy tên file hoặc thư mục ngắn gọn, loại bỏ đường dẫn."""
  if not path:
    return ""
  return os.path.basename(os.path.normpath(path))


def log_i(msg, quiet=False):
  if not quiet:
    print(f"I: {msg}")


def log_w(msg, quiet=False):
  if not quiet:
    print(f"W: {msg}")


def log_e(msg, quiet=False, file=sys.stderr):
  if not quiet:
    print(f"E: {msg}", file=file)


def parse_timestamp(ts_str):
  try:
    dt = datetime.datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
    return (dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
  except Exception:
    return (2009, 1, 1, 0, 0, 0)


# ==============================================================================
# HÀM XỬ LÝ NHỊ PHÂN CHO CHỮ KÝ APK V2/V3
# ==============================================================================


def find_eocd(f):
  f.seek(0, os.SEEK_END)
  filesize = f.tell()
  max_search = min(filesize, 65557)
  f.seek(filesize - max_search, os.SEEK_SET)
  buf = f.read(max_search)

  pos = buf.rfind(b"\x50\x4b\x05\x06")
  if pos == -1:
    return None, None, None

  eocd_pos = filesize - max_search + pos
  f.seek(eocd_pos + 12)
  cd_size, cd_offset = struct.unpack("<II", f.read(8))
  return eocd_pos, cd_size, cd_offset


def extract_apk_sig_block(zip_path):
  if not os.path.exists(zip_path):
    return None
  try:
    with open(zip_path, "rb") as f:
      eocd_pos, cd_size, cd_offset = find_eocd(f)
      if cd_offset is None or cd_offset < 24:
        return None

      f.seek(cd_offset - 16)
      if f.read(16) != b"APK Sig Block 42":
        return None

      f.seek(cd_offset - 24)
      sig_size_footer = struct.unpack("<Q", f.read(8))[0]

      block_start = cd_offset - (sig_size_footer + 8)
      if block_start < 0:
        return None

      f.seek(block_start)
      if struct.unpack("<Q", f.read(8))[0] != sig_size_footer:
        return None

      f.seek(block_start)
      return f.read(sig_size_footer + 8)
  except Exception:
    return None


def insert_apk_sig_block(zip_path, sig_block):
  if not sig_block or not os.path.exists(zip_path):
    return False
  try:
    with open(zip_path, "rb") as f:
      data = f.read()

    pos = data.rfind(b"\x50\x4b\x05\x06")
    if pos == -1:
      return False

    cd_size, cd_offset = struct.unpack("<II", data[pos + 12 : pos + 20])
    sig_len = len(sig_block)

    new_data = (
        data[:cd_offset]
        + sig_block
        + data[cd_offset:pos]
        + data[pos : pos + 16]
        + struct.pack("<I", cd_offset + sig_len)
        + data[pos + 20 :]
    )

    with open(zip_path, "wb") as f:
      f.write(new_data)
    return True
  except Exception:
    return False


# ==============================================================================
# CÁC HÀM XỬ LÝ ZIP CHÍNH
# ==============================================================================


def list_zip_contents(zip_path, password=None, quiet=False, patterns=None):
  if not os.path.exists(zip_path):
    log_e(f"File '{bname(zip_path)}' not found", quiet)
    return False

  pwd_bytes = password.encode("utf-8") if password else None
  try:
    with zipfile.ZipFile(zip_path, "r") as zip_ref:
      if pwd_bytes:
        zip_ref.setpassword(pwd_bytes)

      infolist = zip_ref.infolist()
      if patterns:
        infolist = [
            info
            for info in infolist
            if any(fnmatch.fnmatch(info.filename, p) for p in patterns)
        ]

      log_i(f"Archive: {bname(zip_path)}", quiet)
      print(
          f"{'Filename':<45} {'Compressed':<12} {'Uncompressed':<14}"
          f" {'Method':<10}"
      )
      print("-" * 83)

      total_comp, total_uncomp, count = 0, 0, 0
      for info in infolist:
        comp_type = (
            "Stored"
            if info.compress_type == zipfile.ZIP_STORED
            else "Deflated"
        )
        print(
            f"{info.filename:<45} {info.compress_size:<12}"
            f" {info.file_size:<14} {comp_type:<10}"
        )
        total_comp += info.compress_size
        total_uncomp += info.file_size
        count += 1

      print("-" * 83)
      log_i(
          f"Total: {count} files | Compressed: {total_comp}B | Original:"
          f" {total_uncomp}B",
          quiet,
      )
  except Exception as e:
    log_e(f"Read error: {e}", quiet)
    return False
  return True


def extract_to_stdout(zip_path, member_patterns, password=None):
  if not os.path.exists(zip_path):
    log_e(f"File '{bname(zip_path)}' not found")
    return False

  pwd_bytes = password.encode("utf-8") if password else None
  try:
    with zipfile.ZipFile(zip_path, "r") as zip_ref:
      if pwd_bytes:
        zip_ref.setpassword(pwd_bytes)

      matched = [
          info.filename
          for info in zip_ref.infolist()
          if any(fnmatch.fnmatch(info.filename, p) for p in member_patterns)
      ]
      if not matched:
        log_e(f"No members matched patterns: {member_patterns}")
        return False

      for name in matched:
        with zip_ref.open(name) as f:
          sys.stdout.buffer.write(f.read())
  except Exception as e:
    log_e(f"Extract to stdout failed: {e}")
    return False
  return True


def extract_zip(
    zip_path,
    extract_to,
    password=None,
    member_patterns=None,
    quiet=False,
    clear_source=False,
    force=False,
):
  if not os.path.exists(zip_path):
    log_e(f"File '{bname(zip_path)}' not found", quiet)
    return False

  if os.path.exists(extract_to) and os.listdir(extract_to):
    if not force:
      log_e(
          f"Directory '{bname(extract_to)}' exists. Use -f to force"
          " overwrite",
          quiet,
      )
      return False
    else:
      shutil.rmtree(extract_to)

  os.makedirs(extract_to, exist_ok=True)

  sig_block = extract_apk_sig_block(zip_path)
  pwd_bytes = password.encode("utf-8") if password else None

  try:
    with zipfile.ZipFile(zip_path, "r") as zip_ref:
      if pwd_bytes:
        zip_ref.setpassword(pwd_bytes)

      if member_patterns:
        matched_members = [
            info.filename
            for info in zip_ref.infolist()
            if any(fnmatch.fnmatch(info.filename, p) for p in member_patterns)
        ]
        if not matched_members:
          log_e(f"No files matched patterns '{member_patterns}'", quiet)
          return False

        log_i(
            f"Extracting {len(matched_members)} files: {bname(extract_to)}",
            quiet,
        )

        if sig_block:
          sig_file_path = os.path.join(extract_to, SIG_BLOCK_FILENAME)
          with open(sig_file_path, "wb") as sf:
            sf.write(sig_block)
          log_i("SigBlock v2/v3+ backed up", quiet)

        for member in matched_members:
          zip_ref.extract(member, extract_to)
      else:
        log_i(f"Extracting {bname(zip_path)} -> {bname(extract_to)}", quiet)

        if sig_block:
          sig_file_path = os.path.join(extract_to, SIG_BLOCK_FILENAME)
          with open(sig_file_path, "wb") as sf:
            sf.write(sig_block)
          log_i("SigBlock v2/v3+ backed up", quiet)

        xml_meta_path = os.path.join(extract_to, CACHE_FILENAME)
        root = ET.Element("ZipMetadata")

        for info in zip_ref.infolist():
          zip_ref.extract(info, extract_to)
          file_path = os.path.join(extract_to, info.filename)
          mtime = os.path.getmtime(file_path) if os.path.exists(file_path) else 0

          dt = info.date_time
          try:
            ts_str = f"{dt[0]:04d}-{dt[1]:02d}-{dt[2]:02d} {dt[3]:02d}:{dt[4]:02d}:{dt[5]:02d}"
          except Exception:
            ts_str = "2009-01-01 00:00:00"

          file_elem = ET.SubElement(root, "File")
          file_elem.set("filename", info.filename)
          file_elem.set("compress_type", str(info.compress_type))
          file_elem.set("external_attr", str(info.external_attr))
          file_elem.set("mtime", str(mtime))
          file_elem.set("timestamp", ts_str)
          file_elem.set("extra_hex", info.extra.hex() if info.extra else "")

        tree = ET.ElementTree(root)
        tree.write(xml_meta_path, encoding="utf-8", xml_declaration=True)
        log_i(f"Extracted to {bname(extract_to)}", quiet)

    if clear_source and os.path.exists(zip_path):
      os.remove(zip_path)
      log_i(f"Deleted original ZIP: {bname(zip_path)}", quiet)

  except Exception as e:
    log_e(f"Extraction failed: {e}", quiet)
    return False

  return True


def apply_correct_alignment(zout, zinfo, alignment=None):
  """Căn chỉnh (zipalign) chính xác dựa vào vị trí con trỏ ghi thực tế trong file ZIP."""
  if zinfo.compress_type != zipfile.ZIP_STORED:
    zinfo.extra = b""
    return

  if alignment is None:
    align_bytes = 4096 if zinfo.filename.endswith(".so") else 4
  else:
    align_bytes = alignment

  current_fp_pos = zout.fp.tell()
  header_base_size = 30
  filename_len = len(zinfo.filename.encode("utf-8"))

  offset_without_extra = current_fp_pos + header_base_size + filename_len
  remainder = offset_without_extra % align_bytes

  if remainder != 0:
    padding_len = align_bytes - remainder
    zinfo.extra = b"\x00" * padding_len
  else:
    zinfo.extra = b""


def inject_file_to_zip(
    zip_path,
    file_to_inject,
    arcname=None,
    forced_timestamp="2009-01-01 00:00:00",
    copy_from=None,
    quiet=False,
):
  if not os.path.exists(zip_path):
    log_e(f"Target ZIP '{bname(zip_path)}' not found", quiet)
    return False
  if not os.path.exists(file_to_inject):
    log_e(f"File to inject '{bname(file_to_inject)}' not found", quiet)
    return False

  if not arcname:
    arcname = os.path.basename(file_to_inject)

  sig_block = extract_apk_sig_block(zip_path)
  parsed_ts = parse_timestamp(forced_timestamp)

  target_compress_type = zipfile.ZIP_DEFLATED
  exists_in_zip = False

  with zipfile.ZipFile(zip_path, "r") as zin:
    for item in zin.infolist():
      if item.filename == arcname:
        exists_in_zip = True
        target_compress_type = item.compress_type
        break

  if not exists_in_zip:
    if copy_from:
      try:
        with zipfile.ZipFile(zip_path, "r") as zin:
          info_ref = zin.getinfo(copy_from)
          target_compress_type = info_ref.compress_type
      except KeyError:
        log_e(
            f"Template file '{copy_from}' does not exist in the ZIP to copy.",
            quiet,
        )
        return False
    else:
      target_compress_type = zipfile.ZIP_DEFLATED

  log_i(
      f"Injecting {bname(file_to_inject)} as {arcname} -> {bname(zip_path)}"
      f" [{'Existing file (kept config)' if exists_in_zip else f'New file' + (f' (copied from {copy_from})' if copy_from else '')}]",
      quiet,
  )

  tmp_dir = os.environ.get("TMP")
  if not tmp_dir:
    tmp_dir = tempfile.gettempdir()
  os.makedirs(tmp_dir, exist_ok=True)

  temp_zip = os.path.join(
      tmp_dir, f"zip_temp_{os.path.basename(zip_path)}_{os.getpid()}.tmp"
  )

  try:
    with zipfile.ZipFile(zip_path, "r") as zin, zipfile.ZipFile(
        temp_zip, "w"
    ) as zout:
      for item in zin.infolist():
        if item.filename == arcname:
          continue
        data = zin.read(item.filename)
        # Tính toán lại alignment cho các file STORED khác khi offset bị thay đổi
        if item.compress_type == zipfile.ZIP_STORED:
          apply_correct_alignment(zout, item)
        zout.writestr(item, data)

      zinfo = zipfile.ZipInfo.from_file(file_to_inject, arcname)
      zinfo.compress_type = target_compress_type
      zinfo.date_time = parsed_ts

      if zinfo.compress_type == zipfile.ZIP_STORED:
        apply_correct_alignment(zout, zinfo)

      with open(file_to_inject, "rb") as f:
        zout.writestr(zinfo, f.read())

    if sig_block:
      insert_apk_sig_block(temp_zip, sig_block)

    shutil.move(temp_zip, zip_path)
  except Exception as e:
    if os.path.exists(temp_zip):
      try:
        os.remove(temp_zip)
      except:
        pass
    log_e(f"Injection failed: {e}", quiet)
    return False

  compress_str = (
      "STORED" if target_compress_type == zipfile.ZIP_STORED else "DEFLATED"
  )
  sig_str = " | v2/v3+ preserved" if sig_block else ""
  log_i(
      f"Injected {bname(file_to_inject)} -> {bname(zip_path)}"
      f" [{compress_str}{sig_str}]",
      quiet,
  )
  return True


def repack_zip(
    source_dir,
    output_zip,
    exclude_patterns=None,
    no_compress_patterns=None,
    compression_level=6,
    update_only=False,
    forced_timestamp=None,
    quiet=False,
    clear_source=False,
    force=False,
):
  if not os.path.exists(source_dir):
    log_e(f"Source directory '{bname(source_dir)}' not found", quiet)
    return False

  if os.path.exists(output_zip):
    if not force:
      log_e(
          f"Output '{bname(output_zip)}' exists. Use -f to force overwrite",
          quiet,
      )
      return False
    else:
      log_i(f"Removing existing output '{bname(output_zip)}'", quiet)
      if os.path.isdir(output_zip):
        shutil.rmtree(output_zip)
      else:
        os.remove(output_zip)

  output_dir = os.path.dirname(os.path.abspath(output_zip))
  if output_dir:
    os.makedirs(output_dir, exist_ok=True)

  xml_meta_path = os.path.join(source_dir, CACHE_FILENAME)
  meta_map = {}

  if os.path.exists(xml_meta_path):
    log_i("Loaded metadata", quiet)
    tree = ET.parse(xml_meta_path)
    root = tree.getroot()
    for file_elem in root.findall("File"):
      fname = file_elem.get("filename")
      meta_map[fname] = {
          "compress_type": int(
              file_elem.get("compress_type", zipfile.ZIP_DEFLATED)
          ),
          "external_attr": int(file_elem.get("external_attr", 0)),
          "mtime": float(file_elem.get("mtime", 0)),
          "extra": bytes.fromhex(file_elem.get("extra_hex", "")),
          "timestamp": file_elem.get("timestamp", "2009-01-01 00:00:00"),
      }

  log_i(f"Repacking {bname(source_dir)} -> {bname(output_zip)}", quiet)

  if exclude_patterns is None:
    exclude_patterns = []
  if no_compress_patterns is None:
    no_compress_patterns = []

  forced_ts_parsed = (
      parse_timestamp(forced_timestamp) if forced_timestamp else None
  )

  with zipfile.ZipFile(
      output_zip,
      "w",
      zipfile.ZIP_DEFLATED,
      compresslevel=compression_level,
  ) as zipf:
    for root_dir, dirs, files in os.walk(source_dir):
      for file in files:
        file_path = os.path.join(root_dir, file)
        arcname = os.path.relpath(file_path, source_dir).replace(os.sep, "/")

        if arcname in (CACHE_FILENAME, SIG_BLOCK_FILENAME) or file in (
            CACHE_FILENAME,
            SIG_BLOCK_FILENAME,
        ):
          continue

        if any(
            fnmatch.fnmatch(file, p) or fnmatch.fnmatch(arcname, p)
            for p in exclude_patterns
        ):
          continue

        if update_only and arcname in meta_map:
          if os.path.getmtime(file_path) <= meta_map[arcname]["mtime"]:
            continue

        zinfo = zipfile.ZipInfo.from_file(file_path, arcname)

        if arcname in meta_map:
          zinfo.compress_type = meta_map[arcname]["compress_type"]
          zinfo.external_attr = meta_map[arcname]["external_attr"]
        else:
          zinfo.compress_type = zipfile.ZIP_DEFLATED

        for pattern in no_compress_patterns:
          if fnmatch.fnmatch(file, pattern) or fnmatch.fnmatch(arcname, pattern):
            zinfo.compress_type = zipfile.ZIP_STORED
            break

        if zinfo.compress_type == zipfile.ZIP_STORED:
          apply_correct_alignment(zipf, zinfo)

        if forced_ts_parsed is not None:
          zinfo.date_time = forced_ts_parsed
        elif arcname in meta_map and "timestamp" in meta_map[arcname]:
          zinfo.date_time = parse_timestamp(meta_map[arcname]["timestamp"])
        else:
          zinfo.date_time = parse_timestamp("2009-01-01 00:00:00")

        with open(file_path, "rb") as f:
          zipf.writestr(zinfo, f.read())

  sig_file_path = os.path.join(source_dir, SIG_BLOCK_FILENAME)
  if os.path.exists(sig_file_path):
    with open(sig_file_path, "rb") as sf:
      sig_block = sf.read()
    if insert_apk_sig_block(output_zip, sig_block):
      log_i("SigBlock v2/v3+ restored", quiet)

  log_i(f"Repacked -> {bname(output_zip)}", quiet)

  if clear_source and os.path.exists(source_dir):
    shutil.rmtree(source_dir)
    log_i(f"Deleted source directory: {bname(source_dir)}", quiet)

  return True


def main():
  parser = argparse.ArgumentParser(
      description=(
          "Advanced Python ZIP tool supporting multi-pattern filters and APK"
          " v2/v3+ signature preservation."
      )
  )

  parser.add_argument(
      "-e",
      "--extract",
      metavar="FILE",
      help="Path to the ZIP file (supports wildcards in batch mode)",
  )
  parser.add_argument(
      "-d",
      "--dir",
      default="extracted_folder",
      metavar="DIR",
      help="Directory to extract files into",
  )
  parser.add_argument(
      "-c",
      "--clear",
      action="store_true",
      help=(
          "Clear (delete) original ZIP file after extraction, or delete source"
          " directory after repacking"
      ),
  )
  parser.add_argument(
      "-f",
      "--force",
      action="store_true",
      help="Force remove existing destination directory/file before operation",
  )
  parser.add_argument(
      "-P",
      "--password",
      metavar="PWD",
      help="Password for encrypted ZIP files",
  )
  parser.add_argument(
      "-q",
      "--quiet",
      action="store_true",
      help="Quiet mode (suppresses logs during extraction or repacking)",
  )
  parser.add_argument(
      "-p",
      "--pipe",
      nargs="+",
      metavar="PATTERN",
      help="Extract multiple file(s) matching pattern(s) directly to stdout",
  )
  parser.add_argument(
      "-r", "--repack", metavar="DIR", help="Directory to pack into a ZIP file"
  )
  parser.add_argument(
      "-o", "--output", metavar="FILE", help="Output ZIP file name"
  )
  parser.add_argument(
      "-x",
      "--exclude",
      nargs="*",
      metavar="PATTERN",
      help="Files or patterns to exclude",
  )
  parser.add_argument(
      "-n",
      "--no-compress",
      nargs="*",
      metavar="PATTERN",
      help="Files or patterns to store without compression",
  )
  parser.add_argument(
      "-l",
      "--compression-level",
      type=int,
      default=6,
      metavar="LEVEL",
      help="Compression level (0-9)",
  )
  parser.add_argument(
      "--batch",
      action="store_true",
      help="Process multiple files matching pattern in extract mode",
  )
  parser.add_argument(
      "--update",
      action="store_true",
      help="Smart incremental repack: only pack files modified since extraction",
  )
  parser.add_argument(
      "--inject",
      nargs="+",
      metavar=("FILE", "TARGET_ZIP"),
      help=(
          "Inject/replace a file in ZIP. Usage: --inject FILE TARGET_ZIP"
          " [ARCNAME]"
      ),
  )
  parser.add_argument(
      "--copy-from",
      default=None,
      metavar="TEMPLATE_FILE",
      help=(
          "Copy compression type from an existing file in the ZIP when injecting"
          " a new file"
      ),
  )
  parser.add_argument(
      "--timestamp",
      default=None,
      metavar="YYYY-MM-DD HH:MM:SS",
      help="Override modification timestamp for all files during repack",
  )
  parser.add_argument(
      "--list",
      nargs="+",
      metavar="ARGS",
      help=(
          "List contents: first argument is ZIP file, followed by optional"
          " patterns"
      ),
  )
  parser.add_argument(
      "--member",
      nargs="+",
      metavar="PATTERN",
      help="Extract specific file(s) or pattern(s) matching these arguments",
  )

  args = parser.parse_args()

  if args.list:
    zip_file = args.list[0]
    patterns = args.list[1:] if len(args.list) > 1 else None
    list_zip_contents(
        zip_file, password=args.password, quiet=args.quiet, patterns=patterns
    )
  elif args.pipe:
    target_zip = (
        args.extract if args.extract else (args.list[0] if args.list else "")
    )
    if not target_zip:
      log_e("Please specify target ZIP file using -e or --list alongside -p.")
    else:
      extract_to_stdout(target_zip, args.pipe, password=args.password)
  elif args.inject:
    if len(args.inject) < 2:
      log_e("--inject requires at least 2 arguments: FILE TARGET_ZIP [ARCNAME]")
    else:
      file_to_inject = args.inject[0]
      target_zip = args.inject[1]
      arcname = args.inject[2] if len(args.inject) >= 3 else None

      inject_file_to_zip(
          target_zip,
          file_to_inject,
          arcname=arcname,
          forced_timestamp=(
              args.timestamp if args.timestamp else "2009-01-01 00:00:00"
          ),
          copy_from=args.copy_from,
          quiet=args.quiet,
      )
  elif args.extract:
    if args.batch:
      files = glob.glob(args.extract)
      for f in files:
        base_name = os.path.splitext(os.path.basename(f))[0]
        out_dir = os.path.join(args.dir, base_name)
        extract_zip(
            f,
            out_dir,
            password=args.password,
            quiet=args.quiet,
            clear_source=args.clear,
            force=args.force,
        )
    else:
      extract_zip(
          args.extract,
          args.dir,
          password=args.password,
          member_patterns=args.member,
          quiet=args.quiet,
          clear_source=args.clear,
          force=args.force,
      )
  elif args.repack:
    output_file = args.output if args.output else args.repack + ".zip"
    repack_zip(
        args.repack,
        output_file,
        exclude_patterns=args.exclude,
        no_compress_patterns=args.no_compress,
        compression_level=args.compression_level,
        update_only=args.update,
        forced_timestamp=args.timestamp,
        quiet=args.quiet,
        clear_source=args.clear,
        force=args.force,
    )
  else:
    parser.print_help()


if __name__ == "__main__":
  main()
