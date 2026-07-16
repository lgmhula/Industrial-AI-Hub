-- ============================================================
-- Day 15: MySQL DDL / DML / DQL 综合复习
-- 数据库：reboot（Industrial AI Hub 核心库）
-- 在 OrbStack 容器内执行，或在 IDEA 数据库工具中运行
-- ============================================================

DROP DATABASE IF EXISTS reboot;
CREATE DATABASE reboot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE reboot;

-- 1. DDL: 用户表
CREATE TABLE `user` (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password   VARCHAR(200) NOT NULL COMMENT '密码（BCrypt）',
    email      VARCHAR(100) COMMENT '邮箱',
    phone      VARCHAR(20)  COMMENT '手机号',
    role       ENUM('ADMIN','OPERATOR','VIEWER') NOT NULL DEFAULT 'VIEWER',
    status     TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_status (status)
) COMMENT '用户表';

-- 2. DDL: 设备表（Industrial AI Hub 核心）
CREATE TABLE device (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL COMMENT '设备名称',
    type       VARCHAR(50)  NOT NULL COMMENT '设备类型（传感器/PLC/网关）',
    location   VARCHAR(100) COMMENT '安装位置',
    status     ENUM('ONLINE','OFFLINE','ALARM','MAINTENANCE') NOT NULL DEFAULT 'OFFLINE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status)
) COMMENT '设备表';

-- 3. DML: 插入用户数据
INSERT INTO user (username, password, email, phone, role) VALUES
('admin',    '$2a$10$xxx_admin',    'admin@reboot.dev',    '13800000001', 'ADMIN'),
('zhangsan', '$2a$10$xxx_zhangsan', 'zhangsan@reboot.dev', '13800000002', 'OPERATOR'),
('lisi',     '$2a$10$xxx_lisi',     'lisi@reboot.dev',     '13800000003', 'OPERATOR'),
('wangwu',   '$2a$10$xxx_wangwu',   'wangwu@reboot.dev',   '13800000004', 'VIEWER'),
('zhaoliu',  '$2a$10$xxx_zhaoliu',  'zhaoliu@reboot.dev',  '13800000005', 'VIEWER'),
('sunqi',    '$2a$10$xxx_sunqi',    'sunqi@reboot.dev',    '13800000006', 'OPERATOR'),
('zhouba',   '$2a$10$xxx_zhouba',   'zhouba@reboot.dev',   '13800000007', 'VIEWER'),
('wujiu',    '$2a$10$xxx_wujiu',    'wujiu@reboot.dev',    '13800000008', 'OPERATOR'),
('zhengshi', '$2a$10$xxx_zhengshi', 'zhengshi@reboot.dev', '13800000009', 'VIEWER'),
('test_user','$2a$10$xxx_test',     'test@reboot.dev',     '13800000010', 'VIEWER');

-- 4. DML: 插入设备数据
INSERT INTO device (name, type, location, status) VALUES
('温度传感器-A1', '传感器', '一号车间', 'ONLINE'),
('压力传感器-B2', '传感器', '二号车间', 'ONLINE'),
('西门子PLC-200', 'PLC',    '主控室',   'ONLINE'),
('湿度传感器-C3', '传感器', '仓库',     'ALARM'),
('IoT网关-GW01',  '网关',   '数据中心', 'ONLINE'),
('振动传感器-D4', '传感器', '三号车间', 'MAINTENANCE');

-- 5. DQL 查询练习
SELECT '=== 1. 所有用户 ===' AS '';
SELECT id, username, email, role FROM user;

SELECT '=== 2. 管理员 (WHERE) ===' AS '';
SELECT username, email FROM user WHERE role = 'ADMIN';

SELECT '=== 3. 用户名含 san (LIKE) ===' AS '';
SELECT username, email FROM user WHERE username LIKE '%san%';

SELECT '=== 4. id 3~7 (BETWEEN) ===' AS '';
SELECT id, username FROM user WHERE id BETWEEN 3 AND 7;

SELECT '=== 5. 特定角色 (IN) ===' AS '';
SELECT username, role FROM user WHERE role IN ('ADMIN', 'OPERATOR');

SELECT '=== 6. 最新5用户 (ORDER+LIMIT) ===' AS '';
SELECT username, created_at FROM user ORDER BY created_at DESC LIMIT 5;

SELECT '=== 7. 各角色人数 (GROUP BY) ===' AS '';
SELECT role, COUNT(*) AS cnt FROM user GROUP BY role ORDER BY cnt DESC;

SELECT '=== 8. 在线设备 (JOIN准备) ===' AS '';
SELECT name, type, location FROM device WHERE status = 'ONLINE';

SELECT '=== 9. 设备状态统计 ===' AS '';
SELECT status, COUNT(*) AS cnt FROM device GROUP BY status
UNION ALL SELECT 'TOTAL', COUNT(*) FROM device;

SELECT '=== 10. 全局统计 ===' AS '';
SELECT
    (SELECT COUNT(*) FROM user) AS 用户总数,
    (SELECT COUNT(*) FROM device) AS 设备总数,
    (SELECT COUNT(*) FROM device WHERE status = 'ONLINE') AS 在线设备;

-- 6. 更新与软删除
UPDATE user SET phone = '13900000001' WHERE username = 'admin';
UPDATE user SET status = 0 WHERE username = 'test_user';

SELECT '=== 最终: 用户 ===' AS '';
SELECT id, username, role, status, created_at FROM user ORDER BY id;
SELECT '=== 最终: 设备 ===' AS '';
SELECT id, name, type, status FROM device ORDER BY id;
