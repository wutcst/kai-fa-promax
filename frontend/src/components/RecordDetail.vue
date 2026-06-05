<template>
  <div class="record-detail">
    <!-- 详情头部：标题 + 比赛时间 -->
    <div class="detail-header">
      <span class="detail-title">单局详细战绩</span>
      <span class="detail-time">比赛时间：{{ record.time || '暂无数据' }}</span>
    </div>

    <!-- 参与者战绩表格 -->
    <el-table
        :data="record.participants || []"
        border
        size="small"
        class="participant-table"
        v-loading="!record.participants || record.participants.length === 0"
        element-loading-text="暂无参与者数据"
    >
      <el-table-column
          label="参与者昵称"
          prop="nickname"
          width="200"
          align="center"
          :formatter="emptyFormatter"
      />
      <el-table-column
          label="比赛成绩"
          prop="score"
          align="center"
          :formatter="formatScore"
      />
    </el-table>
    <!-- 空数据兜底 -->
    <div v-if="!record || !record.participants || record.participants.length === 0" class="detail-empty">
      暂无参与者数据
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
  padding: 20px;
  border-top: 1px solid #e5e7eb;
  background-color: #fff;
}

/* 详情头部 */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-size: 16px;
  color: #666;
}

.detail-title {
  font-weight: bold;
  color: #333;
}

/* 参与者表格 */
.participant-table {
  --el-table-text-color: #333;
  --el-table-header-text-color: #666;
  width: 100%;
}

/* 表格成绩样式（穿透样式） */
:deep(.score-first) {
  color: #67C23A; /* 头游-绿色 */
  font-weight: bold;
}

:deep(.score-second) {
  color: #409EFF; /* 二游-蓝色 */
  font-weight: bold;
}

:deep(.score-third) {
  color: #E6A23C; /* 三游-橙色 */
  font-weight: bold;
}

:deep(.score-last) {
  color: #F56C6C; /* 末游-红色 */
  font-weight: bold;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }
}

/* 空数据兜底 */
.detail-empty {
  text-align: center;
  padding: 20px;
  color: #999;
  font-size: 14px;
}
</style>
