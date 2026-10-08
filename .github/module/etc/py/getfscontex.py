#!/data/data/com.tool.tree/files/home/termux/bin/python

"""
getfscontex_fixed.py

Generate fs_config and file_contexts from a dumped Android filesystem tree.
This version reads SELinux extended attribute directly via libc's getxattr/lgetxattr
(using ctypes) so it does NOT call `ls` and will not follow symlinks when
collecting the context for a symlink.

Usage:
    python getfscontex_fixed.py <dumped_folder> <output_folder>

Notes:
 - If libc does not export getxattr/lgetxattr on your device, the script will
   fall back to calling `ls -Zd` for context retrieval (still safe but slower).
 - The script writes two files in the output folder:
     <name>_fs_config
     <name>_file_contexts
 - file_contexts entries are escaped for regex characters and deduplicated.

"""

import os
import sys
import stat
import re
import ctypes
import subprocess
from ctypes import c_char_p, c_size_t, c_ssize_t, create_string_buffer

# Load libc with errno support
try:
    libc = ctypes.CDLL("libc.so", use_errno=True)
except Exception:
    libc = None

# Try to get getxattr/lgetxattr symbols if present
_have_getxattr = False
_have_lgetxattr = False
if libc is not None:
    try:
        _getxattr = libc.getxattr
        _getxattr.argtypes = [c_char_p, c_char_p, c_char_p, c_size_t]
        _getxattr.restype = c_ssize_t
        _have_getxattr = True
    except AttributeError:
        _have_getxattr = False

    try:
        _lgetxattr = libc.lgetxattr
        _lgetxattr.argtypes = [c_char_p, c_char_p, c_char_p, c_size_t]
        _lgetxattr.restype = c_ssize_t
        _have_lgetxattr = True
    except AttributeError:
        _have_lgetxattr = False

# Fallback to ls -Zd if ctypes syscall unavailable
def _get_selinux_context_ls(path, follow=True):
    # follow=True -> we want the target's context (ls -Zd follows when showing -Z)
    # follow=False -> we will attempt to use ls -Zd on the symlink name itself but toybox may still dereference
    try:
        out = subprocess.check_output(["ls", "-Zd", path], stderr=subprocess.DEVNULL).decode().strip()
        return out.split()[0]
    except Exception:
        return None

# Generic helper: try to read xattr using provided callable (getxattr or lgetxattr)
def _read_xattr_with_ctypes(func, path, name=b"security.selinux"):
    # First try to call with size 0 to obtain required length (many implementations
    # return the size when value is NULL)
    p_path = path.encode()
    p_name = name
    try:
        # Try size=0 to get required length
        ret = func(p_path, p_name, None, 0)
    except Exception:
        # Some platforms raise if buffer is NULL, fall through to a safe attempt
        ret = -1

    if ret == -1:
        err = ctypes.get_errno()
        # If errno == ERANGE, we'll allocate; otherwise if ENOATTR or ENODATA, no attribute
        ERANGE = 34  # might differ, but 34 is common for ERANGE
        ENOATTR = 61  # macOS; on Linux ENODATA (61/ENODATA) sometimes used; but we'll use numeric check
        ENODATA = 61
        # If ret == -1 but errno indicates the size needed is in errno, fall back to small buffer
        # For compatibility, just try with a reasonable buffer and grow as needed
        buf_size = 256
    else:
        buf_size = int(ret) + 1 if ret > 0 else 256

    for attempt in range(4):
        buf = create_string_buffer(buf_size)
        try:
            ret2 = func(p_path, p_name, buf, buf_size)
        except Exception:
            ret2 = -1

        if ret2 == -1:
            err = ctypes.get_errno()
            # If buffer too small, increase
            # EINVAL or ERANGE might indicate buffer too small on some platforms
            if err == 34 or err == 75:  # ERANGE or EOVERFLOW
                buf_size *= 2
                continue
            # If no attribute or other error return None
            return None
        # success
        return buf.value.decode(errors="ignore")
    return None

# Public function: get SELinux context, choosing lgetxattr for symlink (do not follow)
def get_selinux_context(path, follow_symlink=False):
    """Return SELinux context string or None.
    If follow_symlink is False and path is a symlink, we try lgetxattr to get the
    context of the symlink itself (not its target).
    If ctypes xattr calls are not available, fall back to ls -Zd.
    """
    is_link = os.path.islink(path)

    # Prefer ctypes syscalls when available
    if is_link and _have_lgetxattr:
        ctx = _read_xattr_with_ctypes(_lgetxattr, path)
        if ctx:
            return ctx
        # fallthrough to other methods
    if (not is_link) and _have_getxattr:
        ctx = _read_xattr_with_ctypes(_getxattr, path)
        if ctx:
            return ctx
    # If we reach here, either ctypes wasn't available or it failed. Try ls fallback.
    # Note: ls may follow symlinks when reporting contexts on Android's toybox; this is
    # unavoidable on some devices. We still attempt to call ls on the path as-is.
    return _get_selinux_context_ls(path, follow=follow_symlink)

# Escape regex special characters for file_contexts
_regex_escape = lambda s: re.sub(r'([.+*?^$(){}|\[\]\\])', r'\\\1', s)


def generate_fs_config_and_contexts(input_dir, output_dir):
    name = os.path.basename(os.path.normpath(input_dir))
    fs_config_path = os.path.join(output_dir, f"{name}_fs_config")
    file_contexts_path = os.path.join(output_dir, f"{name}_file_contexts")

    fs_config_entries = []
    file_context_entries = []

    try:
        st_root = os.lstat(input_dir)
        mode_root = f"{stat.S_IMODE(st_root.st_mode):04o}"
        uid_root = st_root.st_uid
        gid_root = st_root.st_gid

        fs_config_entries.append(f"/ {uid_root} {gid_root} {mode_root}")

        # Try to get context for root dir (do not follow symlink of input_dir itself)
        context_root = get_selinux_context(input_dir, follow_symlink=False) or "u:object_r:rootfs:s0"
        file_context_entries.append(f"/ {context_root}")

        fs_config_entries.append(f"{name}/ {uid_root} {gid_root} {mode_root}")
        file_context_entries.append(f"/{name} {context_root}")
        file_context_entries.append(f"/{name}/ {context_root}")

    except FileNotFoundError:
        print("Cannot read root directory")
        return

    # Walk the tree
    for root, dirs, files in os.walk(input_dir, followlinks=False):
        # We iterate both dirs and files; keep relative path normalized with forward slashes
        for item in dirs + files:
            path = os.path.join(root, item)
            rel_path = os.path.relpath(path, input_dir)
            if rel_path == ".":
                continue

            try:
                st = os.lstat(path)
                mode = f"{stat.S_IMODE(st.st_mode):04o}"
                uid = st.st_uid
                gid = st.st_gid

                # fs_config entry uses name/relpath
                if os.path.islink(path):
                    link_target = os.readlink(path)
                    fs_config_entries.append(
                        f"{name}/{rel_path} {uid} {gid} {mode} {link_target}"
                    )
                else:
                    fs_config_entries.append(
                        f"{name}/{rel_path} {uid} {gid} {mode}"
                    )

                # If this is a symlink, retrieve symlink's own xattr (do not follow)
                follow = False
                if os.path.islink(path):
                    context = get_selinux_context(path, follow_symlink=False)
                else:
                    context = get_selinux_context(path, follow_symlink=True)

                if context is None:
                    context = "u:object_r:rootfs:s0"

                # Escape regex special characters in rel_path and convert to posix style
                escaped_path = _regex_escape(rel_path.replace(os.sep, '/'))
                file_context_entries.append(f"/{name}/{escaped_path} {context}")

            except FileNotFoundError:
                # broken symlink target or race condition; still attempt to handle symlink itself
                if os.path.islink(path):
                    # Attempt to get symlink's own context
                    context = get_selinux_context(path, follow_symlink=False) or "u:object_r:rootfs:s0"
                    escaped_path = _regex_escape(rel_path.replace(os.sep, '/'))
                    file_context_entries.append(f"/{name}/{escaped_path} {context}")
                continue

    # Deduplicate and sort entries (stable)
    fs_config_entries_unique = []
    seen = set()
    for e in fs_config_entries:
        if e not in seen:
            fs_config_entries_unique.append(e)
            seen.add(e)

    file_context_entries_unique = []
    seen = set()
    for e in file_context_entries:
        if e not in seen:
            file_context_entries_unique.append(e)
            seen.add(e)

    # Optionally sort file_contexts to make diffs stable: sort by path length then lexicographically
    file_context_entries_unique.sort(key=lambda s: (len(s), s))

    # Write outputs
    with open(fs_config_path, "w", encoding="utf-8") as f:
        f.write("\n".join(fs_config_entries_unique) + "\n")

    with open(file_contexts_path, "w", encoding="utf-8") as f:
        f.write("\n".join(file_context_entries_unique) + "\n")

    print(f"Created {fs_config_path}")
    print(f"Created {file_contexts_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Use: python getfscontex.py <dumped_folder> <output_folder>")
        sys.exit(1)

    input_dir = sys.argv[1]
    output_dir = sys.argv[2]

    if not os.path.isdir(input_dir):
        print("The input directory does not exist.")
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)
    generate_fs_config_and_contexts(input_dir, output_dir)
