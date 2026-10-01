#!/usr/bin/env python3
"""Wrap a PNG into a single-image .ico container (PNG-compressed ICO entry).

Windows accepts PNG-compressed entries since Vista, and jpackage only needs a
valid .ico file. No third-party libraries required.

Usage: png-to-ico.py <input.png> <output.ico>
"""

import struct
import sys


def main(argv):
    if len(argv) < 3:
        sys.stderr.write(__doc__)
        return 2
    source, destination = argv[1], argv[2]

    with open(source, "rb") as handle:
        png = handle.read()
    if not png.startswith(b"\x89PNG\r\n\x1a\n"):
        sys.stderr.write("input is not a PNG file: %s\n" % source)
        return 1

    width, height = struct.unpack(">II", png[16:24])
    dimension = 0 if max(width, height) >= 256 else max(width, height)

    header = struct.pack("<HHH", 0, 1, 1)
    entry = struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32,
                        len(png), 6 + 16)
    with open(destination, "wb") as handle:
        handle.write(header + entry + png)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
