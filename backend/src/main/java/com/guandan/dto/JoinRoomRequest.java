package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 加入房间请求
 */
@Data
public class JoinRoomRequest {

    /**
     * 房间号（6位数字）
     */
    @NotBlank(message = "房间号不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "房间号必须是6位数字")
    private String roomNo;
}
// DTO: validation annotations for join room request
