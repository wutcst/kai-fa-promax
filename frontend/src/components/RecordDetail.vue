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
    <!-- 空数据提示 -->
    <div v-if="!record || !record.participants || record.participants.length === 0" class="detail-empty">
      <span class="empty-icon">&#128203;</span>
      <p class="empty-text">暂无对局数据</p>
    </div>
  </div>
</template>

<script setup>
import { defineProps } from 'vue'

// 接收父组件传递的单局战绩数据（增加类型校验和默认值）
const props = defineProps({
  record: {
    type: Object,
    required: true,
    default: () => ({
      time: '',
      participants: []
    })
  }
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
}
</style>
