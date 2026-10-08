#!/data/data/com.tool.tree/files/home/termux/bin/python
# -*- coding: utf-8 -*-

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET

# ---------- WIRE TYPES ----------

WIRE_VARINT = 0
WIRE_64BIT  = 1
WIRE_LEN    = 2
WIRE_32BIT  = 5


# ---------- PURE PYTHON VARINT ----------

def encode_varint(v: int) -> bytes:
    out = bytearray()
    while True:
        bits = v & 0x7F
        v >>= 7
        if v:
            out.append(bits | 0x80)
        else:
            out.append(bits)
            break
    return bytes(out)


def decode_varint(buf: bytes, pos: int):
    res = 0
    shift = 0
    while True:
        if pos >= len(buf):
            raise ValueError("Truncated data while decoding varint")
        b = buf[pos]
        pos += 1
        res |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return res, pos


# ---------- BYTES <-> TEXT (BYTE-EXACT) ----------

def bytes_to_text(raw: bytes) -> str:
    return raw.decode("latin1").encode("unicode_escape").decode("ascii")


def text_to_bytes(text: str) -> bytes:
    return text.encode("ascii").decode("unicode_escape").encode("latin1")


# ---------- TEXT <-> XML VIEW ----------

def text_to_xml(text: str) -> str:
    return text.replace("\\n", "\n")


def xml_to_text(text: str) -> str:
    return text.replace("\n", "\\n")


# ---------- DECODE PB ----------

def decode_message(buf: bytes):
    pos = 0
    out = []

    while pos < len(buf):
        tag, pos = decode_varint(buf, pos)
        field = tag >> 3
        wire  = tag & 0x7

        entry = {"field": field, "wire": wire}

        if wire == WIRE_VARINT:
            val, pos = decode_varint(buf, pos)
            entry["text"] = str(val)

        elif wire == WIRE_64BIT:
            if pos + 8 > len(buf):
                raise ValueError("Truncated 64-bit field data")
            raw = buf[pos:pos+8]
            pos += 8
            entry["text"] = bytes_to_text(raw)

        elif wire == WIRE_32BIT:
            if pos + 4 > len(buf):
                raise ValueError("Truncated 32-bit field data")
            raw = buf[pos:pos+4]
            pos += 4
            entry["text"] = bytes_to_text(raw)

        elif wire == WIRE_LEN:
            size, pos = decode_varint(buf, pos)
            if pos + size > len(buf):
                raise ValueError(f"Truncated length-delimited data (expected {size} bytes)")
            raw = buf[pos:pos+size]
            pos += size
            entry["text"] = bytes_to_text(raw)

        else:
            raise ValueError(f"Invalid wire type: {wire}")

        out.append(entry)

    return out


# ---------- ENCODE PB ----------

def encode_message(entries):
    out = b""

    for e in entries:
        field = int(e["field"])
        wire  = int(e["wire"])
        text  = e["text"]

        tag = (field << 3) | wire
        out += encode_varint(tag)

        if wire == WIRE_VARINT:
            out += encode_varint(int(text))

        elif wire in (WIRE_32BIT, WIRE_64BIT):
            out += text_to_bytes(text)

        elif wire == WIRE_LEN:
            raw = text_to_bytes(text)
            out += encode_varint(len(raw))
            out += raw

        else:
            raise ValueError(f"Invalid wire type: {wire}")

    return out


# ---------- JSON ----------

def json_dump(data, out_path=None):
    content = json.dumps(data, indent=2, ensure_ascii=False)
    if out_path:
        dir_name = os.path.dirname(out_path)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(content)
    else:
        print(content)


def json_load(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# ---------- XML ----------

def xml_dump(entries, out_path=None):
    root = ET.Element("protobuf")

    for e in entries:
        item = ET.SubElement(
            root, "entry",
            field=str(e["field"]),
            wire=str(e["wire"])
        )
        text = ET.SubElement(item, "text")
        text.text = text_to_xml(e["text"])

    if hasattr(ET, "indent"):
        ET.indent(root, space="  ")

    tree = ET.ElementTree(root)
    if out_path:
        dir_name = os.path.dirname(out_path)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
        tree.write(out_path, encoding="utf-8", xml_declaration=False)
    else:
        tree.write(sys.stdout, encoding="unicode", xml_declaration=False)


def xml_load(path):
    tree = ET.parse(path)
    root = tree.getroot()
    out = []

    for item in root.findall("entry"):
        text = item.findtext("text", "")
        out.append({
            "field": int(item.attrib["field"]),
            "wire": int(item.attrib["wire"]),
            "text": xml_to_text(text)
        })

    return out


# ---------- CLI ----------

def main():
    ap = argparse.ArgumentParser(
        description="Protobuf TEXT-only codec (XML / JSON / PB, byte-exact)"
    )
    ap.add_argument("-d", "--decode", help="decode pb -> xml/json")
    ap.add_argument("-e", "--encode", help="encode xml/json -> pb")
    ap.add_argument("--json", action="store_true", help="use JSON instead of XML")
    ap.add_argument("-o", "--out", help="output file path")
    ap.add_argument("-c", "--delete_input", action="store_true", help="delete input file after processing")
    args = ap.parse_args()

    # ---- DECODE ----
    if args.decode:
        in_file = args.decode
        with open(in_file, "rb") as f:
            data = f.read()

        decoded = decode_message(data)
        if args.json:
            json_dump(decoded, args.out)
        else:
            xml_dump(decoded, args.out)

        if args.delete_input:
            try:
                os.remove(in_file)
            except Exception as e:
                print(f"[!] Failed to delete {in_file}: {e}", file=sys.stderr)
        return

    # ---- ENCODE ----
    if args.encode:
        in_file = args.encode
        entries = json_load(in_file) if args.json else xml_load(in_file)
        pb = encode_message(entries)

        if args.out:
            dir_name = os.path.dirname(args.out)
            if dir_name:
                os.makedirs(dir_name, exist_ok=True)
            with open(args.out, "wb") as f:
                f.write(pb)
        else:
            print(pb.hex())

        if args.delete_input:
            try:
                os.remove(in_file)
            except Exception as e:
                print(f"[!] Failed to delete {in_file}: {e}", file=sys.stderr)
        return

    ap.print_help()


if __name__ == "__main__":
    main()
