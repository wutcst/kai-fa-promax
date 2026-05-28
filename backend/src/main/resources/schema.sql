-- 用户表
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname VARCHAR(32) NOT NULL,
  avatar_url VARCHAR(255),
  phone VARCHAR(20),
  status VARCHAR(20) DEFAULT 'ONLINE',
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
);

-- 用户统计表
CREATE TABLE user_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  total_games INT DEFAULT 0,
  win_games INT DEFAULT 0,
  score INT DEFAULT 0,
  current_level INT DEFAULT 0,
  max_bomb_rank INT DEFAULT 0,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 房间表
CREATE TABLE room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_no VARCHAR(10) NOT NULL UNIQUE,
  status INT DEFAULT 0 COMMENT '0=等待 1=游戏中 2=已结束',
  creator_id BIGINT NOT NULL,
  is_private TINYINT DEFAULT 0,
  level_team_a INT DEFAULT 2,
  level_team_b INT DEFAULT 2,
  trump_suit VARCHAR(10),
  config JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_room_no (room_no),
  INDEX idx_status (status),
  FOREIGN KEY (creator_id) REFERENCES users(id)
);

-- 房间玩家关联表
CREATE TABLE room_player (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  seat_index INT DEFAULT 0,
  is_ready TINYINT DEFAULT 0,
  card_count INT DEFAULT 0,
  FOREIGN KEY (room_id) REFERENCES room(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  UNIQUE KEY uk_room_user (room_id, user_id)
);

-- 游戏记录表
CREATE TABLE game_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  winner_team INT COMMENT '1=A队 2=B队',
  score_a INT DEFAULT 0,
  score_b INT DEFAULT 0,
  started_at DATETIME,
  ended_at DATETIME,
  FOREIGN KEY (room_id) REFERENCES room(id)
);

-- 游戏回合记录表
CREATE TABLE game_round (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  round_number INT NOT NULL,
  winner_team INT,
  tribute_json JSON COMMENT '进贡/还贡记录',
  FOREIGN KEY (game_id) REFERENCES game_record(id)
);

-- 回合玩家明细表
CREATE TABLE game_round_player (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  round_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rank_order INT COMMENT '本回合名次',
  score_change INT DEFAULT 0,
  highlights JSON COMMENT '高光时刻',
  FOREIGN KEY (round_id) REFERENCES game_round(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 操作日志表
CREATE TABLE tb_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  operation_type VARCHAR(50),
  request_url VARCHAR(255),
  request_ip VARCHAR(50),
  response_status INT,
  execution_time BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
