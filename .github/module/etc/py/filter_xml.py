#!/data/data/com.tool.tree/files/home/termux/bin/python
import xml.etree.ElementTree as ET
import sys

def clean_values_xml(xml_path, output_path=None):
    tree = ET.parse(xml_path)
    root = tree.getroot()

    seen = set()
    removed = []

    for element in list(root):
        name = element.attrib.get("name")

        if name:
            key = (element.tag, name)

            if key in seen:
                root.remove(element)
                removed.append(f"{element.tag}/{name}")
            else:
                seen.add(key)

    if output_path is None:
        output_path = xml_path

    tree.write(output_path, encoding="utf-8", xml_declaration=True)

    print(f"Deleted {len(removed)} resource coincidence:")
    for r in removed:
        print(" -", r)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python filter_xml.py file.xml")
        sys.exit(1)

    clean_values_xml(sys.argv[1])