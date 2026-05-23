package com.guandan.config;

import com.guandan.service.MatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置
 *
 * 启用 Spring 定时任务支持，用于匹配队列轮询等后台任务。
 *
 * 定时任务说明：
 * - checkMatchQueue：每 3 秒轮询匹配队列，检查是否达到 4 人匹配条件
 *   空值保护：MatchService.checkAndMatch() 内部已处理空队列和不足 4 人的情况
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Resource
    private MatchService matchService;

    /**
     * 定时检查匹配队列
     *
     * 每 3 秒执行一次，检查匹配队列是否达到 4 人。
     * checkAndMatch() 内部有 synchronized 保证并发安全，
     * 空队列或不足 4 人时直接返回，无额外开销。
     */
    @Scheduled(fixedRate = 3000)
    public void checkMatchQueue() {
        try {
            matchService.checkAndMatch();
        } catch (Exception e) {
            log.error("定时检查匹配队列异常", e);
        }
    }
}