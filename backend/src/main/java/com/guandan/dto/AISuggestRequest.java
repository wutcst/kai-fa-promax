package com.guandan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI出牌建议请求")
public class AISuggestRequest {

    @Schema(description = "当前手牌ID列表")
    private List<Integer> handCards;

    @Schema(description = "上一手打出的牌ID列表")
    private List<Integer> lastPlayedCards;

    @Schema(description = "上一手牌的类型（单张/对子/三张/顺子等）")
    private String lastCardType;

    @Schema(description = "级牌点数 (0-12)")
    private int levelCardRank;
}
