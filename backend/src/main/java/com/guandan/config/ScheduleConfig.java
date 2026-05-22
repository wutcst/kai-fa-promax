package com.guandan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 *
 * 启用 Spring 定时任务支持，用于匹配队列轮询等后台任务。
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
}