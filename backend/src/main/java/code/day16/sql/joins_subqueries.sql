-- ============================================================
-- Day 16: 多表 JOIN + 子查询 + 索引 + 数据库设计
-- 数据库：reboot
-- ============================================================
USE reboot;

-- 1. DDL: 设备数据表（与 device 表一对多）
CREATE TABLE IF NOT EXISTS device_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id   BIGINT NOT NULL COMMENT '关联 device.id',
    value       DOUBLE NOT NULL COMMENT '读数',
    unit        VARCHAR(20) NOT NULL COMMENT '单位（℃/MPa/%RH/kPa）',
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_recorded_at (recorded_at),
    FOREIGN KEY (device_id) REFERENCES device(id)
) COMMENT '设备数据表';

-- 2. DML: 插入设备模拟数据
INSERT INTO device_data (device_id, value, unit, recorded_at) VALUES
(1, 25.3, '℃',    '2026-07-16 08:00:00'),
(1, 26.1, '℃',    '2026-07-16 09:00:00'),
(1, 27.8, '℃',    '2026-07-16 10:00:00'),
(1, 29.5, '℃',    '2026-07-16 11:00:00'),
(2, 0.85, 'MPa',  '2026-07-16 08:30:00'),
(2, 0.92, 'MPa',  '2026-07-16 09:30:00'),
(2, 0.78, 'MPa',  '2026-07-16 10:30:00'),
(3, 220,  'V',    '2026-07-16 08:00:00'),
(3, 218,  'V',    '2026-07-16 10:00:00'),
(4, 72.5, '%RH',  '2026-07-16 08:00:00'),
(4, 88.0, '%RH',  '2026-07-16 09:00:00'),
(4, 95.2, '%RH',  '2026-07-16 10:00:00'),
(6, 0.12, 'mm/s', '2026-07-16 08:00:00'),
(6, 1.85, 'mm/s', '2026-07-16 09:00:00');

-- ============================================================
-- DQL: JOIN 查询练习
-- ============================================================

-- 3.1 INNER JOIN: 设备 + 数据
SELECT '=== INNER JOIN: 设备+数据 ===' AS '';
SELECT d.name, dd.value, dd.unit, dd.recorded_at
FROM device d
INNER JOIN device_data dd ON d.id = dd.device_id
ORDER BY d.name, dd.recorded_at;

-- 3.2 LEFT JOIN: 所有设备（含无数据设备）
SELECT '=== LEFT JOIN: 所有设备 ===' AS '';
SELECT d.name, d.status, COUNT(dd.id) AS 数据条数
FROM device d
LEFT JOIN device_data dd ON d.id = dd.device_id
GROUP BY d.id, d.name, d.status
ORDER BY 数据条数 DESC;

-- 3.3 每个设备的最新一条数据
SELECT '=== 每个设备最新数据 ===' AS '';
SELECT d.name, dd.value, dd.unit, dd.recorded_at
FROM device d
INNER JOIN device_data dd ON d.id = dd.device_id
INNER JOIN (
    SELECT device_id, MAX(recorded_at) AS max_time
    FROM device_data GROUP BY device_id
) latest ON dd.device_id = latest.device_id AND dd.recorded_at = latest.max_time
ORDER BY d.name;

-- 3.4 设备报警数据（LEFT JOIN 找异常）
SELECT '=== 报警设备数据 ===' AS '';
SELECT d.name, d.status, dd.value, dd.unit
FROM device d
LEFT JOIN device_data dd ON d.id = dd.device_id
WHERE d.status = 'ALARM'
ORDER BY dd.recorded_at DESC;

-- ============================================================
-- DQL: 子查询练习
-- ============================================================

-- 4.1 数据值超过平均值的设备（标量子查询）
SELECT '=== 数值超过平均值的数据 ===' AS '';
SELECT d.name, dd.value, dd.unit
FROM device_data dd
INNER JOIN device d ON dd.device_id = d.id
WHERE dd.value > (SELECT AVG(value) FROM device_data)
ORDER BY dd.value DESC;

-- 4.2 EXISTS 子查询：有数据记录的设备
SELECT '=== EXISTS: 有数据的设备 ===' AS '';
SELECT name FROM device d
WHERE EXISTS (SELECT 1 FROM device_data dd WHERE dd.device_id = d.id);

-- 4.3 NOT EXISTS: 无数据记录的设备
SELECT '=== NOT EXISTS: 无数据的设备 ===' AS '';
SELECT name FROM device d
WHERE NOT EXISTS (SELECT 1 FROM device_data dd WHERE dd.device_id = d.id);

-- 4.4 从查询（派生表）：设备数据排名
SELECT '=== 每设备最新3条数据 ===' AS '';
SELECT * FROM (
    SELECT d.name, dd.value, dd.unit, dd.recorded_at,
           ROW_NUMBER() OVER (PARTITION BY dd.device_id ORDER BY dd.recorded_at DESC) AS rn
    FROM device_data dd
    INNER JOIN device d ON dd.device_id = d.id
) ranked WHERE rn <= 3 ORDER BY name, recorded_at;

-- ============================================================
-- 索引验证
-- ============================================================
SELECT '=== 索引列表 ===' AS '';
SHOW INDEX FROM device_data;

SELECT '=== EXPLAIN: JOIN 查询计划 ===' AS '';
EXPLAIN SELECT d.name, dd.value, dd.recorded_at
FROM device d
INNER JOIN device_data dd ON d.id = dd.device_id
WHERE dd.recorded_at > '2026-07-16 09:00:00';

-- ============================================================
-- 聚合进阶
-- ============================================================
SELECT '=== 设备数据聚合：按设备统计 ===' AS '';
SELECT d.name,
       COUNT(dd.id) AS 数据量,
       ROUND(AVG(dd.value), 2) AS 平均值,
       ROUND(MAX(dd.value), 2) AS 最大值,
       ROUND(MIN(dd.value), 2) AS 最小值
FROM device d
LEFT JOIN device_data dd ON d.id = dd.device_id
GROUP BY d.id, d.name
ORDER BY 数据量 DESC;
