"""Audit the fixed legacy demo HLS and correct only its course-duration metadata.

Dry-run is the default. First run --database zhimu --env-file /etc/zhimu/env
--output /path/to/evidence; review plan.json. Apply with the same arguments plus
--apply --plan /path/to/evidence/plan.json. --media-only needs no DB credentials.
No media, learning-history or completion records are updated. No migration is edited.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal, ROUND_FLOOR
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urljoin, urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, Request

MASTER = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
IDS = (6101, 6102, 6103, 6104, 6105, 6106, 6201, 6202, 6203, 6204, 6301, 6302, 6303)
ID_SQL = ",".join(map(str, IDS))


def trusted(url):
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname != "test-streams.mux.dev"
            or parsed.port not in (None, 443) or parsed.username or parsed.password
            or not parsed.path.startswith("/x36xhzz/") or not parsed.path.endswith(".m3u8")
            or "/../" in parsed.path or "%" in parsed.path or parsed.query or parsed.fragment):
        raise ValueError("Untrusted HLS URL; only the fixed Mux demo directory is allowed")
    return url


class TrustedRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return super().redirect_request(request, fp, code, message, headers, trusted(new_url))


def fetch_manifest(url):
    with build_opener(TrustedRedirect()).open(Request(trusted(url), headers={"User-Agent": "SLMP-duration-audit/1"}), timeout=25) as response:
        body = response.read(2_000_001)
    if len(body) > 2_000_000:
        raise ValueError("Manifest is unexpectedly large")
    return body.decode("utf-8-sig")


def vod_duration(manifest):
    if not manifest.startswith("#EXTM3U") or "#EXT-X-ENDLIST" not in manifest:
        raise ValueError("A complete VOD playlist is required")
    durations = [Decimal(line.split(":", 1)[1].split(",", 1)[0])
                 for line in manifest.splitlines() if line.startswith("#EXTINF:")]
    if not durations or any(not value.is_finite() or value <= 0 for value in durations):
        raise ValueError("Invalid segment durations")
    return sum(durations), len(durations)


def media_audit(fetch=fetch_manifest):
    master = fetch(MASTER)
    if "#EXT-X-STREAM-INF:" not in master:
        raise ValueError("Expected the known demo master playlist")
    urls = [trusted(urljoin(MASTER, line.strip())) for line in master.splitlines()
            if line.strip() and not line.startswith("#")]
    if not 1 <= len(urls) <= 8 or len(set(urls)) != len(urls):
        raise ValueError("Unexpected variant list")
    variants = []
    for url in urls:
        text = fetch(url)
        seconds, segments = vod_duration(text)
        variants.append({"url": url, "seconds": str(seconds), "segments": segments,
                         "sha256": hashlib.sha256(text.encode()).hexdigest()})
    seconds = [Decimal(row["seconds"]) for row in variants]
    whole = {int(value.to_integral_value(rounding=ROUND_FLOOR)) for value in seconds}
    if len(whole) != 1 or max(seconds) - min(seconds) >= 1 or min(seconds) < 1:
        raise ValueError("Variants disagree about video duration")
    return {"master": MASTER, "master_sha256": hashlib.sha256(master.encode()).hexdigest(),
            "variants": variants, "duration_sec": whole.pop(), "rounding": "floor"}


def settings(path):
    values = dict(os.environ)
    if path:
        for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
            line = raw.strip().removeprefix("export ")
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[key.strip()] = value
    if not values.get("DB_USER") or "DB_PASSWORD" not in values:
        raise ValueError("DB_USER and DB_PASSWORD are required in the environment or env file")
    return values


def mysql(sql, args, values):
    env = dict(os.environ, MYSQL_PWD=values["DB_PASSWORD"])
    command = [args.mysql, "--protocol=TCP", "--host=" + values.get("DB_HOST", "127.0.0.1"),
               "--port=" + values.get("DB_PORT", "3306"), "--user=" + values["DB_USER"],
               "--database=" + args.database, "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"]
    result = subprocess.run(command, input=sql, text=True, encoding="utf-8", capture_output=True, env=env, timeout=90)
    if result.returncode:
        raise RuntimeError("MySQL operation rejected; any open transaction was rolled back. Credentials are not printed.")
    return result.stdout.strip()


def snapshot(args, values):
    courses = mysql("SELECT JSON_OBJECT('id',id,'title',title,'duration_sec',duration_sec,'video_file_id',video_file_id,"
                    "'status',status,'deleted',deleted,'version',version,'updated_at',DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s')) "
                    f"FROM t_course WHERE id IN ({ID_SQL}) ORDER BY id;", args, values)
    media = mysql("SELECT JSON_OBJECT('id',id,'course_id',course_id,'media_type',media_type,'quality',quality,'url',url,'status',status) "
                  f"FROM t_course_media WHERE course_id IN ({ID_SQL}) ORDER BY id;", args, values)
    result = {"courses": [json.loads(line) for line in courses.splitlines()], "media": [json.loads(line) for line in media.splitlines()]}
    validate_snapshot(result)
    return result


def validate_snapshot(rows):
    if tuple(row["id"] for row in rows["courses"]) != IDS:
        raise ValueError("The exact thirteen demo course IDs must be present")
    if any(row["video_file_id"] is not None or row["status"] != "ON_SHELF" or row["deleted"] != 0
           for row in rows["courses"]):
        raise ValueError("Course ownership/status guard failed")
    if len(rows["media"]) != 26 or any(row["url"] != MASTER or row["status"] != "SUCCESS"
            or row["media_type"] != "HLS" for row in rows["media"]):
        raise ValueError("Only the known, unbound demo HLS rows may be repaired")
    for course_id in IDS:
        if sorted(row["quality"] for row in rows["media"] if row["course_id"] == course_id) != ["P480", "P720"]:
            raise ValueError("Each demo course must have exactly its two known qualities")


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "CONVERT(0x" + str(value).encode().hex() + " USING utf8mb4)"


def matching(rows):
    return " OR ".join("(" + " AND ".join(f"`{key}` <=> {sql_value(value)}" for key, value in row.items()) + ")" for row in rows)


def apply_sql(before, duration):
    validate_snapshot(before)
    changed = sum(row["duration_sec"] != duration for row in before["courses"])
    # A duplicate insert is a deliberate assertion failure. The client exits without COMMIT,
    # causing rollback; this also works on older MySQL versions without enforced CHECKs.
    return f"""CREATE TEMPORARY TABLE qa_course_duration_guard (ok INT PRIMARY KEY);
INSERT INTO qa_course_duration_guard VALUES (1);
START TRANSACTION;
SELECT id FROM t_course WHERE id IN ({ID_SQL}) FOR UPDATE;
SELECT id FROM t_course_media WHERE course_id IN ({ID_SQL}) FOR UPDATE;
INSERT INTO qa_course_duration_guard SELECT 1 WHERE (SELECT COUNT(*) FROM t_course WHERE {matching(before['courses'])}) <> 13;
INSERT INTO qa_course_duration_guard SELECT 1 WHERE (SELECT COUNT(*) FROM t_course_media WHERE course_id IN ({ID_SQL})) <> 26;
INSERT INTO qa_course_duration_guard SELECT 1 WHERE (SELECT COUNT(*) FROM t_course_media WHERE {matching(before['media'])}) <> 26;
UPDATE t_course SET duration_sec={duration}, version=version+1, updated_at=NOW()
WHERE id IN ({ID_SQL}) AND duration_sec <> {duration};
SET @changed_courses = ROW_COUNT();
INSERT INTO qa_course_duration_guard SELECT 1 WHERE @changed_courses <> {changed};
COMMIT;
SELECT @changed_courses;
"""


def save(path, value):
    with path.open("x", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
    path.chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--media-only", action="store_true")
    parser.add_argument("--database")
    parser.add_argument("--env-file")
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--plan", type=Path)
    args = parser.parse_args()
    audit = media_audit()
    if args.media_only:
        if args.apply:
            raise ValueError("--media-only cannot apply database changes")
        print(json.dumps(audit, indent=2))
        return
    if not args.database or not re.fullmatch(r"[A-Za-z0-9_]+", args.database) or not args.output:
        raise ValueError("An explicit --database and --output directory are required")
    args.output.mkdir(parents=True, exist_ok=True)
    values = settings(args.env_file)
    before = snapshot(args, values)
    if not args.apply:
        plan = {"schema": 1, "created_at": datetime.now(timezone.utc).isoformat(), "database": args.database,
                "media_audit": audit, "before": before}
        save(args.output / "plan.json", plan)
        print(json.dumps({"mode": "DRY_RUN", "plan": str(args.output / 'plan.json'), "duration_sec": audit["duration_sec"],
                          "courses": len(before["courses"]), "learning_records_changed": 0}))
        return
    if not args.plan:
        raise ValueError("--apply requires the reviewed --plan from a dry run")
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    if plan.get("schema") != 1 or plan.get("database") != args.database or plan.get("before") != before or plan.get("media_audit") != audit:
        raise ValueError("Plan is stale or does not match this database and the verified media")
    save(args.output / "backup-before-apply.json", plan)
    result = mysql(apply_sql(before, audit["duration_sec"]), args, values)
    after = snapshot(args, values)
    if any(row["duration_sec"] != audit["duration_sec"] for row in after["courses"]):
        raise RuntimeError("Post-apply duration verification failed; backup is preserved")
    save(args.output / "result.json", {"status": "PASS", "changed_courses": int(result.splitlines()[-1]),
         "duration_sec": audit["duration_sec"], "after": after, "learning_records_changed": 0})
    print(json.dumps({"mode": "APPLIED", "duration_sec": audit["duration_sec"], "evidence": str(args.output / 'result.json')}))


if __name__ == "__main__":
    main()
