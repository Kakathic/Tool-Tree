#!/data/data/com.tool.tree/files/home/termux/bin/python

import re
import sys
import argparse
import xml.etree.ElementTree as ET
from io import StringIO
from collections import defaultdict
import shutil
import os

def parse_existing_ids_by_type(public_root):
    ids_by_type = defaultdict(list)
    for pub in public_root.findall('public'):
        typ = pub.get('type')
        val = int(pub.get('id'), 16)
        ids_by_type[typ].append(val)
    return ids_by_type

def get_next_id(ids_by_type, typ):
    if typ in ids_by_type and ids_by_type[typ]:
        return max(ids_by_type[typ]) + 1
    else:
        return None

def detect_type(tag):
    if tag.endswith('-array'):
        return 'array'
    return tag

def parse_existing_name_type_pairs(public_root):
    return set((pub.get('name'), pub.get('type')) for pub in public_root.findall('public'))

def add_missing_entries(public_root, resource_root, verbose=True):
    existing_name_type = parse_existing_name_type_pairs(public_root)
    ids_by_type = parse_existing_ids_by_type(public_root)
    added = 0
    skipped_type = set()

    for elem in resource_root:
        if 'name' not in elem.attrib:
            continue
        name = elem.attrib['name']
        typ = detect_type(elem.tag)
        if (name, typ) in existing_name_type:
            #if verbose:
            #    print(f"Skip existed: name={name}, type={typ}")
            continue
        if 'id' in elem.attrib:
            id_str = elem.attrib['id']
            if re.fullmatch(r"0x[0-9a-fA-F]{8}", id_str):
                next_id = int(id_str, 16)
            else:
                if verbose:
                    print(f"! Ignore: {name} has invalid id: {id_str}")
                continue
        else:
            next_id = get_next_id(ids_by_type, typ)
            if next_id is None:
                skipped_type.add(typ)
                continue
            id_str = f"0x{next_id:08x}"
    
        new_elem = ET.SubElement(public_root, 'public', {
            'id': id_str,
            'type': typ,
            'name': name
        })
        new_elem.tail = "\n  "
        ids_by_type[typ].append(next_id)
        if verbose:
            print(f"Added: {name} type={typ} id={id_str}")
        added += 1

    if verbose and skipped_type:
        print(f"! Ignore types without ID in public.xml: {', '.join(sorted(skipped_type))}")
    return added

def main(public_path, resource_input, use_file):
    # Check file public.xml tồn tại
    if not os.path.isfile(public_path):
        print(f"Error: public.xml file not found: {public_path}")
        sys.exit(1)

    # Nếu resource_input là file thì cũng check tồn tại
    if use_file and not os.path.isfile(resource_input):
        print(f"Error: resource input file not found: {resource_input}")
        sys.exit(1)

    if use_file:
        resource_tree = ET.parse(resource_input)
        resource_root = resource_tree.getroot()
    else:
        wrapped_text = f"<resources>{resource_input}</resources>"
        try:
            resource_tree = ET.parse(StringIO(wrapped_text))
            resource_root = resource_tree.getroot()
        except ET.ParseError as e:
            print(f"Error parsing input XML: {e}")
            sys.exit(1)

    public_tree = ET.parse(public_path)
    public_root = public_tree.getroot()

    print(f"Parsed {len(resource_root)} elements from snippet.")
    added = add_missing_entries(public_root, resource_root, verbose=True)

    public_tree.write(public_path, encoding="utf-8", xml_declaration=True)
    print(f"Added {added} new item(s) to {public_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Add <public> to public.xml from XML resource")
    parser.add_argument("public_path", help="Path to public.xml file")
    parser.add_argument("resource_input", help="XML snippet or path to file (if --use-file)")
    parser.add_argument("--use-file", action="store_true", help="Treat resource_input as file path")

    args = parser.parse_args()
    main(args.public_path, args.resource_input, args.use_file)
    