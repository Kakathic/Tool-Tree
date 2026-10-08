#!/data/data/com.tool.tree/files/home/termux/bin/python

import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

formats = (
    (b'PK', "zip"),
    (b'\x10\x20\xF5\xF2', 'f2fs', 1024),
    (b'\x53\xef', 'ext', 1080),
    (b'\x3a\xff\x26\xed', "sparse"),
    (b'\x67\x44\x6c\x61', 'super', 4096),
    (b"AVB0", "vbmeta"),
    (b'\xe2\xe1\xf5\xe0', "erofs", 1024),
    (b"CrAU", "payload"),
    (b'\xd7\xb7\xab\x1e', "dtbo"),
    (b'\xd0\x0d\xfe\xed', "dtb"),
    (b"MZ", "exe"),
    (b".ELF", 'elf'),
    (b"ANDROID!", "boot"),
    (b"VNDRBOOT", "vendor_boot"),
    (b'\x28\xb5\x2f\xfd', 'zstd'),
    (b"sqsh", "squashfs"),
    (b'hsqs', 'squashfs'),
    (b"NTPI", 'NTPI'),
    (b'\xfa\xff\xfa\xff', 'pac', 2116),
    (b'RKFW', 'rkfw'),
    (b'RKAF', 'rkaf'),
    (b'\x56\x19\xb5\x27', 'amlogic', 8),
    (b"-rom1fs-", 'romfs'),
    (b'(\x05\x00\x00$8"%', 'kdz'),
    (b"\x32\x96\x18\x74", 'dz'),
    (b'OPPOENCRYPT!', "ozip"),
    (b'7z', "7z"),
    (b'\x1f\x8b', "gzip"),
    (b'AVBf', "avb_foot"),
    (b'BZh', "bzip2"),
    (b'\x89PNG', 'png'),
    (b'CHROMEOS', 'chrome'),
    (b"LOGO!!!!", 'logo', 16384),
    (b'\x1f\x9e', "gzip"),
    (b'\x02\x21\x4c\x18', "lz4_legacy"),
    (b'\x03\x21\x4c\x18', 'lz4'),
    (b'\x04\x22\x4d\x18', 'lz4'),
    (b'\x1f\x8b\x08\x00\x00\x00\x00\x00\x02\x03', "zopfli"),
    (b'\xfd7zXZ', 'xz'),
    (b'\x7fELF', 'elf'),
    (b'\x5d\x00', 'lzma'),
    (b']\x00\x00\x00\x04\xff\xff\xff\xff\xff\xff\xff\xff', 'lzma'),
    (b'\x02!L\x18', 'lz4_lg'),
    (b'UBI#', "ubi"),
    (b"\x85\x19", "jffs2"),
)

def get_max_offset():
    return max((entry[2] if len(entry) == 3 else 0) + len(entry[0]) for entry in formats)

MAX_OFFSET = get_max_offset()

HOME = os.environ.get("HOME", os.path.expanduser("~"))
CACHE_DIR = os.path.join(HOME, "root")
CACHE_FILE = os.path.join(CACHE_DIR, "file_type.log")


def load_cache():
    """
    Cache format:
        root_dir|relative_path|type|mtime_ns
    """
    cache = {}
    if not os.path.exists(CACHE_FILE):
        return cache

    try:
        with open(CACHE_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line:
                    continue

                parts = line.split("|", 3)
                if len(parts) != 4:
                    continue

                root, relpath, typ, mtime_ns = parts
                try:
                    mtime_ns = int(mtime_ns)
                except:
                    continue

                cache.setdefault(root, {})[relpath] = (typ, mtime_ns)
    except:
        pass

    return cache


def save_cache(cache):
    os.makedirs(CACHE_DIR, exist_ok=True)
    tmp_file = CACHE_FILE + ".tmp"

    with open(tmp_file, "w", encoding="utf-8") as f:
        for root in sorted(cache.keys()):
            entries = cache[root]
            for relpath in sorted(entries.keys()):
                typ, mtime_ns = entries[relpath]
                f.write(f"{root}|{relpath}|{typ}|{mtime_ns}\n")

    os.replace(tmp_file, CACHE_FILE)


def cleanup_cache(cache, root, valid_relpaths):
    """
    Chỉ dọn cache của đúng thư mục gốc đang quét.
    Không đụng vào cache của thư mục khác.
    """
    if root not in cache:
        return

    entries = cache[root]
    for relpath in list(entries.keys()):
        if relpath not in valid_relpaths:
            entries.pop(relpath, None)

    if not entries:
        cache.pop(root, None)


def gettype(args):
    file, root, root_cache = args
    relpath = os.path.relpath(file, root)

    if not os.path.exists(file):
        return file, relpath, "fne", 0

    try:
        st = os.stat(file)
        mtime_ns = st.st_mtime_ns
    except:
        return file, relpath, "err", 0

    old = root_cache.get(relpath)
    if old and old[1] == mtime_ns:
        return file, relpath, old[0], mtime_ns

    if os.path.getsize(file) == 0:
        return file, relpath, "empty", mtime_ns

    ext = os.path.splitext(file)[1].lower().lstrip(".")
    if ext in ("dat", "br"):
        return file, relpath, ext, mtime_ns

    try:
        with open(file, "rb") as f:
            data = f.read(MAX_OFFSET)
    except:
        return file, relpath, "err", mtime_ns

    for item in formats:
        sig = item[0]
        if len(item) == 2:
            if data.startswith(sig):
                return file, relpath, item[1], mtime_ns
        else:
            offset = item[2]
            if len(data) >= offset + len(sig) and data[offset:offset + len(sig)] == sig:
                return file, relpath, item[1], mtime_ns

    return file, relpath, "unknown", mtime_ns


def collect_files(path):
    if os.path.isfile(path):
        return [os.path.abspath(path)]

    files = []
    for n in os.listdir(path):
        full = os.path.abspath(os.path.join(path, n))
        if os.path.isfile(full):
            files.append(full)
    return files


def main(path):
    path = os.path.abspath(path)
    cache = load_cache()

    if os.path.isfile(path):
        root = os.path.dirname(path)
        root = os.path.abspath(root)
        files = [path]
    else:
        root = path
        files = collect_files(path)

    root_cache = cache.get(root, {})

    if os.path.isfile(path):
        file, relpath, typ, mtime_ns = gettype((path, root, root_cache))
        cache.setdefault(root, {})[relpath] = (typ, mtime_ns)
        save_cache(cache)
        print(typ)
        return

    valid_relpaths = set()
    cpu = min((os.cpu_count() or 1), 4)
    args = [(f, root, root_cache) for f in files]

    with ThreadPoolExecutor(max_workers=cpu) as executor:
        futures = [executor.submit(gettype, arg) for arg in args]
        for future in as_completed(futures):
            file, relpath, typ, mtime_ns = future.result()
            valid_relpaths.add(relpath)
            cache.setdefault(root, {})[relpath] = (typ, mtime_ns)
            print(f"{os.path.basename(file)}:{typ}")

    cleanup_cache(cache, root, valid_relpaths)
    save_cache(cache)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: gettype.py <file_or_directory>")
        sys.exit(1)
    main(sys.argv[1])