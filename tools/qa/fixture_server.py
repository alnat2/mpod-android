#!/usr/bin/env python3
"""Local, dependency-free RSS/audio fixtures. No app or emulator mutations."""
import argparse
import array
import hashlib
import io
import json
import math
from pathlib import Path
import struct
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
import wave
from xml.sax.saxutils import escape
import zlib

FEEDS = ("race", "aliases", "dc", "import-a", "import-b")
DEFAULTS = {"revision": 1, "feed_revisions": {}, "feed_delay": 0, "audio_delay": 0,
            "fail_feeds": [], "fail_audio": False}


def audio_bytes():
    samples = array.array("h", (int(1800 * math.sin(2 * math.pi * 440 * i / 16000))
                              for i in range(30 * 16000)))
    if sys.byteorder != "little":
        samples.byteswap()
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(samples.tobytes())
    return output.getvalue()


def artwork_bytes():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    # A distinctive blue/yellow checkerboard, not an external artwork dependency.
    pixels = b"".join(b"\x00" + b"".join(
        bytes((25, 90, 190) if (x // 16 + y // 16) % 2 else (255, 205, 40))
        for x in range(64)) for y in range(64))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


def feed_xml(base, name, revision, audio_size):
    author = "<d:creator>QA Dublin Core Author</d:creator>" if name == "dc" else "<p:author>QA Alias Author</p:author>"
    items = []
    for number in range(1, revision + 3):
        description = (f"<c:encoded><![CDATA[<p>QA content {number}</p>]]></c:encoded>"
                       if number != 2 else "<p:summary>QA summary 2</p:summary>")
        items.append(f"""<item>
<x:title>POISON title</x:title><title>QA {name} episode {number}</title>
<x:guid>POISON guid</x:guid><guid isPermaLink="false">qa-{name}-{number}</guid>
<x:encoded>POISON description</x:encoded><x:summary>POISON summary</x:summary>{description}
<x:enclosure url="{base}/missing.wav"/>
<enclosure url="{base}/audio/{name}-{number}.wav" length="{audio_size}" type="audio/wav"/>
<p:duration>00:00:30</p:duration><x:duration>99:59:59</x:duration>
<pubDate>Mon, 21 Sep 2026 10:00:0{number} +0000</pubDate>
<x:pubDate>Tue, 01 Jan 2030 00:00:00 +0000</x:pubDate>
</item>""")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:p="http://www.itunes.com/dtds/podcast-1.0.dtd"
 xmlns:c="http://purl.org/rss/1.0/modules/content/" xmlns:d="http://purl.org/dc/elements/1.1/"
 xmlns:x="urn:mpoddy:qa:unknown"><channel>
<x:title>POISON podcast</x:title><title>QA {name}</title>
<x:author>POISON author</x:author><x:creator>POISON creator</x:creator>{author}
<x:summary>POISON channel</x:summary><p:summary>QA channel summary</p:summary>
<x:image href="{base}/missing.png"/><p:image href="{base}/artwork.png"/>
<link>{base}/</link><lastBuildDate>Mon, 21 Sep 2026 10:00:00 +0000</lastBuildDate>
{''.join(items)}</channel></rss>""".encode()


def opml_bytes(base):
    outlines = "".join(f'<outline type="rss" text="QA {name}" xmlUrl="{escape(base)}/{name}.xml"/>'
                       for name in ("import-a", "import-b", "import-a"))
    return f'<?xml version="1.0"?><opml version="2.0"><head><title>QA import</title></head><body>{outlines}</body></opml>'.encode()


def export_files(output, base, audio, artwork):
    output.mkdir(parents=True, exist_ok=True)
    files = {f"{name}.xml": feed_xml(base, name, 1, len(audio)) for name in FEEDS}
    files.update({"race-v2.xml": feed_xml(base, "race", 2, len(audio)),
                  "import.opml": opml_bytes(base), "invalid.xml": b"<rss><channel>",
                  "tone.wav": audio, "artwork.png": artwork})
    for name, data in files.items():
        (output / name).write_bytes(data)
    manifest = {name: {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                for name, data in files.items()}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


class FixtureServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, output, public_base):
        if (Path(output) / "requests.jsonl").exists():
            raise FileExistsError("Use a new output directory to preserve previous evidence")
        super().__init__(address, Handler)
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=True)
        self.base = public_base.rstrip("/")
        self.audio = audio_bytes()
        self.artwork = artwork_bytes()
        self.lock = threading.Lock()
        self.state = dict(DEFAULTS)
        self.counter = 0
        self.events = self.output / "requests.jsonl"
        # Never silently truncate an earlier run's evidence.
        self.log = self.events.open("x", buffering=1)
        export_files(self.output, self.base, self.audio, self.artwork)

    def event(self, **values):
        with self.lock:
            self.log.write(json.dumps({"time": time.time(), **values}) + "\n")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def respond(self, code, body, content_type, extra=None, delay=0):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (extra or {}).items():
            self.send_header(key, str(value))
        self.end_headers()
        sent = 0
        try:
            if self.command != "HEAD":
                for offset in range(0, len(body), 8192):
                    if offset and delay:
                        time.sleep(delay)
                    part = body[offset:offset + 8192]
                    self.wfile.write(part)
                    self.wfile.flush()
                    sent += len(part)
            self.server.event(event="end", id=self.request_id, status=code, bytes=sent)
        except (BrokenPipeError, ConnectionResetError):
            self.server.event(event="disconnected", id=self.request_id, status=code, bytes=sent)

    def begin(self):
        with self.server.lock:
            self.server.counter += 1
            self.request_id = self.server.counter
            state = dict(self.server.state)
        self.server.event(event="start", id=self.request_id, method=self.command,
                          path=urlsplit(self.path).path, range=self.headers.get("Range"),
                          user_agent=self.headers.get("User-Agent"), state=state)
        return state

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        state = self.begin()
        path = urlsplit(self.path).path
        if path == "/__state":
            return self.respond(200, json.dumps(state).encode(), "application/json")
        name = path.removeprefix("/").removesuffix(".xml")
        if name in FEEDS and path == f"/{name}.xml":
            if state["feed_delay"]:
                time.sleep(state["feed_delay"])
            if name in state["fail_feeds"]:
                return self.respond(503, b"QA controlled feed failure", "text/plain")
            revision = state["feed_revisions"].get(name, state["revision"] if name == "race" else 1)
            return self.respond(200, feed_xml(self.server.base, name, revision, len(self.server.audio)), "application/rss+xml")
        if path == "/invalid.xml":
            return self.respond(200, b"<rss><channel>", "application/rss+xml")
        if path == "/import.opml":
            return self.respond(200, opml_bytes(self.server.base), "text/xml")
        if path == "/artwork.png":
            return self.respond(200, self.server.artwork, "image/png")
        valid_audio = {f"/audio/{name}-{n}.wav" for name in FEEDS for n in range(1, 6)}
        if path in valid_audio:
            if state["fail_audio"]:
                return self.respond(503, b"QA controlled audio failure", "text/plain")
            data = self.server.audio
            size = len(data)
            headers = {"Accept-Ranges": "bytes"}
            code = 200
            if self.headers.get("Range"):
                try:
                    unit, bounds = self.headers["Range"].split("=", 1)
                    first, last = bounds.split("-", 1)
                    if unit != "bytes" or "," in bounds:
                        raise ValueError()
                    if first:
                        start, end = int(first), min(int(last), size - 1) if last else size - 1
                    else:
                        start, end = max(0, size - int(last)), size - 1
                    if not 0 <= start <= end < size:
                        raise ValueError()
                except ValueError:
                    return self.respond(416, b"", "audio/wav", {"Content-Range": f"bytes */{size}"})
                data = data[start:end + 1]
                code = 206
                headers["Content-Range"] = f"bytes {start}-{end}/{size}"
            return self.respond(code, data, "audio/wav", headers, state["audio_delay"])
        return self.respond(404, b"QA fixture not found", "text/plain")

    def do_POST(self):
        self.begin()
        if self.path != "/__control":
            return self.respond(404, b"", "text/plain")
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 4096:
                raise ValueError()
            changes = json.loads(self.rfile.read(size))
            if not isinstance(changes, dict) or set(changes) - set(DEFAULTS):
                raise ValueError()
            with self.server.lock:
                state = {**self.server.state, **changes}
                if (type(state["revision"]) is not int or state["revision"] not in (1, 2)
                    or not isinstance(state["feed_revisions"], dict)
                    or any(name not in FEEDS or type(revision) is not int or revision not in (1, 2, 3)
                           for name, revision in state["feed_revisions"].items())
                    or type(state["feed_delay"]) not in (int, float) or not 0 <= state["feed_delay"] <= 20
                    or type(state["audio_delay"]) not in (int, float) or not 0 <= state["audio_delay"] <= 1
                    or type(state["fail_audio"]) is not bool
                    or not isinstance(state["fail_feeds"], list)
                    or any(name not in FEEDS for name in state["fail_feeds"])):
                    raise ValueError()
                self.server.state = state
            self.server.event(event="control", state=state)
            return self.respond(200, json.dumps(state).encode(), "application/json")
        except (ValueError, TypeError):
            return self.respond(400, b"Invalid control JSON", "text/plain")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--public-base", default="http://127.0.0.1:8765")
    parser.add_argument("--export-only", action="store_true", help="Write fixtures without opening a port")
    args = parser.parse_args()
    if args.export_only:
        export_files(args.output, args.public_base.rstrip("/"), audio_bytes(), artwork_bytes())
        print(f"QA fixtures exported to {args.output.resolve()}")
        return
    server = FixtureServer(("127.0.0.1", args.port), args.output, args.public_base)
    print(f"QA fixture listening on 127.0.0.1:{args.port}; output={args.output.resolve()}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        server.log.close()


if __name__ == "__main__":
    main()
