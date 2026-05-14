CREATE DATABASE IF NOT EXISTS guandan CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE guandan;

-- 用户表
-- 存储用户账号信息，密码使用 BCrypt 加密
CREATE TABLE `user` (
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

-- 用户统计表
-- 存储用户游戏统计数据，与用户表一对一关系
CREATE TABLE user_stats (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY COMMENT '用户ID',
    total_games INT DEFAULT 0 COMMENT '总场次',
    win_games INT DEFAULT 0 COMMENT '获胜场次',
    level_current INT DEFAULT 2 COMMENT '当前级别',
    max_bomb_rank VARCHAR(20) DEFAULT NULL COMMENT '最大炸弹等级',
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户统计表';

-- 房间表
CREATE TABLE room (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_no VARCHAR(6) NOT NULL UNIQUE COMMENT '房间号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-等待，1-游戏中，2-结束',
    creator_id BIGINT UNSIGNED NOT NULL COMMENT '创建者ID',
    create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_room_no (room_no),
    INDEX idx_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='房间表';

-- 房间玩家关联表
CREATE TABLE room_player (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT UNSIGNED NOT NULL COMMENT '房间ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    seat_index INT NOT NULL COMMENT '座位号',
    is_ready TINYINT DEFAULT 0 COMMENT '是否准备',
    card_count INT DEFAULT 0 COMMENT '手牌数量',
    update_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_room_user (room_id, user_id),
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='房间玩家表';
