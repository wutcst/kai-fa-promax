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
