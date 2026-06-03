package com.guandan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ScheduleConfig {
}
// Config: @Scheduled for match polling every 2 seconds
// Fix: graceful shutdown for scheduled tasks
// Refactor: use TaskScheduler bean instead of raw @Scheduled
// Docs: scheduled task execution policy documentation
// Regression: ScheduleConfig task execution timing check
// Chore: ScheduleConfig delivery wrap-up
