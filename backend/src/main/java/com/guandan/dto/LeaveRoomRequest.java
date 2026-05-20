package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 离开房间请求
 */
@Data
public class LeaveRoomRequest {

    private Long userId;

    @NotBlank(message = "房间号不能为空")
    private String roomNo;
}
