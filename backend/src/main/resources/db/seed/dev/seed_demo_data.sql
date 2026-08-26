-- ===================================================================
-- Industrial AI Hub — Dev Demo Seed（仅限开发环境）
-- Version: 3.0 | Updated: 2026-08-18
--
-- 定位（ADR 0019「演示种子与生产 Flyway 隔离」）：
--   1. 本文件是「演示/测试种子数据」的唯一事实源，正式迁移链中不再包含种子
--      （原 V2__seed_test_data.sql 已退役，见 ADR 0019）。
--   2. 目录 db/seed/dev/ 位于 Flyway locations（classpath:db/migration）之外，
--      任何环境启动时都不会被 Flyway 扫描执行。
--   3. 生产环境【禁止】执行本文件；开发环境显式执行：
--          ./scripts/seed-dev.sh
--      或（不推荐，需自行保证 utf8mb4）：
--          mysql --default-character-set=utf8mb4 -u <user> -p <db> < 本文件
--
-- 幂等性：所有 INSERT 均带业务键 NOT EXISTS 守卫，可安全重复执行，
-- 不会产生重复用户/设备/告警/采集数据/日志。
-- 设备外键按 device_code 解析（不依赖自增 ID），适用于已有开发库。
--
-- 必需初始化数据（默认角色 + admin 账户）在 V1__baseline.sql，与本文件无关。
-- ===================================================================

SET NAMES utf8mb4;

-- ================================================================
-- 1. 测试用户（20 条）— 密码均为 Test123456 (BCrypt)
--    hash: $2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje
--    守卫键：user.username（uk_username）
-- ================================================================
INSERT INTO `user` (`username`, `password`, `email`, `phone`, `status`)
SELECT t.username, t.password, t.email, t.phone, t.status
FROM (
    SELECT 'operator01' AS username, '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje' AS password, 'op01@test.com' AS email, '13800001001' AS phone, 1 AS status
    UNION ALL SELECT 'operator02', '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'op02@test.com', '13800001002', 1
    UNION ALL SELECT 'viewer01',   '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'vw01@test.com', '13800001003', 1
    UNION ALL SELECT 'viewer02',   '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'vw02@test.com', '13800001004', 1
    UNION ALL SELECT 'user05',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u05@test.com', '13800001005', 1
    UNION ALL SELECT 'user06',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u06@test.com', '13800001006', 1
    UNION ALL SELECT 'user07',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u07@test.com', '13800001007', 1
    UNION ALL SELECT 'user08',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u08@test.com', '13800001008', 1
    UNION ALL SELECT 'user09',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u09@test.com', '13800001009', 1
    UNION ALL SELECT 'user10',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u10@test.com', '13800001010', 1
    UNION ALL SELECT 'user11',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u11@test.com', '13800001011', 0
    UNION ALL SELECT 'user12',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u12@test.com', '13800001012', 0
    UNION ALL SELECT 'user13',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u13@test.com', '13800001013', 1
    UNION ALL SELECT 'user14',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u14@test.com', '13800001014', 1
    UNION ALL SELECT 'user15',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u15@test.com', '13800001015', 1
    UNION ALL SELECT 'user16',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u16@test.com', '13800001016', 1
    UNION ALL SELECT 'user17',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u17@test.com', '13800001017', 1
    UNION ALL SELECT 'user18',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u18@test.com', '13800001018', 1
    UNION ALL SELECT 'user19',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u19@test.com', '13800001019', 1
    UNION ALL SELECT 'user20',     '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje', 'u20@test.com', '13800001020', 1
) t
WHERE NOT EXISTS (SELECT 1 FROM `user` u WHERE u.username = t.username);

-- ================================================================
-- 2. 分配角色：operator → OPERATOR, viewer → VIEWER, user05~20 → VIEWER
--    守卫键：user_role(user_id, role_id)
--    （仅处理本文件创建的演示用户，不触碰开发者自建用户）
-- ================================================================
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.role_code = 'OPERATOR'
WHERE u.username IN ('operator01', 'operator02')
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.role_code = 'VIEWER'
WHERE u.username IN ('viewer01', 'viewer02')
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.role_code = 'VIEWER'
WHERE u.username IN ('user05','user06','user07','user08','user09','user10',
                     'user11','user12','user13','user14','user15','user16',
                     'user17','user18','user19','user20')
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- ================================================================
-- 3. 测试设备（50 条）—— 覆盖 6 种设备类型
--    守卫键：device.device_code（uk_device_code）
-- ================================================================
INSERT INTO `device` (`device_name`, `device_code`, `device_type`, `status`, `ip_address`, `port`, `location`)
SELECT t.device_name, t.device_code, t.device_type, t.status, t.ip_address, t.port, t.location
FROM (
    SELECT '温控传感器-01' AS device_name, 'TEMP-001' AS device_code, 'SENSOR' AS device_type, 1 AS status, '192.168.1.101' AS ip_address, 502 AS port, '一车间-东区-1号' AS location
    UNION ALL SELECT '温控传感器-02', 'TEMP-002', 'SENSOR', 1, '192.168.1.102', 502, '一车间-东区-2号'
    UNION ALL SELECT '温控传感器-03', 'TEMP-003', 'SENSOR', 0, '192.168.1.103', 502, '一车间-西区-1号'
    UNION ALL SELECT '压力传感器-01', 'PRESS-001', 'SENSOR', 1, '192.168.1.201', 502, '二车间-管线A'
    UNION ALL SELECT '压力传感器-02', 'PRESS-002', 'SENSOR', 1, '192.168.1.202', 502, '二车间-管线B'
    UNION ALL SELECT '压力传感器-03', 'PRESS-003', 'SENSOR', 2, '192.168.1.203', 502, '二车间-管线C'
    UNION ALL SELECT '湿度传感器-01', 'HUM-001', 'SENSOR', 1, '192.168.2.101', 502, '仓库-东区'
    UNION ALL SELECT '湿度传感器-02', 'HUM-002', 'SENSOR', 1, '192.168.2.102', 502, '仓库-西区'
    UNION ALL SELECT 'PLC-主控-01', 'PLC-M-001', 'PLC', 1, '192.168.10.1', 102, '中央控制室-主控柜'
    UNION ALL SELECT 'PLC-主控-02', 'PLC-M-002', 'PLC', 1, '192.168.10.2', 102, '中央控制室-辅控柜'
    UNION ALL SELECT 'PLC-产线A-01', 'PLC-A-001', 'PLC', 1, '192.168.11.1', 102, '一车间-产线A'
    UNION ALL SELECT 'PLC-产线A-02', 'PLC-A-002', 'PLC', 0, '192.168.11.2', 102, '一车间-产线A-备'
    UNION ALL SELECT 'PLC-产线B-01', 'PLC-B-001', 'PLC', 1, '192.168.12.1', 102, '二车间-产线B'
    UNION ALL SELECT 'PLC-产线B-02', 'PLC-B-002', 'PLC', 1, '192.168.12.2', 102, '二车间-产线B-备'
    UNION ALL SELECT 'PLC-产线C-01', 'PLC-C-001', 'PLC', 2, '192.168.13.1', 102, '三车间-产线C'
    UNION ALL SELECT '工业摄像头-01', 'CAM-001', 'CAMERA', 1, '192.168.20.101', 554, '一车间-入口'
    UNION ALL SELECT '工业摄像头-02', 'CAM-002', 'CAMERA', 1, '192.168.20.102', 554, '一车间-出口'
    UNION ALL SELECT '工业摄像头-03', 'CAM-003', 'CAMERA', 1, '192.168.20.103', 554, '二车间-入口'
    UNION ALL SELECT '工业摄像头-04', 'CAM-004', 'CAMERA', 0, '192.168.20.104', 554, '二车间-出口'
    UNION ALL SELECT '工业摄像头-05', 'CAM-005', 'CAMERA', 1, '192.168.20.105', 554, '仓库-主通道'
    UNION ALL SELECT '焊接机器人-01', 'ROBOT-W-001', 'ROBOT', 1, '192.168.30.1', 8080, '焊接车间-工位1'
    UNION ALL SELECT '焊接机器人-02', 'ROBOT-W-002', 'ROBOT', 1, '192.168.30.2', 8080, '焊接车间-工位2'
    UNION ALL SELECT '焊接机器人-03', 'ROBOT-W-003', 'ROBOT', 2, '192.168.30.3', 8080, '焊接车间-工位3'
    UNION ALL SELECT '搬运机器人-01', 'ROBOT-T-001', 'ROBOT', 1, '192.168.31.1', 8080, '物流区-入口'
    UNION ALL SELECT '搬运机器人-02', 'ROBOT-T-002', 'ROBOT', 1, '192.168.31.2', 8080, '物流区-出口'
    UNION ALL SELECT '喷涂机器人-01', 'ROBOT-P-001', 'ROBOT', 1, '192.168.32.1', 8080, '喷涂车间-区A'
    UNION ALL SELECT '喷涂机器人-02', 'ROBOT-P-002', 'ROBOT', 0, '192.168.32.2', 8080, '喷涂车间-区B'
    UNION ALL SELECT '变频器-01', 'VFD-001', 'OTHER', 1, '192.168.5.101', 502, '配电房-1号柜'
    UNION ALL SELECT '变频器-02', 'VFD-002', 'OTHER', 1, '192.168.5.102', 502, '配电房-2号柜'
    UNION ALL SELECT '变频器-03', 'VFD-003', 'OTHER', 1, '192.168.5.103', 502, '配电房-3号柜'
    UNION ALL SELECT '变频器-04', 'VFD-004', 'OTHER', 0, '192.168.5.104', 502, '配电房-4号柜'
    UNION ALL SELECT '机床-CNC-01', 'CNC-001', 'OTHER', 1, '192.168.6.101', 8080, '机械车间-工位A'
    UNION ALL SELECT '机床-CNC-02', 'CNC-002', 'OTHER', 1, '192.168.6.102', 8080, '机械车间-工位B'
    UNION ALL SELECT '机床-CNC-03', 'CNC-003', 'OTHER', 2, '192.168.6.103', 8080, '机械车间-工位C'
    UNION ALL SELECT 'AGV-物流小车-01', 'AGV-001', 'ROBOT', 1, '192.168.40.1', 9090, '仓库-分拣区'
    UNION ALL SELECT 'AGV-物流小车-02', 'AGV-002', 'ROBOT', 1, '192.168.40.2', 9090, '仓库-运输通道1'
    UNION ALL SELECT 'AGV-物流小车-03', 'AGV-003', 'ROBOT', 0, '192.168.40.3', 9090, '仓库-运输通道2'
    UNION ALL SELECT 'SCADA-采集网关-01', 'SCADA-001', 'OTHER', 1, '192.168.100.1', 2404, '数据中心-机柜A'
    UNION ALL SELECT 'SCADA-采集网关-02', 'SCADA-002', 'OTHER', 1, '192.168.100.2', 2404, '数据中心-机柜B'
    UNION ALL SELECT '安全光幕-01', 'SAFE-001', 'SENSOR', 1, '192.168.7.101', 502, '冲压车间-正面'
    UNION ALL SELECT '安全光幕-02', 'SAFE-002', 'SENSOR', 1, '192.168.7.102', 502, '冲压车间-侧面'
    UNION ALL SELECT '扫码枪-01', 'SCAN-001', 'CAMERA', 1, '192.168.21.1', 80, '包装线-入口'
    UNION ALL SELECT '扫码枪-02', 'SCAN-002', 'CAMERA', 0, '192.168.21.2', 80, '包装线-出口'
    UNION ALL SELECT '环境监测-01', 'ENV-001', 'SENSOR', 1, '192.168.8.1', 502, '厂区-大气监测点'
    UNION ALL SELECT '环境监测-02', 'ENV-002', 'SENSOR', 1, '192.168.8.2', 502, '厂区-排水监测点'
    UNION ALL SELECT '压缩机-01', 'COMP-001', 'OTHER', 1, '192.168.9.1', 502, '气动车间-主压缩机'
    UNION ALL SELECT '压缩机-02', 'COMP-002', 'OTHER', 1, '192.168.9.2', 502, '气动车间-备用压缩机'
    UNION ALL SELECT '锅炉监测-01', 'BOIL-001', 'SENSOR', 1, '192.168.3.1', 502, '锅炉房-1号'
    UNION ALL SELECT '锅炉监测-02', 'BOIL-002', 'SENSOR', 0, '192.168.3.2', 502, '锅炉房-2号'
    UNION ALL SELECT '电动阀门-01', 'VALVE-001', 'OTHER', 1, '192.168.4.1', 502, '管道区-蒸汽主管'
) t
WHERE NOT EXISTS (SELECT 1 FROM `device` d WHERE d.device_code = t.device_code);

-- ================================================================
-- 4. 测试告警（12 条）—— 覆盖已确认/已解决/未处理三种状态
--    守卫键：(device_id, alarm_type, triggered_at)；device_id 按 device_code 解析
-- ================================================================
INSERT INTO `alarm` (`device_id`, `alarm_type`, `alarm_level`, `alarm_message`, `status`, `triggered_at`, `resolved_at`)
SELECT d.id, t.alarm_type, t.alarm_level, t.alarm_message, t.status, t.triggered_at, t.resolved_at
FROM `device` d
JOIN (
    SELECT 'TEMP-001' AS device_code, 'OVER_TEMP' AS alarm_type, 2 AS alarm_level, '温控传感器-01 温度超过阈值 85°C，当前 92.3°C' AS alarm_message, 0 AS status, '2026-07-24 08:30:00' AS triggered_at, NULL AS resolved_at
    UNION ALL SELECT 'TEMP-003', 'OFFLINE', 3, '温控传感器-03 离线超过 30 分钟', 0, '2026-07-24 09:15:00', NULL
    UNION ALL SELECT 'PRESS-002', 'UNDER_PRESSURE', 2, '压力传感器-02 压力过低 0.12 MPa（正常 > 0.3）', 1, '2026-07-24 10:00:00', NULL
    UNION ALL SELECT 'PRESS-003', 'OVER_TEMP', 3, '压力传感器-03 维护中，温度异常升高 65°C', 1, '2026-07-24 10:45:00', NULL
    UNION ALL SELECT 'PLC-M-002', 'OFFLINE', 3, 'PLC-主控-02 通信中断，主备切换中', 2, '2026-07-24 11:00:00', '2026-07-24 11:05:00'
    UNION ALL SELECT 'PLC-A-002', 'OFFLINE', 2, 'PLC-产线A-02 掉线，备用启动', 0, '2026-07-25 02:30:00', NULL
    UNION ALL SELECT 'ROBOT-W-001', 'OVER_TEMP', 1, '焊接机器人-01 焊接头温度微幅偏高 48°C', 2, '2026-07-24 14:00:00', '2026-07-24 14:20:00'
    UNION ALL SELECT 'ROBOT-W-002', 'UNDER_PRESSURE', 2, '焊接机器人-02 冷却液压力不足 0.08 MPa', 0, '2026-07-25 06:00:00', NULL
    UNION ALL SELECT 'ROBOT-T-001', 'OFFLINE', 2, '搬运机器人-01 导航模块异常，停止移动', 1, '2026-07-24 15:30:00', NULL
    UNION ALL SELECT 'VFD-003', 'OVER_TEMP', 1, '变频器-03 散热风扇转速降低，内部温度 52°C', 0, '2026-07-25 01:15:00', NULL
    UNION ALL SELECT 'CNC-003', 'OFFLINE', 3, '机床-CNC-03 主轴电机过载停机', 2, '2026-07-24 12:00:00', '2026-07-24 13:10:00'
    UNION ALL SELECT 'COMP-001', 'UNDER_PRESSURE', 1, '压缩机-01 输出压力波动 ±0.05 MPa', 0, '2026-07-25 04:45:00', NULL
) t ON t.device_code = d.device_code
WHERE NOT EXISTS (
    SELECT 1 FROM `alarm` a
    WHERE a.device_id = d.id AND a.alarm_type = t.alarm_type AND a.triggered_at = t.triggered_at
);

-- ================================================================
-- 5. 测试 device_data（模拟采集数据，约 110 条）
--    守卫键：(device_id, data_type, recorded_at)；device_id 按 device_code 解析
-- ================================================================

-- 温度数据（温控传感器 TEMP-001/002）
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'TEMPERATURE' AS data_type, 78.5 AS data_value, '°C' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'TEMPERATURE', 79.1, '°C', '2026-07-25 09:00:00'
    UNION ALL SELECT 'TEMPERATURE', 85.3, '°C', '2026-07-25 10:00:00'
    UNION ALL SELECT 'TEMPERATURE', 83.7, '°C', '2026-07-25 11:00:00'
) t
WHERE d.device_code = 'TEMP-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'TEMPERATURE' AS data_type, 72.4 AS data_value, '°C' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'TEMPERATURE', 73.8, '°C', '2026-07-25 09:00:00'
    UNION ALL SELECT 'TEMPERATURE', 74.1, '°C', '2026-07-25 10:00:00'
    UNION ALL SELECT 'TEMPERATURE', 73.2, '°C', '2026-07-25 11:00:00'
) t
WHERE d.device_code = 'TEMP-002'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- 压力数据（压力传感器 PRESS-001/002）
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'PRESSURE' AS data_type, 0.45 AS data_value, 'MPa' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'PRESSURE', 0.44, 'MPa', '2026-07-25 09:00:00'
    UNION ALL SELECT 'PRESSURE', 0.46, 'MPa', '2026-07-25 10:00:00'
) t
WHERE d.device_code = 'PRESS-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'PRESSURE' AS data_type, 0.38 AS data_value, 'MPa' AS unit, '2026-07-25 08:30:00' AS recorded_at
    UNION ALL SELECT 'PRESSURE', 0.34, 'MPa', '2026-07-25 09:30:00'
    UNION ALL SELECT 'PRESSURE', 0.22, 'MPa', '2026-07-25 10:30:00'
) t
WHERE d.device_code = 'PRESS-002'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- 湿度数据（湿度传感器 HUM-001/002）
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'HUMIDITY' AS data_type, 55.2 AS data_value, '%' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'HUMIDITY', 56.1, '%', '2026-07-25 10:00:00'
    UNION ALL SELECT 'HUMIDITY', 58.3, '%', '2026-07-25 12:00:00'
) t
WHERE d.device_code = 'HUM-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'HUMIDITY' AS data_type, 62.5 AS data_value, '%' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'HUMIDITY', 61.8, '%', '2026-07-25 10:00:00'
    UNION ALL SELECT 'HUMIDITY', 60.4, '%', '2026-07-25 12:00:00'
) t
WHERE d.device_code = 'HUM-002'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- 转速数据（PLC 主控 PLC-M-001）
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'SPEED' AS data_type, 1500.0 AS data_value, 'RPM' AS unit, '2026-07-25 09:00:00' AS recorded_at
    UNION ALL SELECT 'SPEED', 1505.5, 'RPM', '2026-07-25 10:00:00'
    UNION ALL SELECT 'SPEED', 1498.2, 'RPM', '2026-07-25 11:00:00'
    UNION ALL SELECT 'SPEED', 1502.1, 'RPM', '2026-07-25 12:00:00'
) t
WHERE d.device_code = 'PLC-M-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- 电流数据（变频器 VFD-001/002）
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'CURRENT' AS data_type, 12.5 AS data_value, 'A' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'CURRENT', 13.1, 'A', '2026-07-25 10:00:00'
    UNION ALL SELECT 'CURRENT', 15.8, 'A', '2026-07-25 12:00:00'
) t
WHERE d.device_code = 'VFD-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'CURRENT' AS data_type, 10.2 AS data_value, 'A' AS unit, '2026-07-25 08:00:00' AS recorded_at
    UNION ALL SELECT 'CURRENT', 10.5, 'A', '2026-07-25 10:00:00'
    UNION ALL SELECT 'CURRENT', 9.8, 'A', '2026-07-25 12:00:00'
) t
WHERE d.device_code = 'VFD-002'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- 环境监测-01（ENV-001）24h 连续采集：温度 + 压力，每小时 1 条
INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'TEMPERATURE' AS data_type, 25.3 AS data_value, '°C' AS unit, '2026-07-26 00:00:00' AS recorded_at
    UNION ALL SELECT 'TEMPERATURE', 24.8, '°C', '2026-07-26 01:00:00'
    UNION ALL SELECT 'TEMPERATURE', 23.9, '°C', '2026-07-26 02:00:00'
    UNION ALL SELECT 'TEMPERATURE', 23.2, '°C', '2026-07-26 03:00:00'
    UNION ALL SELECT 'TEMPERATURE', 22.8, '°C', '2026-07-26 04:00:00'
    UNION ALL SELECT 'TEMPERATURE', 23.5, '°C', '2026-07-26 05:00:00'
    UNION ALL SELECT 'TEMPERATURE', 25.1, '°C', '2026-07-26 06:00:00'
    UNION ALL SELECT 'TEMPERATURE', 27.3, '°C', '2026-07-26 07:00:00'
    UNION ALL SELECT 'TEMPERATURE', 29.8, '°C', '2026-07-26 08:00:00'
    UNION ALL SELECT 'TEMPERATURE', 31.5, '°C', '2026-07-26 09:00:00'
    UNION ALL SELECT 'TEMPERATURE', 33.2, '°C', '2026-07-26 10:00:00'
    UNION ALL SELECT 'TEMPERATURE', 34.7, '°C', '2026-07-26 11:00:00'
    UNION ALL SELECT 'TEMPERATURE', 35.9, '°C', '2026-07-26 12:00:00'
    UNION ALL SELECT 'TEMPERATURE', 36.2, '°C', '2026-07-26 13:00:00'
    UNION ALL SELECT 'TEMPERATURE', 35.8, '°C', '2026-07-26 14:00:00'
    UNION ALL SELECT 'TEMPERATURE', 34.5, '°C', '2026-07-26 15:00:00'
    UNION ALL SELECT 'TEMPERATURE', 32.9, '°C', '2026-07-26 16:00:00'
    UNION ALL SELECT 'TEMPERATURE', 31.0, '°C', '2026-07-26 17:00:00'
    UNION ALL SELECT 'TEMPERATURE', 29.2, '°C', '2026-07-26 18:00:00'
    UNION ALL SELECT 'TEMPERATURE', 27.8, '°C', '2026-07-26 19:00:00'
    UNION ALL SELECT 'TEMPERATURE', 26.5, '°C', '2026-07-26 20:00:00'
    UNION ALL SELECT 'TEMPERATURE', 25.9, '°C', '2026-07-26 21:00:00'
    UNION ALL SELECT 'TEMPERATURE', 25.4, '°C', '2026-07-26 22:00:00'
    UNION ALL SELECT 'TEMPERATURE', 25.0, '°C', '2026-07-26 23:00:00'
) t
WHERE d.device_code = 'ENV-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

INSERT INTO `device_data` (`device_id`, `data_type`, `data_value`, `unit`, `recorded_at`)
SELECT d.id, t.data_type, t.data_value, t.unit, t.recorded_at
FROM `device` d
CROSS JOIN (
    SELECT 'PRESSURE' AS data_type, 101.2 AS data_value, 'kPa' AS unit, '2026-07-26 00:00:00' AS recorded_at
    UNION ALL SELECT 'PRESSURE', 101.0, 'kPa', '2026-07-26 01:00:00'
    UNION ALL SELECT 'PRESSURE', 100.8, 'kPa', '2026-07-26 02:00:00'
    UNION ALL SELECT 'PRESSURE', 100.5, 'kPa', '2026-07-26 03:00:00'
    UNION ALL SELECT 'PRESSURE', 100.3, 'kPa', '2026-07-26 04:00:00'
    UNION ALL SELECT 'PRESSURE', 100.6, 'kPa', '2026-07-26 05:00:00'
    UNION ALL SELECT 'PRESSURE', 101.1, 'kPa', '2026-07-26 06:00:00'
    UNION ALL SELECT 'PRESSURE', 101.8, 'kPa', '2026-07-26 07:00:00'
    UNION ALL SELECT 'PRESSURE', 102.5, 'kPa', '2026-07-26 08:00:00'
    UNION ALL SELECT 'PRESSURE', 103.2, 'kPa', '2026-07-26 09:00:00'
    UNION ALL SELECT 'PRESSURE', 103.9, 'kPa', '2026-07-26 10:00:00'
    UNION ALL SELECT 'PRESSURE', 104.3, 'kPa', '2026-07-26 11:00:00'
    UNION ALL SELECT 'PRESSURE', 104.6, 'kPa', '2026-07-26 12:00:00'
    UNION ALL SELECT 'PRESSURE', 104.4, 'kPa', '2026-07-26 13:00:00'
    UNION ALL SELECT 'PRESSURE', 103.8, 'kPa', '2026-07-26 14:00:00'
    UNION ALL SELECT 'PRESSURE', 102.9, 'kPa', '2026-07-26 15:00:00'
    UNION ALL SELECT 'PRESSURE', 102.0, 'kPa', '2026-07-26 16:00:00'
    UNION ALL SELECT 'PRESSURE', 101.3, 'kPa', '2026-07-26 17:00:00'
    UNION ALL SELECT 'PRESSURE', 100.9, 'kPa', '2026-07-26 18:00:00'
    UNION ALL SELECT 'PRESSURE', 100.7, 'kPa', '2026-07-26 19:00:00'
    UNION ALL SELECT 'PRESSURE', 100.8, 'kPa', '2026-07-26 20:00:00'
    UNION ALL SELECT 'PRESSURE', 101.0, 'kPa', '2026-07-26 21:00:00'
    UNION ALL SELECT 'PRESSURE', 101.1, 'kPa', '2026-07-26 22:00:00'
    UNION ALL SELECT 'PRESSURE', 101.2, 'kPa', '2026-07-26 23:00:00'
) t
WHERE d.device_code = 'ENV-001'
  AND NOT EXISTS (SELECT 1 FROM `device_data` dd WHERE dd.device_id = d.id AND dd.data_type = t.data_type AND dd.recorded_at = t.recorded_at);

-- ================================================================
-- 6. 测试 operation_log（7 条）
--    守卫键：(user_id, operation_type, target_type, description)
--    user_id 按 username 解析（仅演示用户，管理员 admin）
-- ================================================================
INSERT INTO `operation_log` (`user_id`, `operation_type`, `target_type`, `target_id`, `description`, `ip_address`)
SELECT u.id, t.operation_type, t.target_type, t.target_id, t.description, t.ip_address
FROM `user` u
JOIN (
    SELECT 'admin' AS username, 'LOGIN' AS operation_type, 'USER' AS target_type, 1 AS target_id, '管理员 admin 登录系统' AS description, '192.168.1.100' AS ip_address
    UNION ALL SELECT 'admin', 'CREATE', 'DEVICE', 1, '创建设备：温控传感器-01', '192.168.1.100'
    UNION ALL SELECT 'admin', 'UPDATE', 'DEVICE', 3, '更新设备状态：温控传感器-03 离线', '192.168.1.100'
    UNION ALL SELECT 'operator01', 'LOGIN', 'USER', 2, '操作员 operator01 登录系统', '192.168.1.101'
    UNION ALL SELECT 'operator01', 'CREATE', 'ALARM', 1, '确认告警：温控传感器-01 过温', '192.168.1.101'
    UNION ALL SELECT 'viewer01', 'LOGIN', 'USER', 3, '观察者 viewer01 登录系统', '192.168.2.1'
    UNION ALL SELECT 'viewer01', 'EXPORT', 'DEVICE', NULL, '导出设备列表 CSV', '192.168.2.1'
) t ON t.username = u.username
WHERE NOT EXISTS (
    SELECT 1 FROM `operation_log` ol
    WHERE ol.user_id = u.id AND ol.operation_type = t.operation_type
      AND ol.target_type = t.target_type AND ol.description = t.description
);

-- ================================================================
-- 7. 站点成员分配（user_site）—— 演示用户归属默认站点（P1-01）
--    守卫键：user_site(user_id, site_id)
--    operator01/02 → 默认站点 OPERATOR；viewer01/02 + user05~20 → 默认站点 VIEWER
--    全局 ADMIN（admin）无需 user_site（系统管理员隐式全站点）
--    安全网：确保 DEFAULT 站点存在（V4 迁移应已创建，此处兜底）
-- ================================================================
INSERT INTO `site` (`site_name`, `site_code`, `description`)
SELECT '默认工厂', 'DEFAULT', '系统默认站点（seed 兜底）'
WHERE NOT EXISTS (SELECT 1 FROM `site` WHERE `site_code` = 'DEFAULT');
INSERT INTO `user_site` (`user_id`, `site_id`, `role_id`)
SELECT u.id, s.id, r.id
FROM `user` u
JOIN `site` s ON s.site_code = 'DEFAULT'
JOIN `role` r ON r.role_code = 'OPERATOR'
WHERE u.username IN ('operator01', 'operator02')
  AND NOT EXISTS (SELECT 1 FROM `user_site` us WHERE us.user_id = u.id AND us.site_id = s.id);

INSERT INTO `user_site` (`user_id`, `site_id`, `role_id`)
SELECT u.id, s.id, r.id
FROM `user` u
JOIN `site` s ON s.site_code = 'DEFAULT'
JOIN `role` r ON r.role_code = 'VIEWER'
WHERE u.username IN ('viewer01','viewer02',
                     'user05','user06','user07','user08','user09','user10',
                     'user11','user12','user13','user14','user15','user16',
                     'user17','user18','user19','user20')
  AND NOT EXISTS (SELECT 1 FROM `user_site` us WHERE us.user_id = u.id AND us.site_id = s.id);
