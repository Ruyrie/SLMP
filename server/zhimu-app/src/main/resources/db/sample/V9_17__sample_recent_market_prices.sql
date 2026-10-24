-- 只给开发/演示库补齐最近七天的示例行情；不修改已有发布价格，也不生成交易或生产记录。
-- 使用执行当天的北京时间，App 与 Web 按真实日期筛选时仍有近期趋势。
SET @market_today = DATE(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR));

INSERT IGNORE INTO t_market_price
(province_code, province_name, category_id, breed_id, price_type, price_date, price,
 price_unit, price_unit_text, corrected, status, source, created_at, updated_at)
SELECT c.province_code, c.province_name, c.category_id, c.breed_id, c.price_type,
       DATE_SUB(@market_today, INTERVAL days.n DAY),
       CASE c.category_id WHEN 101 THEN 14.90 ELSE 4.40 END - days.n * 0.02,
       'YUAN_PER_KG', '元/千克', 0, 'PUBLISHED', 'SEED',
       DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)
FROM t_market_price_config c
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) days
WHERE c.province_code = '150000' AND c.category_id IN (101, 201)
  AND c.breed_id = 0 AND c.price_type = 'SLAUGHTER' AND c.status = 'ENABLED';
