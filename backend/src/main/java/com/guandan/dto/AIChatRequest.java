package com.guandan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI聊天请求")
public class AIChatRequest {

    @Schema(description = "用户消息", required = true)
    private String message;
}
