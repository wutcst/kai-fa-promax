package com.guandan.dto;

import lombok.Data;

/**
 * 创建游戏/房间请求
 */
@Data
public class NewGameRequest {

    private Long userId;

    private Boolean isPrivate;

    private String config;
}
