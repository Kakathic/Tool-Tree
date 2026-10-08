#!/data/data/com.tool.tree/files/home/termux/bin/python

import os
import sys
import json
import argparse
import hashlib
from concurrent.futures import ThreadPoolExecutor

def compute_hash(file_path, buffer_size=1048576):
    """
    Tính SHA-256 của file.
    Trả về tuple (file_path, hexhash) hoặc (file_path, None) nếu có lỗi.
    """
    sha256 = hashlib.sha256()
    try:
        with open(file_path, "rb") as f:
            while True:
                chunk = f.read(buffer_size)
                if not chunk:
                    break
                sha256.update(chunk)
        return file_path, sha256.hexdigest()
    except Exception:
        return file_path, None

def load_hash_db(hash_db_path):
    """
    Đọc database JSON (nếu có) trả về dict {rel_path: {"hash":..., "size":..., "mtime":...}}.
    """
    if os.path.exists(hash_db_path):
        try:
            with open(hash_db_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_hash_db(hash_db_path, hash_db):
    """
    Lưu database JSON một cách nguyên tử: ghi tạm, rồi replace.
    """
    temp_file = hash_db_path + ".tmp"
    try:
        with open(temp_file, "w", encoding="utf-8") as f:
            json.dump(hash_db, f, ensure_ascii=False, indent=2)
        os.replace(temp_file, hash_db_path)
    except Exception as e:
        print(f"Unable to save database hash: {e}", file=sys.stderr)

def iter_files(root_dir, excluded_exts):
    """
    Yield đường dẫn file trong thư mục và các thư mục con (có đệ quy).
    Loại trừ các file có đuôi trong excluded_exts.
    """
    for dirpath, _, filenames in os.walk(root_dir):
        for name in filenames:
            if any(name.lower().endswith(ext) for ext in excluded_exts):
                continue
            yield os.path.join(dirpath, name)

def scan_directory(root_dir, excluded_exts, previous_db, force=False, buffer_size=1048576, max_workers=None):
    """
    Quét thư mục, tính hash cho file mới/đã thay đổi, kết hợp với database trước đó.
    Trả về dict mới {rel_path: {"hash":..., "size":..., "mtime":...}}.
    """
    current_db = {}
    def gen_tasks():
        for file_path in iter_files(root_dir, excluded_exts):
            rel = os.path.relpath(file_path, root_dir)
            try:
                st = os.stat(file_path)
            except Exception:
                continue
            size = st.st_size
            mtime = st.st_mtime_ns
            prev = previous_db.get(rel)
            if not force and prev and prev.get("size") == size and prev.get("mtime") == mtime:
                current_db[rel] = {"hash": prev.get("hash"), "size": size, "mtime": mtime}
            else:
                yield file_path

    if max_workers is None:
        cpu = os.cpu_count() or 1
        max_workers = min(32, cpu * 2)
    try:
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            for file_path, file_hash in executor.map(lambda p: compute_hash(p, buffer_size), gen_tasks(), chunksize=64):
                rel = os.path.relpath(file_path, root_dir)
                try:
                    st = os.stat(file_path)
                    size = st.st_size
                    mtime = st.st_mtime_ns
                except Exception:
                    size = None
                    mtime = None
                current_db[rel] = {"hash": file_hash, "size": size, "mtime": mtime}
    except KeyboardInterrupt:
        print("Program paused (interrupted by user)...", file=sys.stderr)
        return current_db

    return current_db

def check_changes(root_dir, hash_db_path, exclude_list, force=False, buffer_size=1048576, max_workers=None, dry_run=False):
    """
    So sánh thay đổi giữa hash cũ và hash mới, in ra kết quả.
    Cập nhật và lưu database (nếu dry_run là False).
    """
    previous_db = load_hash_db(hash_db_path)
    current_db = scan_directory(root_dir, exclude_list, previous_db, force, buffer_size, max_workers)

    for rel, info in current_db.items():
        if rel not in previous_db:
            print(f"new: {rel}")
        elif previous_db.get(rel, {}).get("hash") != info.get("hash"):
            print(f"fixed: {rel}")
    for rel in previous_db:
        if rel not in current_db:
            print(f"deleted: {rel}")

    if not dry_run:
        save_hash_db(hash_db_path, current_db)

def main():
    parser = argparse.ArgumentParser(description="Scan the directory, calculate the SHA-256 value, and check for changes.")
    parser.add_argument("directory", help="Folder to scan")
    parser.add_argument("hash_db", help="The path to the JSON file storing the cache hash.")
    parser.add_argument("--exclude", default=".bak,.tmp,.log",
                        help="Excluded file extensions (separated by commas). Default: .bak, .tmp, .log")
    parser.add_argument("--force", action="store_true",
                        help="Recalculate the hash for all files, without using the cache.")
    parser.add_argument("--workers", type=int,
                        help="Maximum number of threads (default = min(32, CPU*2))")
    parser.add_argument("--dry-run", action="store_true",
                        help="Chỉ in ra log thay đổi mà không lưu lại database hash.")
    args = parser.parse_args()

    directory = os.path.abspath(args.directory)
    exclude_exts = [ext if ext.startswith('.') else '.'+ext for ext in (args.exclude or "").split(',') if ext.strip()]
    if not exclude_exts:
        exclude_exts = [".bak", ".tmp", ".log"]

    if not os.path.isdir(directory):
        print(f"Paths that are not folders: {directory}", file=sys.stderr)
        sys.exit(1)

    check_changes(directory, args.hash_db, exclude_exts, force=args.force,
                  buffer_size=1048576, max_workers=args.workers, dry_run=args.dry_run)

if __name__ == "__main__":
    main()
