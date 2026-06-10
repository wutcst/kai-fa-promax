package com.guandan.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlayerGameRecordVO {

    private Long id;

    private Long userId;

    private String opponentNames;

    private Integer result;

    private Integer score;

    private LocalDateTime gameTime;

    private Integer duration;
}
