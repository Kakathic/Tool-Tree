#!/data/data/com.tool.tree/files/home/termux/bin/python

import sys
import os
import re
import xml.etree.ElementTree as ET

# Regex loại bỏ control chars không hợp lệ trong XML 1.0
_INVALID_XML_CHARS_RE = re.compile(r"[\x00-\x08\x0B\x0C\x0E-\x1F]")

# Regex bắt entity dạng &#123; hoặc &#x1F;
_INVALID_ENTITY_RE = re.compile(r"&#(x[0-9A-Fa-f]+|\d+);")

def _remove_invalid_entities(s):
    """
    Xóa các entity số không hợp lệ (theo XML 1.0).
    """
    def repl(match):
        val = match.group(1)
        try:
            num = int(val, 16) if val.lower().startswith("x") else int(val)
        except ValueError:
            return ""  # Nếu parse số lỗi thì bỏ
        # XML 1.0 hợp lệ: tab (0x9), newline (0xA), carriage return (0xD), hoặc >= 0x20
        if num == 0x9 or num == 0xA or num == 0xD or num >= 0x20:
            return match.group(0)  # Giữ nguyên entity hợp lệ
        return ""  # Invalid -> bỏ hẳn
    return _INVALID_ENTITY_RE.sub(repl, s)

def _clean_xml_string(s):
    """Lọc control chars và entity không hợp lệ."""
    s = _INVALID_XML_CHARS_RE.sub("", s)
    s = _remove_invalid_entities(s)
    return s

def _safe_parse(file_path):
    """
    Đọc file dưới dạng text (utf-8, ignore errors), lọc ký tự/ entity invalid,
    rồi parse bằng ET.fromstring -> trả về ElementTree.
    """
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
    content = _clean_xml_string(content)
    root = ET.fromstring(content)
    return ET.ElementTree(root)

def get_names_from_file(file_path):
    if not os.path.isfile(file_path):
        print(f"The file does not exist: {file_path}")
        return set()
    try:
        tree = _safe_parse(file_path)
    except ET.ParseError as e:
        print(f"Error when parsing {file_path}: {e}")
        return set()

    root = tree.getroot()
    names = set()
    for elem in root:
        name = elem.attrib.get("name")
        if name:
            names.add(name)
    return names

def filter_file2(file1, file2):
    if not os.path.isfile(file1):
        print(f"The file does not exist.: {file1}")
        return

    if not os.path.isfile(file2):
        print(f"The file does not exist.: {file2}")
        return

    base_names = get_names_from_file(file1)
    if not base_names:
        print(f"No names found in {file1}, unfiltered.")
        return

    try:
        tree2 = _safe_parse(file2)
    except ET.ParseError as e:
        print(f"Error when parsing {file2}: {e}")
        return

    root2 = tree2.getroot()

    to_remove = []
    for elem in root2:
        name = elem.attrib.get("name")
        if name and name not in base_names:
            to_remove.append(elem)

    for elem in to_remove:
        root2.remove(elem)

    tree2.write(file2, encoding="utf-8", xml_declaration=True)
    print(f"Filtered {file2}: retain {len(root2)} element.")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Use: python text_filter.py file1 file2")
        sys.exit(1)

    file1 = sys.argv[1]
    file2 = sys.argv[2]

    filter_file2(file1, file2)