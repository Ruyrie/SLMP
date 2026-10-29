-- 示例课程目录时长与视频实际时长不一致（例：6102 目录 3:42，播放器 10:34），学习进度“看到 1:24 · 还剩 2:18”按目录时长算错。
-- 13 门示例课程没有上传视频（video_file_id 为空），都直接挂同一个 Mux 测试 HLS（x36xhzz），实际时长约 634.6 秒。
-- 与运维脚本 server/tools/repair_demo_course_duration.py 同一规则：只改仍只挂该示例 HLS 的示例课程，时长取整秒 634（向下取整）。
-- 只在时长仍是原示例值时才改：云端已跑过脚本（已是 634）或管理员改过的课程不动；不改学习记录与媒体，可在已有数据上重复执行。
-- 上传视频的课程由转码完成回写真实时长（CourseTranscodeListener），不受影响。

UPDATE t_course c
    JOIN (SELECT 6101 AS id, 178 AS sample_sec
          UNION ALL SELECT 6102, 222
          UNION ALL SELECT 6103, 245
          UNION ALL SELECT 6104, 210
          UNION ALL SELECT 6105, 192
          UNION ALL SELECT 6106, 271
          UNION ALL SELECT 6201, 310
          UNION ALL SELECT 6202, 286
          UNION ALL SELECT 6203, 254
          UNION ALL SELECT 6204, 235
          UNION ALL SELECT 6301, 266
          UNION ALL SELECT 6302, 362
          UNION ALL SELECT 6303, 195) s ON s.id = c.id
SET c.duration_sec = 634,
    c.version      = c.version + 1,
    c.updated_at   = UTC_TIMESTAMP() + INTERVAL 8 HOUR
WHERE c.duration_sec = s.sample_sec
  AND c.video_file_id IS NULL
  AND c.deleted = 0
  AND EXISTS (SELECT 1 FROM t_course_media m WHERE m.course_id = c.id)
  AND NOT EXISTS (SELECT 1
                  FROM t_course_media m
                  WHERE m.course_id = c.id
                    AND (m.media_type <> 'HLS' OR m.url <> 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'));
