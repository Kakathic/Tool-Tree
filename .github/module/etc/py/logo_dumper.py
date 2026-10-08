#!/data/data/com.tool.tree/files/home/termux/bin/python
# Python script by affggh (modified)

import os
import struct

class DUMPCFG:
    def __init__(self):
        self.blksz = 0x1 << 0xc
        self.headoff = 0x4000
        self.magic = b"LOGO!!!!"
        self.imgnum = 0
        self.imgblkoffs = []
        self.imgblkszs = []

class BMPHEAD(object):
    def __init__(self, buf: bytes = None):
        if buf is None:
            raise SyntaxError("buf Should be bytes not %s" % type(buf))
        self.structstr = "<H6I"
        (
            self.magic,
            self.fsize,
            self.reserved,
            self.hsize,
            self.dib,
            self.width,
            self.height,
        ) = struct.unpack(self.structstr, buf)

class XIAOMI_BLKSTRUCT(object):
    def __init__(self, buf: bytes):
        self.structstr = "2I"
        (
            self.imgoff,
            self.blksz,
        ) = struct.unpack(self.structstr, buf)

class LOGODUMPER(object):
    def __init__(self, img: str, out: str, bmpdir: str = "pic"):
        self.out = out
        self.bmpdir = bmpdir
        self.img = img
        self.structstr = "<8s"
        self.cfg = DUMPCFG()
        self.chkimg(img)

    def chkimg(self, img: str):
        if not os.access(img, os.F_OK):
            raise FileNotFoundError(f"{img} does not exist!")
        with open(img, 'rb') as f:
            f.seek(self.cfg.headoff, 0)
            self.magic = struct.unpack(
                self.structstr, f.read(struct.calcsize(self.structstr))
            )[0]
            while True:
                m = XIAOMI_BLKSTRUCT(f.read(8))
                if m.imgoff != 0:
                    self.cfg.imgblkszs.append(m.blksz << 0xc)
                    self.cfg.imgblkoffs.append(m.imgoff << 0xc)
                    self.cfg.imgnum += 1
                else:
                    break
        if self.magic != b"LOGO!!!!":
            raise TypeError("File does not match Xiaomi LOGO!!!! format!")
        else:
            print("Xiaomi LOGO!!!! format check pass!")

    def unpack(self):
        with open(self.img, 'rb') as f:
            print("Unpack:\nBMP\tSize\tWidth\tHeight")
            for i in range(self.cfg.imgnum):
                f.seek(self.cfg.imgblkoffs[i], 0)
                bmph = BMPHEAD(f.read(26))
                f.seek(self.cfg.imgblkoffs[i], 0)
                print("%d\t%d\t%d\t%d" % (i, bmph.fsize, bmph.width, bmph.height))
                with open(os.path.join(self.out, f"{i}.bmp"), 'wb') as o:
                    o.write(f.read(bmph.fsize))
            print("\tDone!")

    def repack(self):
        if os.path.isdir(self.out):
            raise ValueError(f"{self.out} is a directory! Must provide a file path for output.")
        with open(self.out, 'wb') as o:
            off = 0x5
            for i in range(self.cfg.imgnum):
                bmp_path = os.path.join(self.bmpdir, f"{i}.bmp")
                if not os.path.exists(bmp_path):
                    raise FileNotFoundError(f"{bmp_path} not found!")
                print("Write BMP [%s] at offset 0x%X" % (bmp_path, off << 0xc))
                with open(bmp_path, 'rb') as b:
                    bhead = BMPHEAD(b.read(26))
                    b.seek(0, 0)
                    self.cfg.imgblkszs[i] = (bhead.fsize >> 0xc) + 1
                    self.cfg.imgblkoffs[i] = off
                    o.seek(off << 0xc)
                    o.write(b.read(bhead.fsize))
                    off += self.cfg.imgblkszs[i]
            o.seek(self.cfg.headoff)
            o.write(self.magic)
            for i in range(self.cfg.imgnum):
                o.write(struct.pack("<I", self.cfg.imgblkoffs[i]))
                o.write(struct.pack("<I", self.cfg.imgblkszs[i]))
            print("\tDone!")

if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(
        prog="logo_dumper",
        description="Dump Xiaomi bmp format logo and repack.",
    )
    parser.add_argument("IMAGE", help="Input image path.")
    parser.add_argument("FUNC", help="Function: unpack or repack.")
    parser.add_argument("-o", "--out", help="Set output dir (for unpack) or file path (for repack)", dest="out")
    parser.add_argument("--bmpdir", help="Set input BMP folder for repack (default: pic)", default="pic")

    args = parser.parse_args()

    func = args.FUNC.lower()
    print(f"Function : {func}")

    if func == 'unpack':
        out_dir = args.out if args.out else "pic"
        if not os.path.isdir(out_dir):
            os.makedirs(out_dir)
        LOGODUMPER(args.IMAGE, out_dir).unpack()

    elif func == 'repack':
        out_file = args.out if args.out else "new-logo.img"
        LOGODUMPER(args.IMAGE, out_file, bmpdir=args.bmpdir).repack()
    else:
        parser.error("Invalid function: must be 'unpack' or 'repack'")