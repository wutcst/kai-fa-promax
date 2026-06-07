<template>
  <div class="record-detail">
    <!-- 详情头部：标题 + 比赛时间 -->
    <div class="detail-header">
      <span class="detail-title">战绩详情</span>
      <span class="detail-time">{{ record.time || '暂无数据' }}</span>
    </div>

    <!-- 参与者战绩表格 -->
    <el-table
        :data="record.participants || []"
        border
        size="small"
        class="participant-table"
        v-loading="!record.participants || record.participants.length === 0"
        element-loading-text="加载中..."
    >
      <el-table-column
          label="玩家"
          prop="nickname"
          width="160"
          align="center"
          :formatter="emptyFormatter"
      />
      <el-table-column
          label="成绩"
          prop="score"
          align="center"
          :formatter="formatScore"
      />
      <!-- 新增：对手信息展示 -->
      <el-table-column
          label="对手"
          align="center"
          width="160"
      >
        <template #default="{ row }">
          <span class="opponent-name">{{ row.opponentName || '未知' }}</span>
        </template>
      </el-table-column>
      <!-- 新增：当局得分展示 -->
      <el-table-column
          label="当局得分"
          align="center"
          width="120"
      >
        <template #default="{ row }">
          <span :class="['round-score', row.roundScore > 0 ? 'score-positive' : row.roundScore < 0 ? 'score-negative' : '']">
            {{ row.roundScore !== undefined ? (row.roundScore > 0 ? '+' : '') + row.roundScore : '--' }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    <!-- 逐轮回放区域 -->
    <div v-if="record.rounds && record.rounds.length > 0" class="round-replay">
      <div class="replay-header">
        <span class="replay-title">逐轮回放</span>
        <span class="replay-hint">共 {{ record.rounds.length }} 轮</span>
      </div>
      <div class="round-tabs">
        <button
            v-for="(round, rIdx) in record.rounds"
            :key="rIdx"
            class="round-tab"
            :class="{ active: activeRound === rIdx }"
            @click="activeRound = rIdx"
        >
          第{{ rIdx + 1 }}轮
        </button>
      </div>
      <div v-if="currentRound" class="round-detail">
        <div class="round-info">
          <span class="info-label">出牌玩家</span>
          <span class="info-value">{{ currentRound.player || '未知' }}</span>
        </div>
        <div class="round-info">
          <span class="info-label">出牌组合</span>
          <span class="info-value">{{ currentRound.combo || '未知' }}</span>
        </div>
        <div v-if="currentRound.cards && currentRound.cards.length > 0" class="round-cards">
          <span class="cards-label">打出牌张：</span>
          <div class="cards-list">
            <span v-for="(card, cIdx) in currentRound.cards" :key="cIdx" class="card-chip">{{ card }}</span>
          </div>
        </div>
        <div v-else-if="currentRound.cards && currentRound.cards.length === 0" class="round-cards">
          <span class="cards-label">打出牌张：</span>
          <span class="no-cards">无牌张数据</span>
        </div>
        <div v-if="currentRound.remark" class="round-remark">
          {{ currentRound.remark }}
        </div>
      </div>
      <div v-else class="round-empty">
        暂无回放数据
      </div>
    </div>
    <!-- 空数据提示 -->
    <div v-if="!record || !record.participants || record.participants.length === 0" class="detail-empty">
      <span class="empty-icon">&#128203;</span>
      <p class="empty-text">暂无对局数据</p>
    </div>
  </div>
</template>

<script setup>
// ========== 联调说明 ==========
// Props 数据契约：父组件需传入 record 对象，包含 { time, participants[], rounds[] }
// API 联调：通过父组件 PersonalHome.vue 中的 getPlayerRecords API 获取数据后传入
// 成绩格式化：formatScore 方法返回 HTML 字符串，需要 v-html 渲染（已在父组件中处理）
// 空数据兜底：当 participants 为空或 record 无数据时，显示空数据提示区域
// 轮次回放：rounds 数组每项需包含 { player, combo, cards[], remark? }
// 样式隔离：使用 scoped 样式，成绩颜色通过 :deep() 穿透覆盖 el-table 默认样式
// ========== 回归验证点 ==========
// [TC-RECORD-001] 传入完整 record 数据 → 正常渲染表格和回放区域
// [TC-RECORD-002] 传入空 participants → 显示"暂无对局数据"兜底提示
// [TC-RECORD-003] 传入空 rounds → 不显示回放区域
// [TC-RECORD-004] 切换轮次标签 → 正确显示对应轮次数据
//
// ========== 手动测试用例 ==========
// [TC-RECORD-MANUAL-001] 【战绩详情展示】传入包含 4 名 participant 的完整 record → 表格正确渲染玩家/成绩/对手/当局得分列
// [TC-RECORD-MANUAL-002] 【战绩详情展示】某行 roundScore > 0 → 显示为 "+分值" 且为绿色
// [TC-RECORD-MANUAL-003] 【战绩详情展示】某行 roundScore < 0 → 显示为 "分值" 且为红色
// [TC-RECORD-MANUAL-004] 【战绩详情展示】roundScore 为 undefined → 显示 "--"
// [TC-RECORD-MANUAL-005] 【战绩详情展示】轮次回放 rounds 数组含 5 轮 → 显示 5 个轮次按钮，点击第 3 轮正确显示该轮数据
// [TC-RECORD-MANUAL-006] 【交互边界】当前轮次 cards 为空数组 → 显示"无牌张数据"兜底文本

import { ref, computed } from 'vue'

// 接收父组件传递的单局战绩数据（增加类型校验和默认值）
const props = defineProps({
  record: {
    type: Object,
    required: true,
    default: () => ({
      time: '',
      participants: [],
      rounds: []
    })
  }
})

// 当前激活的回放轮次
const activeRound = ref(0)

// 当前轮次数据
const currentRound = computed(() => {
  if (!props.record.rounds || props.record.rounds.length === 0) return null
  return props.record.rounds[activeRound.value] || null
})

// 空值格式化（避免表格显示空内容）
const emptyFormatter = ({ row }) => {
  return row.nickname || '未知玩家'
}

// 格式化成绩显示（添加不同颜色样式）
const formatScore = ({ row }) => {
  const classMap = {
    '头游': 'score-first',
    '二游': 'score-second',
    '三游': 'score-third',
    '末游': 'score-last'
  }
  const score = row.score || '未知成绩'
  return `<span class="${classMap[row.score] || ''}">${score}</span>`
}
</script>

<style scoped>
/* 详情容器 */
.record-detail {
  padding: 16px 20px;
  border-top: 1px solid #e5e7eb;
  background-color: #fafafa;
}

/* 详情头部 */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: #666;
}

.detail-title {
  font-weight: 600;
  color: #333;
  font-size: 15px;
}

.detail-time {
  color: #999;
  font-size: 13px;
}

/* 参与者表格 */
.participant-table {
  --el-table-text-color: #333;
  --el-table-header-text-color: #666;
  width: 100%;
  border-radius: 6px;
  overflow: hidden;
}

/* 表格成绩样式（穿透样式） */
:deep(.score-first) {
  color: #67C23A;
  font-weight: bold;
}

:deep(.score-second) {
  color: #409EFF;
  font-weight: bold;
}

:deep(.score-third) {
  color: #E6A23C;
  font-weight: bold;
}

:deep(.score-last) {
  color: #F56C6C;
  font-weight: bold;
}

/* 对手名称样式 */
.opponent-name {
  color: #606266;
  font-size: 13px;
}

/* 当局得分样式 */
.round-score {
  font-weight: 600;
  font-size: 14px;
}

.score-positive {
  color: #67C23A;
}

.score-negative {
  color: #F56C6C;
}

/* 逐轮回放区域 */
.round-replay {
  margin-top: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  background: #fff;
}

.replay-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.replay-title {
  font-weight: 600;
  color: #333;
  font-size: 14px;
}

.replay-hint {
  color: #999;
  font-size: 12px;
}

/* 轮次切换标签 */
.round-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.round-tab {
  padding: 4px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f5f5f5;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.round-tab.active {
  background: #409EFF;
  color: #fff;
  border-color: #409EFF;
}

.round-tab:hover:not(.active) {
  border-color: #409EFF;
  color: #409EFF;
}

/* 轮次详情 */
.round-detail {
  padding: 12px;
  background: #f9f9f9;
  border-radius: 6px;
}

.round-info {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.info-label {
  color: #999;
  flex-shrink: 0;
}

.info-value {
  color: #333;
  font-weight: 500;
}

.round-cards {
  margin-top: 8px;
}

.cards-label {
  font-size: 13px;
  color: #999;
  display: block;
  margin-bottom: 6px;
}

.cards-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.card-chip {
  display: inline-block;
  padding: 3px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
  color: #333;
}

.round-remark {
  margin-top: 8px;
  padding: 8px;
  background: #fff8e1;
  border-radius: 4px;
  font-size: 12px;
  color: #b8860b;
}

.round-empty {
  text-align: center;
  padding: 20px;
  color: #999;
  font-size: 13px;
}

/* 空数据兜底 */
.detail-empty {
  text-align: center;
  padding: 30px 20px;
  color: #999;
}

.empty-icon {
  font-size: 36px;
  display: block;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 14px;
  color: #bbb;
  margin: 0;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .round-tabs {
    gap: 4px;
  }

  .round-tab {
    font-size: 11px;
    padding: 3px 8px;
  }
}
</style>
