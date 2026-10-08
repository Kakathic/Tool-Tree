#!/data/data/com.tool.tree/files/home/termux/bin/python
# -*- coding: utf-8 -*-

import os
import sys
from typing import List, Optional, Tuple

FsEntry = Tuple[str, List[str]]


def scanfs(fs_path: str) -> List[FsEntry]:
    entries: List[FsEntry] = []
    with open(fs_path, "r", encoding="utf-8", errors="ignore") as fp:
        for line_num, raw_line in enumerate(fp, 1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue

            parts = line.split()
            if not parts:
                continue

            filepath, *other = parts
            entries.append((filepath, other))

            if len(other) > 4:
                print(
                    f"[Warn] Line {line_num}: {filepath} has too many fields ({len(other)})."
                )

    return entries


def scan_dir(folder: str):
    folder = os.path.abspath(folder)
    base = os.path.basename(folder)
    yield base
    yield "/"
    yield f"{base}/lost+found"

    for root, dirs, files in os.walk(folder):
        rel_root = os.path.relpath(root, folder)
        rel_root = "" if rel_root == "." else rel_root

        for d in dirs:
            path = os.path.join(base, rel_root, d)
            yield path.replace("\\", "/")

        for f in files:
            path = os.path.join(base, rel_root, f)
            yield path.replace("\\", "/")


def islink(file_path: str) -> Optional[str]:
    if os.path.islink(file_path):
        try:
            return os.readlink(file_path)
        except OSError:
            return None
    return None


def make_config(i: str, filepath: str) -> List[str]:
    path_norm = i.replace("\\", "/")
    
    # Kiểm tra xem đường dẫn có nằm trong bin/xbin/vendor/bin ở bất kỳ cấp độ nào không
    is_bin_path = any(b in path_norm for b in ("/bin/", "/xbin/", "/vendor/bin/"))
    
    # 1. Thư mục
    if os.path.isdir(filepath):
        uid = "0"
        gid = "2000" if is_bin_path else "0"
        mode = "0755"
        return [uid, gid, mode]

    # 2. File không tồn tại thực tế
    if not os.path.exists(filepath):
        return ["0", "0", "0755"]

    # 3. Liên kết mềm (Symlink)
    link = islink(filepath)
    if link:
        uid = "0"
        gid = "2000" if is_bin_path else "0"

        if is_bin_path or path_norm.endswith(".so"):
            mode = "0755"
        elif path_norm.endswith(".sh"):
            mode = "0750"
        else:
            mode = "0644"

        return [uid, gid, mode, link]

    # 4. File thực thi trong bin/xbin hoặc thư viện native (.so / lib)
    if is_bin_path or path_norm.endswith(".so") or "/lib/" in path_norm:
        uid = "0"
        gid = "2000" if is_bin_path else "0"
        mode = "0750" if path_norm.endswith(".sh") else "0755"
        return [uid, gid, mode]

    # 5. Các tệp odex, vdex, art (chuẩn quyền 0644)
    if path_norm.lower().endswith((".odex", ".vdex", ".art")):
        return ["0", "0", "0644"]

    # 6. File thông thường khác
    return ["0", "0", "0644"]


def find_insert_index(entries: List[FsEntry], new_path: str, filepath: str, target_dir: str) -> int:
    norm = new_path.replace("\\", "/").strip("/")
    parts = [p for p in norm.split("/") if p]
    if not parts:
        return len(entries)

    is_dir = os.path.isdir(filepath)
    is_app_path = any(kw in norm for kw in ["/app/", "/priv-app/", "app/", "priv-app/"])

    # Hàm phụ kiểm tra đường dẫn entry có phải là file trên ổ đĩa không
    def entry_is_file(entry_path: str) -> bool:
        base = os.path.basename(target_dir)
        rel = entry_path[len(base) + 1:] if entry_path.startswith(base + "/") else entry_path
        disk_p = os.path.join(target_dir, rel)
        return os.path.isfile(disk_p)

    # Hàm phụ kiểm tra file .apk
    def entry_is_apk(entry_path: str) -> bool:
        return entry_path.lower().endswith(".apk")

    # 1. Nếu nằm trong thư mục app/priv-app -> kế thừa từ .apk gần nhất của app đó
    if is_app_path:
        app_prefix = ""
        for idx, level in enumerate(parts):
            if level in ("app", "priv-app"):
                if idx + 1 < len(parts):
                    app_prefix = "/".join(parts[:idx + 2])
                break
        
        last_apk_idx = None
        for idx, (path, _) in enumerate(entries):
            p = path.replace("\\", "/").strip("/")
            if entry_is_apk(path):
                if app_prefix and (p == app_prefix or p.startswith(app_prefix + "/")):
                    last_apk_idx = idx
                elif not app_prefix:
                    last_apk_idx = idx

        if last_apk_idx is not None:
            return last_apk_idx + 1

    # 2. Nếu là file -> kế thừa từ file gần nhất CHÍNH XÁC trong cùng thư mục cha
    if not is_dir:
        parent_dir = "/".join(parts[:-1]) if len(parts) > 1 else ""

        last_file_idx = None
        for idx, (path, _) in enumerate(entries):
            p = path.replace("\\", "/").strip("/")
            # Đảm bảo file nằm đúng cấp thư mục cha, không bị quét nhầm vào thư mục con bên trong
            if os.path.dirname(p) == parent_dir:
                if entry_is_file(path):
                    last_file_idx = idx

        if last_file_idx is not None:
            return last_file_idx + 1

    # 3. Mặc định / Fallback: kế thừa từ thư mục mẹ
    if len(parts) <= 1:
        return len(entries)

    for end in range(len(parts) - 1, 0, -1):
        prefix = "/".join(parts[:end])
        prefix_slash = prefix + "/"

        last_idx = None
        for idx, (path, _) in enumerate(entries):
            p = path.replace("\\", "/").strip("/")
            if p == prefix or p.startswith(prefix_slash):
                last_idx = idx

        if last_idx is not None:
            return last_idx + 1

    return len(entries)


def fs_patch(fs_entries: List[FsEntry], dir_path: str) -> Tuple[List[FsEntry], int]:
    entries = list(fs_entries)
    existing = {k for k, _ in entries}
    seen_new = set()
    new_add = 0
    print("FsPatcher: Load origin %d entries" % len(entries))

    target_dir = os.path.abspath(dir_path)
    base = os.path.basename(target_dir)

    for i in scan_dir(target_dir):
        if not i.isprintable():
            i = "".join(c if c.isprintable() else "*" for c in i)

        if i == base and i not in existing:
            continue

        if i in existing or i in seen_new:
            continue

        rel_path = i[len(base) + 1:] if i.startswith(base + "/") else i
        filepath = os.path.abspath(os.path.join(target_dir, rel_path))

        config = make_config(i, filepath)

        insert_at = find_insert_index(entries, i, filepath, target_dir)
        entries.insert(insert_at, (i, config))
        seen_new.add(i)
        new_add += 1

        print(f"Add [{i} {' '.join(config)}] at {insert_at}")

    return entries, new_add


def main(dir_path: str, fs_config: str) -> None:
    fs_entries = scanfs(os.path.abspath(fs_config))
    new_entries, new_add = fs_patch(fs_entries, dir_path)
    with open(fs_config, "w", encoding="utf-8", newline="\n") as f:
        for key, value in new_entries:
            f.write(key + " " + " ".join(value) + "\n")

    print(f"FsPatcher: Add {new_add} entries")


def usage() -> None:
    print(
        """
FsPatcher: FsConfig Patching Tool
Usage: ./FsPatcher [Folders] [FsConfig]
"""
    )


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("FsPatcher: Insufficient parameters")
        usage()
    elif os.path.isfile(os.path.abspath(sys.argv[2])) and os.path.isdir(
        os.path.abspath(sys.argv[1])
    ):
        main(sys.argv[1], sys.argv[2])
    else:
        usage()
