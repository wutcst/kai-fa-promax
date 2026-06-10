package com.guandan.vo;

import lombok.Data;

@Data
public class PlayerStatisticsVO {

    private Integer totalGames;

    private Integer winGames;

    private Double winRate;

    private Integer firstPlaceGames;

    private Double firstPlaceRate;

    private Integer levelCurrent;
}
