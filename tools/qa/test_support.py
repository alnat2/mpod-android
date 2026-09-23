"""Checks for QA support only; these are not application acceptance tests."""
import io
import hashlib
import json
from pathlib import Path
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import wave
import xml.etree.ElementTree as ET

from fixture_server import FixtureServer, export_files


class FixtureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.server = FixtureServer(("127.0.0.1", 0), self.temp.name, "http://127.0.0.1:8765")
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.server.log.close()
        self.temp.cleanup()

    def get(self, path, headers=None):
        return urlopen(Request(self.base + path, headers=headers or {}), timeout=5)

    def control(self, **values):
        return urlopen(Request(self.base + "/__control", data=json.dumps(values).encode(),
                               headers={"Content-Type": "application/json"}), timeout=5)

    def test_feed_revision_namespace_and_media(self):
        with self.get("/race.xml") as response:
            root = ET.fromstring(response.read())
        self.assertEqual(len(root.findall("channel/item")), 3)
        channel = root.find("channel")
        self.assertEqual(channel.find("{http://www.itunes.com/dtds/podcast-1.0.dtd}author").text, "QA Alias Author")
        with self.control(revision=2):
            pass
        with self.get("/race.xml") as response:
            self.assertEqual(len(ET.fromstring(response.read()).findall("channel/item")), 4)
        with self.get("/dc.xml") as response:
            self.assertEqual(ET.fromstring(response.read()).find("channel/{http://purl.org/dc/elements/1.1/}creator").text, "QA Dublin Core Author")
        with wave.open(io.BytesIO(self.server.audio)) as wav:
            self.assertEqual(wav.getnframes() / wav.getframerate(), 30)
        self.assertTrue((Path(self.temp.name) / "manifest.json").is_file())

    def test_range_and_rejected_range(self):
        for value, expected in (("bytes=8-15", self.server.audio[8:16]), ("bytes=-8", self.server.audio[-8:])):
            with self.get("/audio/race-1.wav", {"Range": value}) as response:
                self.assertEqual(response.status, 206)
                self.assertEqual(response.read(), expected)
        with self.assertRaises(HTTPError) as caught:
            self.get("/audio/race-1.wav", {"Range": "bytes=10000000-"})
        self.assertEqual(caught.exception.code, 416)

    def test_failures_validation_and_reset(self):
        with self.control(fail_feeds=["race"], fail_audio=True):
            pass
        for path in ("/race.xml", "/audio/race-1.wav"):
            with self.assertRaises(HTTPError) as caught:
                self.get(path)
            self.assertEqual(caught.exception.code, 503)
        with self.get("/dc.xml") as response:
            self.assertEqual(response.status, 200)
        with self.assertRaises(HTTPError) as caught:
            self.control(feed_delay=60)
        self.assertEqual(caught.exception.code, 400)
        with self.control(fail_feeds=[], fail_audio=False):
            pass
        with self.get("/race.xml") as response:
            self.assertEqual(response.status, 200)

    def test_delay_is_logged_before_response(self):
        with self.control(feed_delay=0.2):
            pass
        started = time.monotonic()
        with self.get("/race.xml") as response:
            response.read()
        self.assertGreaterEqual(time.monotonic() - started, 0.18)
        events = [json.loads(line) for line in self.server.events.read_text().splitlines()]
        request = next(event for event in events if event.get("path") == "/race.xml")
        end = next(event for event in events if event.get("event") == "end" and event["id"] == request["id"])
        self.assertGreaterEqual(end["time"] - request["time"], 0.18)

    def test_opml_duplicates_and_evidence_not_overwritten(self):
        with self.get("/import.opml") as response:
            urls = [item.attrib["xmlUrl"] for item in ET.fromstring(response.read()).findall("body/outline")]
        self.assertEqual(len(urls), 3)
        self.assertEqual(len(set(urls)), 2)
        with self.assertRaises(FileExistsError):
            FixtureServer(("127.0.0.1", 0), self.temp.name, "http://127.0.0.1:8765")

    def test_audio_delay_and_head(self):
        with self.control(audio_delay=0.05):
            pass
        started = time.monotonic()
        with self.get("/audio/race-1.wav", {"Range": "bytes=0-24575"}) as response:
            self.assertEqual(len(response.read()), 24576)
        self.assertGreaterEqual(time.monotonic() - started, 0.09)
        with urlopen(Request(self.base + "/audio/race-1.wav", method="HEAD"), timeout=5) as response:
            self.assertEqual(int(response.headers["Content-Length"]), len(self.server.audio))
            self.assertEqual(response.read(), b"")

    def test_control_is_responsive_during_delayed_feed(self):
        with self.control(feed_delay=0.5):
            pass
        result = []
        def fetch():
            with self.get("/race.xml") as response:
                result.append(ET.fromstring(response.read()))
        worker = threading.Thread(target=fetch)
        worker.start()
        deadline = time.monotonic() + 2
        while not any(json.loads(line).get("path") == "/race.xml"
                      for line in self.server.events.read_text().splitlines()):
            if time.monotonic() >= deadline:
                self.fail("Request did not start")
            time.sleep(0.01)
        with self.control(revision=2, feed_delay=0):
            pass
        worker.join(timeout=3)
        self.assertFalse(worker.is_alive())
        self.assertEqual(len(result[0].findall("channel/item")), 3)
        with self.get("/race.xml") as response:
            self.assertEqual(len(ET.fromstring(response.read()).findall("channel/item")), 4)

    def test_export_manifest_is_deterministic(self):
        export = Path(self.temp.name) / "export"
        export_files(export, "http://127.0.0.1:8765", self.server.audio, self.server.artwork)
        manifest = json.loads((export / "manifest.json").read_text())
        for name, expected in manifest.items():
            data = (export / name).read_bytes()
            self.assertEqual(len(data), expected["bytes"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), expected["sha256"])
            self.assertEqual(data, (Path(self.temp.name) / name).read_bytes())
        self.assertFalse((export / "requests.jsonl").exists())

    def test_independent_revisions_and_partial_failure_recovery(self):
        def episode_guids(name):
            with self.get(f"/{name}.xml") as response:
                return [item.findtext("guid") for item in ET.fromstring(response.read()).findall("channel/item")]
        baseline = episode_guids("race")
        with self.control(feed_revisions={"race": 2}):
            pass
        self.assertEqual(episode_guids("race"), baseline + ["qa-race-4"])
        self.assertEqual(len(episode_guids("aliases")), 3)
        with self.control(feed_revisions={"race": 3, "aliases": 2, "dc": 2}, fail_feeds=["dc"]):
            pass
        self.assertEqual(len(episode_guids("race")), 5)
        self.assertEqual(len(episode_guids("aliases")), 4)
        with self.assertRaises(HTTPError) as caught:
            episode_guids("dc")
        self.assertEqual(caught.exception.code, 503)
        with self.control(fail_feeds=[]):
            pass
        self.assertEqual(len(episode_guids("dc")), 4)
        with self.assertRaises(HTTPError) as caught:
            self.control(feed_revisions={"dc": 100})
        self.assertEqual(caught.exception.code, 400)


if __name__ == "__main__":
    unittest.main()
