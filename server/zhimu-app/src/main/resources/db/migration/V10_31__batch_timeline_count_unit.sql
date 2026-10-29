-- 批次时间线标题的数量改用品类单位并加千分位（例：“入栏 4600 头” → 鸡批次“入栏 4,600 只”）。
-- 代码已改为按批次品类单位、千分位生成；这里修正已写入的入栏 / 转栏 / 死淘 / 出栏标题。
-- 只匹配程序生成的固定格式（示例数据的“入栏 · 8480 只”等其它写法不动），可在已有数据上重复执行。

-- 1) 鸡等按“只”计数的品类：标题里的“头”改为“只”
UPDATE t_batch_timeline tl
    JOIN t_batch b ON b.id = tl.batch_id
    JOIN t_category c ON c.id = b.category_id
SET tl.title = REGEXP_REPLACE(tl.title, '^(入栏 |转栏 · |死淘 · |出栏 )([0-9,]+) 头', '$1$2 只')
WHERE c.count_unit = 'BIRD'
  AND tl.title REGEXP '^(入栏 |转栏 · |死淘 · |出栏 )[0-9,]+ 头';

-- 2) 四位及以上的数量加千分位
UPDATE t_batch_timeline tl
SET tl.title = CONCAT(
        REGEXP_SUBSTR(tl.title, '^(入栏 |转栏 · |死淘 · |出栏 )'),
        FORMAT(CAST(REGEXP_SUBSTR(tl.title, '[0-9]+') AS UNSIGNED), 0, 'en_US'),
        SUBSTRING(tl.title, CHAR_LENGTH(REGEXP_SUBSTR(tl.title, '^(入栏 |转栏 · |死淘 · |出栏 )[0-9]+')) + 1))
WHERE tl.title REGEXP '^(入栏 |转栏 · |死淘 · |出栏 )[0-9]{4,} (头|只)';
