<template>
  <div class="personal-home">
    <h1>个人主页</h1>
    <p>欢迎回来，{{ nickname }}</p>
    <div class="stats-card">
      <div class="stat-item">
        <span class="label">总场次</span>
        <span class="value">{{ playerStatistics.totalGames || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="label">胜场</span>
        <span class="value">{{ playerStatistics.winGames || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="label">胜率</span>
        <span class="value">{{ winRate }}%</span>
      </div>
      <div class="stat-item">
        <span class="label">当前等级</span>
        <span class="value">Lv.{{ playerStatistics.levelCurrent || 1 }}</span>
      </div>
    </div>

    <!-- 胜率图表看板 -->
    <div class="chart-section" v-if="hasChartData">
      <h2>胜率图表</h2>
      <div class="chart-container">
        <div class="chart-bar-wrapper">
          <div class="chart-label">胜率</div>
          <div class="chart-track">
            <div class="chart-fill" :style="{ width: chartPercent + '%' }"></div>
          </div>
          <div class="chart-value">{{ chartPercent }}%</div>
        </div>
        <div class="chart-bar-wrapper">
          <div class="chart-label">总局数</div>
          <div class="chart-track">
            <div class="chart-fill total-fill" :style="{ width: totalPercent + '%' }"></div>
          </div>
          <div class="chart-value">{{ playerStatistics.totalGames }}</div>
        </div>
      </div>
    </div>
    <div v-else class="chart-section">
      <h2>胜率图表</h2>
      <div class="empty-chart-hint">暂无对局数据，开始游戏后将展示胜率统计</div>
    </div>

    <!-- 对局历史 -->
    <div class="records-section">
      <h2>对局历史</h2>
      <div v-if="isRecordsLoading" class="loading-state">
        <span class="loading-text">正在加载战绩记录...</span>
      </div>
      <div v-else-if="recordList.length === 0" class="empty-state">
        <div class="empty-icon">📋</div>
        <span class="empty-text">暂无对局记录</span>
        <span class="empty-hint">开始游戏后，您的战绩将会展示在这里</span>
      </div>
      <div v-else v-for="(record, index) in recordList" :key="record.id" class="record-item">
        <div class="record-main" @click="toggleDetail(index)">
          <span class="record-time">{{ formatTime(record.gameTime) }}</span>
          <span class="record-result" :class="getResultClass(record.result)">{{ formatResult(record.result) }}</span>
        </div>
        <transition name="slide">
          <div v-if="record.showDetail" class="record-detail-wrapper">
            <RecordDetail :record="record" />
          </div>
        </transition>
      </div>
    </div>

    <div class="action-bar">
      <el-button @click="goBack">返回大厅</el-button>
      <el-button type="primary" @click="refreshData">刷新数据</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPlayerStatistics, getPlayerRecords } from '@/api/game'
import RecordDetail from '@/components/RecordDetail.vue'

const router = useRouter()
const nickname = ref(localStorage.getItem('nickname') || '玩家')

const playerStatistics = ref({
  totalGames: 0,
  winGames: 0,
  winRate: 0,
  levelCurrent: 1
})

const recordList = ref([])
const isRecordsLoading = ref(false)

const winRate = computed(() => {
  if (!playerStatistics.value.totalGames) return 0
  return playerStatistics.value.winRate
    ? playerStatistics.value.winRate.toFixed(1)
    : Math.round((playerStatistics.value.winGames / playerStatistics.value.totalGames) * 100)
})

// 胜率图表百分比
const chartPercent = computed(() => {
  const rate = parseFloat(playerStatistics.value.winRate) || 0
  return Math.min(rate, 100).toFixed(1)
})

// 总局数图表百分比 (按100场为基准)
const totalPercent = computed(() => {
  const total = playerStatistics.value.totalGames || 0
  return Math.min((total / 100) * 100, 100)
})

// 是否有图表数据
const hasChartData = computed(() => {
  return playerStatistics.value.totalGames > 0
})

const fetchPlayerStatistics = async () => {
  try {
    const response = await getPlayerStatistics()
    console.log('获取玩家统计信息:', response)
    playerStatistics.value = response || { totalGames: 0, winGames: 0, winRate: 0, levelCurrent: 1 }
  } catch (error) {
    console.error('获取玩家统计信息失败：', error)
    ElMessage.error('获取统计信息失败')
  }
}

const fetchPlayerRecords = async () => {
  isRecordsLoading.value = true
  try {
    const response = await getPlayerRecords({ page: 1, pageSize: 20 })
    const { records } = response || { records: [] }
    recordList.value = (records || []).map(record => ({
      ...record,
      showDetail: false
    }))
  } catch (error) {
    console.error('获取战绩记录失败：', error)
    ElMessage.error('获取战绩记录失败')
    recordList.value = []
  } finally {
    isRecordsLoading.value = false
  }
}

onMounted(() => {
  fetchPlayerStatistics()
  fetchPlayerRecords()
})

const refreshData = () => {
  fetchPlayerStatistics()
  fetchPlayerRecords()
  ElMessage.success('数据已刷新')
}

const toggleDetail = (index) => {
  recordList.value[index].showDetail = !recordList.value[index].showDetail
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatResult = (result) => {
  return result === 1 ? '头游' : '末游'
}

const getResultClass = (res) => {
  const map = { '头游': 'res-gold', '二游': 'res-blue', '三游': 'res-orange', '末游': 'res-gray' }
  return map[res] || ''
}

const goBack = () => router.push('/lobby')
</script>

<style scoped>
.personal-home {
  padding: 30px;
  max-width: 800px;
  margin: 0 auto;
}

.stats-card {
  display: flex;
  gap: 20px;
  margin: 20px 0;
  padding: 20px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  border-radius: 12px;
  border: 2px solid #c19a6b;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.label { font-size: 14px; color: #666; display: block; }
.value { font-size: 24px; font-weight: bold; color: #8B4513; display: block; margin-top: 5px; }

/* 胜率图表看板 */
.chart-section {
  margin: 20px 0;
}

.chart-section h2 {
  color: #8B4513;
  border-bottom: 1px solid #c19a6b;
  padding-bottom: 10px;
}

.chart-container {
  background: linear-gradient(to bottom, #fff8e1, #ffe0b2);
  border: 1px solid #c19a6b;
  border-radius: 12px;
  padding: 20px;
  margin-top: 10px;
}

.chart-bar-wrapper {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 15px;
}

.chart-bar-wrapper:last-child {
  margin-bottom: 0;
}

.chart-label {
  width: 60px;
  font-size: 14px;
  color: #6d4c41;
  text-align: right;
  flex-shrink: 0;
}

.chart-track {
  flex: 1;
  height: 24px;
  background: #e8d4b8;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.1);
}

.chart-fill {
  height: 100%;
  background: linear-gradient(to right, #D4AF37, #FFD700);
  border-radius: 12px;
  transition: width 0.8s ease;
  min-width: 4px;
}

.total-fill {
  background: linear-gradient(to right, #8B4513, #A1887F);
}

.chart-value {
  width: 50px;
  font-size: 14px;
  font-weight: bold;
  color: #8B4513;
  text-align: left;
  flex-shrink: 0;
}

/* 对局历史 */
.records-section {
  margin: 20px 0;
}

.records-section h2 {
  color: #8B4513;
  border-bottom: 1px solid #c19a6b;
  padding-bottom: 10px;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 15px;
}

.empty-text {
  display: block;
  font-size: 16px;
  color: #8B4513;
  margin-bottom: 8px;
}

.empty-hint {
  display: block;
  font-size: 13px;
  color: #999;
}

.empty-chart-hint {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 14px;
}

.loading-state {
  text-align: center;
  padding: 40px;
}

.loading-text {
  color: #8B4513;
  font-size: 14px;
}

.loading-text::after {
  content: '';
  display: inline-block;
  width: 6px;
  margin-left: 4px;
  animation: dotAnimation 1.4s infinite;
}

@keyframes dotAnimation {
  0%, 20% { content: '.'; }
  40% { content: '..'; }
  60%, 100% { content: '...'; }
}

.record-item {
  background: linear-gradient(to bottom, #fff8e1, #ffe0b2);
  border: 1px solid #c19a6b;
  border-radius: 12px;
  margin-bottom: 10px;
  overflow: hidden;
}

.record-main {
  padding: 15px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}

.record-time {
  font-size: 16px;
  color: #6d4c41;
}

.record-result {
  font-weight: bold;
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 16px;
}

.res-gold {
  color: #fff;
  background: linear-gradient(to right, #D4AF37, #FFD700);
}

.res-blue {
  color: #fff;
  background: linear-gradient(to right, #1A237E, #3949AB);
}

.res-orange {
  color: #fff;
  background: linear-gradient(to right, #E65100, #FF8A65);
}

.res-gray {
  color: #fff;
  background: linear-gradient(to right, #424242, #757575);
}

.record-detail-wrapper {
  border-top: 1px dashed #c19a6b;
}

.action-bar {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 20px;
}

/* 过渡动画 */
.slide-enter-from {
  max-height: 0;
  opacity: 0;
}

.slide-enter-active {
  max-height: 500px;
  opacity: 1;
  transition: all 0.3s ease;
}

.slide-leave-to {
  max-height: 0;
  opacity: 0;
}

.slide-leave-active {
  max-height: 500px;
  opacity: 1;
  transition: all 0.3s ease;
}
</style>
