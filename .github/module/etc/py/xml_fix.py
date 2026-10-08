#!/data/data/com.tool.tree/files/home/termux/bin/python

import xml.etree.ElementTree as ET
import sys
import os

def clean_string_text(text):
    if text:
        # Không strip nữa, giữ nguyên xuống dòng/khoảng trắng
        if text.startswith('"') and text.endswith('"'):
            text = text[1:-1]
        return text.replace(r'\"', '"').replace('&quot;', '"')
    return text

def process_xml_file(xml_path):
    # Check file tồn tại trước khi xử lý
    if not os.path.isfile(xml_path):
        print(f"Error: file not found -> {xml_path}")
        return

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()

        for elem in root.iter():
            if elem.text:
                elem.text = clean_string_text(elem.text)
            if elem.tail:
                elem.tail = clean_string_text(elem.tail)

        tree.write(xml_path, encoding="utf-8", xml_declaration=True)
        print(f"Processed: {xml_path}")

    except Exception as e:
        print(f"File processing error: {e}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Use: python3 xml_fix.py <file.xml>")
    else:
        process_xml_file(sys.argv[1])
        