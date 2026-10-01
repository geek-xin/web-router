#!/usr/bin/env python3
"""Rasterise a small SVG icon into a PNG without third-party libraries.

Only the SVG subset used by wrouter's favicon is supported: <rect> (with
optional rx) and <path> made of absolute M/L/H/V/Z commands, plus <circle>.
That is enough to produce a usable placeholder icon for jpackage.

Usage: svg-to-png.py <input.svg> <output.png> [size]
"""

import re
import struct
import sys
import zlib


def _hex_to_rgb(value):
    value = value.strip().lstrip("#")
    if len(value) == 3:
        value = "".join(ch * 2 for ch in value)
    if len(value) != 6:
        raise ValueError("unsupported colour: %s" % value)
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def _parse_colour(raw):
    if raw is None:
        return None
    raw = raw.strip()
    if raw in ("none", "transparent", ""):
        return None
    if raw.startswith("#"):
        return _hex_to_rgb(raw)
    match = re.match(r"rgba?\(([^)]*)\)", raw)
    if match:
        parts = [p.strip() for p in match.group(1).split(",")]
        if len(parts) >= 3:
            return tuple(int(float(p)) for p in parts[:3])
    named = {"black": (0, 0, 0), "white": (255, 255, 255), "red": (255, 0, 0)}
    if raw.lower() in named:
        return named[raw.lower()]
    return None


def _iter_attrs(tag_body):
    for match in re.finditer(r'([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"', tag_body):
        yield match.group(1), match.group(2)


class Canvas(object):
    def __init__(self, size, background):
        self.size = size
        if background is None:
            background = (0, 0, 0, 0)
        self.pixels = bytearray(bytes(background) * (size * size))

    def blend(self, x, y, rgb, alpha):
        if alpha <= 0 or x < 0 or y < 0 or x >= self.size or y >= self.size:
            return
        if alpha > 1.0:
            alpha = 1.0
        index = (y * self.size + x) * 4
        dst_r, dst_g, dst_b, dst_a = self.pixels[index:index + 4]
        dst_a_f = dst_a / 255.0
        out_a = alpha + dst_a_f * (1.0 - alpha)
        if out_a <= 0:
            return
        for offset, channel in enumerate(rgb):
            src = channel / 255.0
            dst = self.pixels[index + offset] / 255.0
            out = (src * alpha + dst * dst_a_f * (1.0 - alpha)) / out_a
            self.pixels[index + offset] = max(0, min(255, int(round(out * 255))))
        self.pixels[index + 3] = max(0, min(255, int(round(out_a * 255))))

    def to_png(self):
        raw = bytearray()
        stride = self.size * 4
        for row in range(self.size):
            raw.append(0)
            raw.extend(self.pixels[row * stride:(row + 1) * stride])

        def chunk(tag, payload):
            return (struct.pack(">I", len(payload)) + tag + payload
                    + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

        header = struct.pack(">IIBBBBB", self.size, self.size, 8, 6, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def _point_in_rounded_rect(px, py, x0, y0, x1, y1, rx):
    if px < x0 or px > x1 or py < y0 or py > y1:
        return False
    if rx <= 0:
        return True
    for cx, cy in ((x0 + rx, y0 + rx), (x1 - rx, y0 + rx),
                   (x0 + rx, y1 - rx), (x1 - rx, y1 - rx)):
        inside_x = (px < x0 + rx) if cx == x0 + rx else (px > x1 - rx)
        inside_y = (py < y0 + rx) if cy == y0 + rx else (py > y1 - rx)
        if inside_x and inside_y:
            return (px - cx) ** 2 + (py - cy) ** 2 <= rx * rx
    return True


def _distance_to_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = ((px - ax) * dx + (py - ay) * dy) / length_sq
    t = max(0.0, min(1.0, t))
    return ((px - (ax + t * dx)) ** 2 + (py - (ay + t * dy)) ** 2) ** 0.5


def _subpaths(commands):
    segments = []
    current = []
    cx = cy = 0.0
    start_x = start_y = 0.0
    for command, args in commands:
        if command in ("M", "L"):
            for i in range(0, len(args) - 1, 2):
                x, y = args[i], args[i + 1]
                if command == "M" and i == 0:
                    if current:
                        segments.append(current)
                    current = []
                    start_x, start_y = x, y
                elif command == "M":
                    if current:
                        segments.append(current)
                    current = []
                    start_x, start_y = x, y
                else:
                    current.append((cx, cy, x, y))
                cx, cy = x, y
        elif command == "H":
            for x in args:
                current.append((cx, cy, x, cy))
                cx = x
        elif command == "V":
            for y in args:
                current.append((cx, cy, cx, y))
                cy = y
        elif command == "Z":
            current.append((cx, cy, start_x, start_y))
            cx, cy = start_x, start_y
            segments.append(current)
            current = []
    if current:
        segments.append(current)
    return segments


def _parse_path(d):
    tokens = re.findall(r"[MmLlHhVvZz]|-?\d*\.?\d+(?:e[-+]?\d+)?", d)
    commands = []
    index = 0
    current_command = None
    while index < len(tokens):
        token = tokens[index]
        if re.match(r"[A-Za-z]", token):
            current_command = token
            index += 1
            if current_command in ("Z", "z"):
                commands.append(("Z", []))
                continue
        if current_command is None:
            raise ValueError("path data does not start with a command")
        numbers = []
        while index < len(tokens) and not re.match(r"[A-Za-z]", tokens[index]):
            numbers.append(float(tokens[index]))
            index += 1
        commands.append((current_command.upper(), numbers))
    return commands


def render(svg_text, size):
    view_box = re.search(r'viewBox\s*=\s*"([^"]+)"', svg_text)
    if view_box:
        parts = [float(p) for p in re.split(r"[\s,]+", view_box.group(1).strip())]
        vb_x, vb_y, vb_w, vb_h = parts
    else:
        vb_x = vb_y = 0.0
        width = re.search(r'width\s*=\s*"([\d.]+)', svg_text)
        height = re.search(r'height\s*=\s*"([\d.]+)', svg_text)
        vb_w = float(width.group(1)) if width else 64.0
        vb_h = float(height.group(1)) if height else vb_w

    scale = size / float(vb_w)
    background = None
    bg_match = re.search(r"<svg[^>]*>", svg_text)
    canvas = Canvas(size, background)

    def to_px(x, y):
        return (x - vb_x) * scale, (y - vb_y) * scale

    def sample():
        step = 2
        offsets = [(i + 0.5) / step for i in range(step)]
        return [(ox, oy) for oy in offsets for ox in offsets]

    samples = sample()
    antialias = 1.0 / len(samples)

    for match in re.finditer(r"<(rect|circle|path)\b([^>]*?)/?>", svg_text, re.S):
        element, body = match.group(1), match.group(2)
        attrs = dict(_iter_attrs(body))
        fill = _parse_colour(attrs.get("fill"))
        stroke = _parse_colour(attrs.get("stroke"))
        stroke_width = float(attrs.get("stroke-width", "0") or 0)
        if fill is None and stroke is None:
            continue

        if element == "rect":
            x = float(attrs.get("x", 0))
            y = float(attrs.get("y", 0))
            w = float(attrs.get("width", 0))
            h = float(attrs.get("height", 0))
            rx = float(attrs.get("rx", 0) or 0)
            x0, y0 = to_px(x, y)
            x1, y1 = to_px(x + w, y + h)
            rx_px = rx * scale
            half = stroke_width * scale / 2.0 if stroke is not None else 0.0
            for py in range(max(0, int(y0 - half) - 1), min(size, int(y1 + half) + 2)):
                for px in range(max(0, int(x0 - half) - 1), min(size, int(x1 + half) + 2)):
                    fill_hits = 0
                    stroke_hits = 0
                    for ox, oy in samples:
                        sx, sy = px + ox, py + oy
                        if fill is not None and _point_in_rounded_rect(
                                sx, sy, x0 + half, y0 + half, x1 - half, y1 - half,
                                max(rx_px - half, 0.0)):
                            fill_hits += 1
                        if half > 0 and _point_in_rounded_rect(
                                sx, sy, x0 - half, y0 - half, x1 + half, y1 + half, rx_px + half) \
                                and not _point_in_rounded_rect(
                                    sx, sy, x0 + half, y0 + half, x1 - half, y1 - half,
                                    max(rx_px - half, 0.0)):
                            stroke_hits += 1
                    if fill_hits:
                        canvas.blend(px, py, fill, fill_hits * antialias)
                    if stroke_hits:
                        canvas.blend(px, py, stroke, stroke_hits * antialias)
        elif element == "circle":
            cx, cy = to_px(float(attrs.get("cx", 0)), float(attrs.get("cy", 0)))
            r = float(attrs.get("r", 0)) * scale
            half = stroke_width * scale / 2.0 if stroke is not None else 0.0
            for py in range(max(0, int(cy - r - half) - 1), min(size, int(cy + r + half) + 2)):
                for px in range(max(0, int(cx - r - half) - 1), min(size, int(cx + r + half) + 2)):
                    fill_hits = 0
                    stroke_hits = 0
                    for ox, oy in samples:
                        distance = ((px + ox - cx) ** 2 + (py + oy - cy) ** 2) ** 0.5
                        if fill is not None and distance <= r - half:
                            fill_hits += 1
                        if half > 0 and abs(distance - r) <= half:
                            stroke_hits += 1
                    if fill_hits:
                        canvas.blend(px, py, fill, fill_hits * antialias)
                    if stroke_hits:
                        canvas.blend(px, py, stroke, stroke_hits * antialias)
        else:
            segments = _subpaths(_parse_path(attrs.get("d", "")))
            if not segments:
                continue
            half = max(stroke_width * scale / 2.0, 0.35)
            for py in range(size):
                for px in range(size):
                    hits = 0
                    for ox, oy in samples:
                        sx, sy = px + ox, py + oy
                        if stroke is not None:
                            for segment in segments:
                                for ax, ay, bx, by in segment:
                                    p0 = to_px(ax, ay)
                                    p1 = to_px(bx, by)
                                    if _distance_to_segment(sx, sy, p0[0], p0[1], p1[0], p1[1]) <= half:
                                        hits += 1
                                        break
                                else:
                                    continue
                                break
                    if hits:
                        canvas.blend(px, py, stroke, hits * antialias)

    return canvas


def main(argv):
    if len(argv) < 3:
        sys.stderr.write(__doc__)
        return 2
    source, destination = argv[1], argv[2]
    size = int(argv[3]) if len(argv) > 3 else 512
    with open(source, "r", encoding="utf-8") as handle:
        svg_text = handle.read()
    canvas = render(svg_text, size)
    with open(destination, "wb") as handle:
        handle.write(canvas.to_png())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
