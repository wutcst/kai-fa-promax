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
 * <p>启用 Spring 定时任务支持，用于匹配队列轮询等后台任务。</p>
 *
 * <h3>定时任务说明</h3>
 * <ul>
 *   <li><b>pollMatchQueue</b>：每 3 秒轮询匹配队列，检查是否达到 4 人匹配条件</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>{@link MatchService#checkAndMatch()} 内部使用 {@code synchronized} 保证并发安全，
 * 空队列或不足 4 人时直接返回，无额外开销。</p>
 *
 * <h3>职责边界</h3>
 * <p>仅负责触发匹配检测，不参与匹配业务逻辑。匹配业务由 {@link MatchService} 完成。</p>
 *
 * @author 何涛
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Resource
    private MatchService matchService;

    /**
     * 定时轮询匹配队列，检查是否达到匹配条件
     *
     * 每 3 秒执行一次，检查匹配队列是否已达到 4 人。
     * checkAndMatch() 内部有 synchronized 保证并发安全，
     * 空队列或不足 4 人时直接返回，无额外开销。
     *
     * 职责边界：仅负责触发匹配检测，不参与匹配业务逻辑。
     *
     * ── 匹配队列配置说明 ──────────────────────────────────
     * - 轮询间隔: 3000ms（3秒），由 @Scheduled(fixedRate = 3000) 配置
     * - 线程安全: checkAndMatch() 内部使用 synchronized 关键字
     * - 空队列处理: 队列为空或不足 4 人时直接返回，无额外开销
     * - 异常兜底: catch Exception 记录错误日志，不影响下次轮询
     * - 职责分离: 本类只负责调度，匹配业务逻辑委托给 MatchService
     * ─────────────────────────────────────────────────────
     *
     * ── 阶段提交说明 ──────────────────────────────────────
     * 本配置与 MatchController、MatchService 组成匹配模块的完整提交：
     * - ScheduleConfig : 定时调度层
     * - MatchController : API 入口层
     * - MatchService : 匹配业务逻辑层
     * 三者在同一阶段提交，确保匹配功能完整可用。
     * ─────────────────────────────────────────────────────
     *
     * ## 回归验证点
     * - [TC-MATCH-006] 匹配队列满4人 → checkAndMatch 自动创建房间
     * - [TC-MATCH-009] 多人同时匹配 → checkAndMatch 内部有 synchronized 锁
     */
    @Scheduled(fixedRate = 3000)
    public void pollMatchQueue() {
        try {
            matchService.checkAndMatch();
        } catch (Exception e) {
            log.error("定时检查匹配队列异常", e);
        }
    }
}