-- ============================================================================
-- 数据库初始化脚本
-- 目标数据库：guandan（掼蛋游戏）
-- 字符集：utf8mb4（支持完整 Unicode，包括 Emoji）
-- 排序规则：utf8mb4_general_ci
-- ============================================================================
--
-- ── 维护说明 ──────────────────────────────────────────────
-- 1. 所有 DDL 变更必须同时更新本文件（单数据源真相）
-- 2. 字段默认值统一在 DDL 中定义，实体类不重复设置
-- 3. 索引命名规范：
--    - 主键：PRIMARY
--    - 唯一索引：uk_${table}_${column}
--    - 普通索引：idx_${table}_${column}
-- 4. 所有表必须包含 create_time / update_time 时间戳字段
-- 5. 外键约束仅在建表脚本中体现逻辑关系，不使用物理 FOREIGN KEY
--    关联一致性由应用层保证
-- 6. 分表场景前缀：user_stats_2026（按年月后缀）
-- ─────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS guandan CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE guandan;

-- 用户表
-- 存储用户账号信息，密码使用 BCrypt 加密
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（6位纯数字）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(50) NOT NULL COMMENT '昵称',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    online TINYINT DEFAULT 0 COMMENT '在线状态：0-离线，1-在线',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_username (username),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 扩展用户字段（版本追加）
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除';
ALTER TABLE `user` MODIFY COLUMN avatar LONGTEXT COMMENT '头像（支持base64或URL）';

-- 用户统计表
-- 存储用户游戏统计数据，与用户表一对一关系
CREATE TABLE IF NOT EXISTS user_stats (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY COMMENT '用户ID',
    total_games INT DEFAULT 0 COMMENT '总场次',
    win_games INT DEFAULT 0 COMMENT '获胜场次',
    level_current INT DEFAULT 2 COMMENT '当前级别',
    max_bomb_rank VARCHAR(20) DEFAULT NULL COMMENT '最大炸弹等级',
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户统计表';

-- 房间表
-- 存储房间配置和状态信息，6位唯一房间号
CREATE TABLE IF NOT EXISTS room (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_no VARCHAR(6) NOT NULL UNIQUE COMMENT '房间号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-等待，1-游戏中，2-结束',
    creator_id BIGINT UNSIGNED NOT NULL COMMENT '创建者ID',
    level_team_a INT DEFAULT 2 COMMENT 'A队当前级别（掼蛋从2开始）',
    level_team_b INT DEFAULT 2 COMMENT 'B队当前级别',
    current_trump_suit VARCHAR(20) DEFAULT NULL COMMENT '当前主牌花色',
    next_tribute_state VARCHAR(20) DEFAULT NULL COMMENT '下局进贡状态',
    is_private TINYINT(1) DEFAULT 0 COMMENT '是否私密房间',
    config VARCHAR(500) DEFAULT NULL COMMENT '房间配置JSON',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_room_no (room_no),
    INDEX idx_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='房间表';
-- 房间状态 CHECK 约束：仅允许 0（等待）、1（游戏中）、2（结束）
ALTER TABLE room ADD CONSTRAINT IF NOT EXISTS chk_room_status CHECK (status IN (0, 1, 2));
-- 房间号格式 CHECK 约束：6位数字
ALTER TABLE room ADD CONSTRAINT IF NOT EXISTS chk_room_no_format CHECK (room_no REGEXP '^[0-9]{6}$');

-- 房间玩家关联表
-- 每个房间最多4名玩家，座位号0-3，同一用户不可重复加入同房间
CREATE TABLE IF NOT EXISTS room_player (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT UNSIGNED NOT NULL COMMENT '房间ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    seat_index INT NOT NULL COMMENT '座位号',
    is_ready TINYINT DEFAULT 0 COMMENT '是否准备：0-未准备，1-已准备',
    card_count INT DEFAULT 0 COMMENT '手牌数量',
    update_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_room_user (room_id, user_id),
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='房间玩家表';
-- 座位号 CHECK：只能在 0-3 范围内
ALTER TABLE room_player ADD CONSTRAINT IF NOT EXISTS chk_seat_index CHECK (seat_index >= 0 AND seat_index <= 3);
-- 准备状态 CHECK：仅允许 0 或 1
ALTER TABLE room_player ADD CONSTRAINT IF NOT EXISTS chk_is_ready CHECK (is_ready IN (0, 1));
-- 手牌数 CHECK：0-27
ALTER TABLE room_player ADD CONSTRAINT IF NOT EXISTS chk_card_count CHECK (card_count >= 0 AND card_count <= 27);
-- room_id + user_id 唯一约束已在 DDL 中定义

-- 游戏记录表
-- 记录每局游戏的概要信息
CREATE TABLE IF NOT EXISTS game_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT UNSIGNED NOT NULL COMMENT '房间ID',
    winner_id BIGINT UNSIGNED DEFAULT NULL COMMENT '获胜者ID',
    score INT NOT NULL DEFAULT 0 COMMENT '分数',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='游戏记录表';

-- 游戏回合表
-- 存储每个回合的详细数据，包括进贡记录
CREATE TABLE IF NOT EXISTS game_round (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT UNSIGNED NOT NULL COMMENT '房间ID',
    round_number INT NOT NULL COMMENT '回合数',
    winner_team TINYINT COMMENT '获胜队伍：1-A队，2-B队',
    start_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
    end_time DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    tribute_record VARCHAR(500) DEFAULT NULL COMMENT '进贡记录JSON',
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='游戏回合表';

-- 回合玩家关联表
-- 记录每回合各玩家的排名和得分变化
CREATE TABLE IF NOT EXISTS game_round_player (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    round_id BIGINT UNSIGNED NOT NULL COMMENT '回合ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    seat_index INT NOT NULL COMMENT '座位号',
    rank_order TINYINT COMMENT '排名：1-4',
    score_change INT DEFAULT 0 COMMENT '分数变化',
    highlights VARCHAR(500) DEFAULT NULL COMMENT '高光记录JSON',
    INDEX idx_round_id (round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回合玩家表';

-- 操作日志表
-- 记录用户关键操作的审计日志
CREATE TABLE IF NOT EXISTS tb_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    operation_type VARCHAR(50) DEFAULT NULL COMMENT '操作类型',
    operation_module VARCHAR(50) DEFAULT NULL COMMENT '操作模块',
    operation_desc VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
    target_type VARCHAR(50) DEFAULT NULL COMMENT '目标类型',
    target_id BIGINT UNSIGNED DEFAULT NULL COMMENT '目标ID',
    request_ip VARCHAR(50) DEFAULT NULL COMMENT '请求IP',
    request_method VARCHAR(10) DEFAULT NULL COMMENT '请求方法',
    request_url VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
    response_status INT DEFAULT NULL COMMENT '响应状态',
    is_success TINYINT DEFAULT NULL COMMENT '是否成功：0-失败，1-成功',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    execute_time DATETIME(3) DEFAULT NULL COMMENT '执行时间',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';
