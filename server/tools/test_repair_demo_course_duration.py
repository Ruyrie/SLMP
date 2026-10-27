import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("repair", Path(__file__).with_name("repair_demo_course_duration.py"))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


def fixture():
    courses = [{"id": value, "title": "demo", "duration_sec": 310, "video_file_id": None,
                "status": "ON_SHELF", "deleted": 0, "version": 1, "updated_at": "2026-10-27 00:00:00"} for value in repair.IDS]
    media = [{"id": value * 10 + index, "course_id": value, "media_type": "HLS", "quality": quality,
              "url": repair.MASTER, "status": "SUCCESS"} for value in repair.IDS for index, quality in enumerate(("P480", "P720"))]
    return {"courses": courses, "media": media}


class DurationRepairTest(unittest.TestCase):
    def test_manifest_audit_checks_every_variant_and_floors_seconds(self):
        master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhd.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2\nld.m3u8\n"
        def fetch(url):
            if url == repair.MASTER:
                return master
            tail = "4.584" if url.endswith("hd.m3u8") else "4.634"
            return "#EXTM3U\n" + "#EXTINF:10,\na.ts\n" * 63 + "#EXTINF:" + tail + ",\na.ts\n#EXT-X-ENDLIST\n"
        report = repair.media_audit(fetch)
        self.assertEqual(report["duration_sec"], 634)
        self.assertEqual([v["segments"] for v in report["variants"]], [64, 64])

    def test_untrusted_hosts_paths_redirects_and_live_playlists_are_rejected(self):
        for url in ("http://test-streams.mux.dev/x36xhzz/a.m3u8", "https://127.0.0.1/x36xhzz/a.m3u8",
                    "https://test-streams.mux.dev/other/a.m3u8", "https://test-streams.mux.dev/x36xhzz/%2e%2e/a.m3u8"):
            with self.assertRaises(ValueError):
                repair.trusted(url)
        with self.assertRaises(ValueError):
            repair.vod_duration("#EXTM3U\n#EXTINF:10,\na.ts\n")

    def test_bound_or_replaced_courses_cannot_pass_the_guard(self):
        rows = fixture()
        rows["courses"][0]["video_file_id"] = 20
        with self.assertRaises(ValueError):
            repair.validate_snapshot(rows)
        rows = fixture()
        rows["media"][0]["url"] = "https://real.example/course.m3u8"
        with self.assertRaises(ValueError):
            repair.validate_snapshot(rows)

    def test_sql_locks_and_asserts_snapshots_and_never_updates_learning_history(self):
        sql = repair.apply_sql(fixture(), 634)
        self.assertIn("START TRANSACTION", sql)
        self.assertEqual(sql.count("FOR UPDATE"), 2)
        self.assertIn("@changed_courses <> 13", sql)
        self.assertIn("duration_sec=634", sql)
        self.assertIn("COMMIT", sql)
        self.assertNotIn("t_course_progress", sql)
        self.assertNotIn("t_course_play_record", sql)
        self.assertNotIn("DROP", repair.sql_value("'); DROP TABLE t_course; --"))


if __name__ == "__main__":
    unittest.main()
