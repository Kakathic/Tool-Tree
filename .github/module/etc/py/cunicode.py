#!/data/data/com.tool.tree/files/home/termux/bin/python

import sys

def encode_selected_chars(text, selected_chars=None):
    if selected_chars is None:
        # Mặc định: mã hóa tất cả ký tự
        return ''.join(f'\\u{ord(c):04x}' for c in text)
    else:
        return ''.join(f'\\u{ord(c):04x}' if c in selected_chars else c for c in text)

def decode_unicode(text):
    return text.encode().decode('unicode_escape')

def main():
    args = sys.argv[1:]
    if not args:
        print("Usage:")
        print("  cunicode.py <text>               # Encode all characters")
        print("  cunicode.py -e <\\uXXXX>         # Decode")
        print("  cunicode.py -c <chars> <text>   # Encode only specified characters")
        return

    if args[0] == '-e' and len(args) >= 2:
        print(decode_unicode(args[1]))
    elif args[0] == '-c' and len(args) >= 3:
        selected = args[1]
        text = ' '.join(args[2:])
        print(encode_selected_chars(text, selected))
    else:
        print(encode_selected_chars(' '.join(args)))

if __name__ == "__main__":
    main()
