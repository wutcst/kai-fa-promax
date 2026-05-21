CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname VARCHAR(32) NOT NULL,
  avatar_url VARCHAR(255),
  status VARCHAR(20) DEFAULT 'ONLINE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  total_games INT DEFAULT 0,
  win_games INT DEFAULT 0,
  score INT DEFAULT 0
);
-- Room schema: unique constraints and default states
-- Fix: add NOT NULL constraints to critical columns
-- Refactor: reorganize DDL statements with clear sections
-- Docs: schema design notes and migration guide
