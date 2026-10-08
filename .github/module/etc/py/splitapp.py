#!/data/data/com.tool.tree/files/home/termux/bin/python
import sys
import argparse
import shutil
from pathlib import Path
from struct import unpack, calcsize
from typing import Union, List, Generator
from dataclasses import dataclass

__author__ = 'MiuiPro.info DEV Team'
__copyright__ = 'Copyright (c) 2021 MiuiPro.info'
__version__ = '1.0.0'

HEADER_OFFSET = 0x5C
MAGIC_HEADER = 0x55AA5AA5


@dataclass
class HeaderAPP:
    # Format Header 3I8s2I16s16s16s16s3H
    MAGIC: int  # 4 bytes
    HEADER_LENGTH: int  # 4 bytes
    HARDWARE_ID: str  # 8 bytes
    FILE_SEQUENCE: int  # 4 bytes
    FILE_SIZE: int  # 4 bytes
    FILE_DATE: str  # 16 bytes
    FILE_TIME: str  # 16 bytes
    FILE_NAME: str  # 16 bytes
    HEADER_CHECKSUM: int  # 2 bytes
    BLOCK_SIZE: int  # 2 bytes


@dataclass
class ImageMetada:
    name: str
    path: Path
    start: int
    size: int


class SplitAPP:
    def __init__(self, *, input_file: Union[str, Path], out_dir: Union[str, Path]):
        if isinstance(input_file, str):
            input_file = Path(input_file)

        if isinstance(out_dir, str):
            out_dir = Path(out_dir)

        self._out_dir = out_dir

        self._input_file = open(input_file, 'rb')
        self._size = self._get_file_size()
        self._input_file.seek(HEADER_OFFSET, 0)

    def __exit__(self, **exc):
        self._input_file.close()

    def _auto_unpack(self, fmt, *, offset: Union[None, int] = None):
        size = calcsize(fmt)
        if offset:
            self._input_file.seek(offset + size)
        data = self._input_file.read(size)
        return unpack(fmt, data[0:size])

    def _get_file_size(self) -> int:
        self._input_file.seek(0, 2)
        return self._input_file.tell()

    def _read_partitions(self) -> List[ImageMetada]:
        list_partition = []

        while True:
            header = self._read_header()
            start_data = self._input_file.tell() + header.HEADER_LENGTH - 98
            self._input_file.seek(start_data, 0)

            list_partition.append(ImageMetada(name=header.FILE_NAME,
                                              path=Path(self._out_dir / f'{header.FILE_NAME.lower()}.img'),
                                              start=start_data,
                                              size=header.FILE_SIZE
                                              )
                                  )

            self._input_file.seek(header.FILE_SIZE, 1)
            padding_offset = 4 - self._input_file.tell() % 4
            if padding_offset < 4:
                self._input_file.seek(padding_offset, 1)

            if self._input_file.tell() >= self._size:
                break

        return list_partition

    def _read_buffer(self, *, buffer: int = 4096):
        while True:
            data = self._input_file.read(buffer)

            if not data:
                break

            yield data

    def _read_header(self) -> HeaderAPP:
        fmt = '3I8s2I16s16s16s16s3H'
        unpacked_header = self._auto_unpack(fmt)

        header = HeaderAPP(
            MAGIC=unpacked_header[0],
            HEADER_LENGTH=unpacked_header[1],
            HARDWARE_ID=unpacked_header[3],
            FILE_SEQUENCE=unpacked_header[4],
            FILE_SIZE=unpacked_header[5],
            FILE_DATE=unpacked_header[6].partition(b'\0')[0].decode(),
            FILE_TIME=unpacked_header[7].partition(b'\0')[0].decode(),
            FILE_NAME=unpacked_header[8].partition(b'\0')[0].decode(),
            HEADER_CHECKSUM=unpacked_header[10],
            BLOCK_SIZE=unpacked_header[11],
        )

        return header

    def _write_to_file(self, out_file_path: Path, *, start: int, size: int, buffer_size: int):
        self._input_file.seek(start, 0)
        with open(out_file_path, 'wb') as out:
            write_byte = 0
            buffer_size = buffer_size if size > buffer_size else size
            while write_byte != size:
                out.write(next(self._read_buffer(buffer=buffer_size)))
                write_byte += buffer_size
                buffer_size = buffer_size if size - write_byte > buffer_size else size - write_byte

    @staticmethod
    def generate_new_path(path: Path, uniq_new_name: bool = True) -> Generator:
        new_name_gen = (lambda p, c: (Path(p.parent / f'{p.stem}_{index}{p.suffix}')
                                      for index in range(c)))(path, 1000)

        if uniq_new_name:
            # Метод №1
            # Генерирует новое имя если нашёл такой же файл
            new_name_func = lambda p, gen: p if not p.exists() \
                else new_name_func(Path(p.parent / next(gen)), new_name_gen)
        else:
            # Метод №2
            # Имена постоянные зависит от дубликатов в списке партиций
            new_name_func = lambda p, gen: p

        try:
            while True:
                yield new_name_func(Path(path.parent / new_name_gen.__next__()), new_name_gen)

        except StopIteration:
            print("The limit on generating new names has been reached ")

    @staticmethod
    def rebuild_duplicate_path(partitions: List[ImageMetada]) -> List[ImageMetada]:
        paths = [item.path for item in partitions]
        duplicates_index = [n for n, x in enumerate(paths) if x in paths[:n]]

        if duplicates_index == list():
            return partitions

        for index in duplicates_index:
            generate_new_path_gen = SplitAPP.generate_new_path(partitions[index].path, uniq_new_name=False)
            for i in [i for i, x in enumerate(paths) if x == partitions[index].path]:
                partitions[i].path = next(generate_new_path_gen)

        return partitions

    def info(self):
        for partition in self._read_partitions():
            print(f'Partition: {partition.name} Start: {hex(partition.start)} Size: {hex(partition.size)}')

    def extract(self, *, buffer_size: int = 4096, list_partition: list):
        if list(self._out_dir.glob('*.img')):
            for file in self._out_dir.glob('*.img'):
                file.unlink(missing_ok=True)
        list_partitions = SplitAPP.rebuild_duplicate_path(self._read_partitions())
        for partition in list_partitions:
            if list_partition:
                if partition.name.lower() not in list_partition:
                    continue

            print(f'Extracting {partition.path.name}')
            self._write_to_file(partition.path, start=partition.start, size=partition.size, buffer_size=buffer_size)


def create_parser():
    _parser = argparse.ArgumentParser(description='Split UPDATE.APP file into img files')
    _parser.add_argument('-v', '--version', action='version',
                         version=f'version [{__version__}]')
    required = _parser.add_argument_group('Required')
    required.add_argument("-f", "--filename", required=True, help="Path to update.app file")
    required.add_argument("-o", "--out-dir", required=True, help="Path to output dir")
    optional = _parser.add_argument_group('Optional')
    optional.add_argument("-b", "--buffer-size", help="Buffer size")
    optional.add_argument("-l", "--list", nargs="*", metavar=('img1', 'img2'), help="List of img files to extract")

    return _parser


if __name__ == '__main__':
    parser = create_parser()
    namespace = parser.parse_args()

    if len(sys.argv) >= 5:
        buffer_size = 4096
        partition_list = []

        if namespace.buffer_size:
            buffer_size = namespace.buffer_size

        if namespace.list:
            partition_list = namespace.list

        out_dir = Path(namespace.out_dir)

        if not out_dir.exists():
            out_dir.mkdir(parents=True)

        SplitAPP(input_file=namespace.filename,
                 out_dir=namespace.out_dir).extract(buffer_size=buffer_size, list_partition=partition_list)

    else:
        parser.print_usage()
        sys.exit(64)
