<template>
  <div class="table-container">
    <div class="table-header">
      <div class="vintage-back" @click="goBack">
        <div class="back-circle">
          <div class="back-arrow"></div>
        </div>
        <span>返回大厅</span>
      </div>
      <div class="title-ribbon">
        <h2 class="page-title">个人主页</h2>
      </div>
      <div class="user-currency">
        <div class="gold-coin"></div>
        <span class="coin-count">积分：8888</span>
      </div>
    </div>

    <div class="main-body">
      <div class="tab-sidebar">
        <div
            v-for="nav in navList"
            :key="nav.id"
            class="poker-tab"
            :class="{ active: activeNav === nav.id }"
            @click="activeNav = nav.id"
        >
          <component :is="nav.icon" class="tab-icon" />
          <span>{{ nav.name }}</span>
        </div>
      </div>

      <div class="game-panel">
        <div class="rivet lt"></div><div class="rivet rt"></div>
        <div class="rivet lb"></div><div class="rivet rb"></div>

        <div v-if="activeNav === 'profile'" class="profile-layout">
          <div class="avatar-module">
            <div class="avatar-frame">
              <div class="avatar-main" :style="{ backgroundColor: userInfo.avatarBg }">
                {{ userInfo.avatarText }}
              </div>
              <div class="rank-badge">LV.MAX</div>
            </div>
            <p class="nickname-display">{{ userInfo.nickname }}</p>
          </div>

          <div class="info-list">
            <div class="info-cell">
              <span class="cell-label">账号</span>
              <span class="cell-value">{{ userInfo.account }}</span>
            </div>
            <div class="info-cell">
              <span class="cell-label">姓名</span>
              <span class="cell-value">{{ userInfo.nickname }}</span>
            </div>
            <div class="info-cell">
              <span class="cell-label">密码</span>
              <span class="cell-value">••••••••</span>
            </div>
            <div class="action-footer">
              <button class="wood-btn" @click="showChangePassword">修改密码</button>
            </div>
          </div>
        </div>

        <div v-else-if="activeNav === 'recordStats'" class="record-layout">
          <div class="record-header">
            <h3 class="panel-title">个人战绩</h3>
            <span class="record-count" v-if="totalRecords > 0">共 {{ totalRecords }} 条</span>
            <span class="record-count" v-else>暂无记录</span>
          </div>
          <!-- 胜率概览条 -->
          <div class="winrate-bar" v-if="playerStatistics.totalGames > 0">
            <div class="winrate-item">
              <span class="winrate-label">总场次</span>
              <span class="winrate-value">{{ playerStatistics.totalGames }}</span>
            </div>
            <div class="winrate-item">
              <span class="winrate-label">胜场</span>
              <span class="winrate-value">{{ playerStatistics.winGames }}</span>
            </div>
            <div class="winrate-item">
              <span class="winrate-label">胜率</span>
              <span class="winrate-value highlight">{{ winRate }}%</span>
            </div>
          </div>
          <!-- 筛选条件 -->
          <div class="filter-bar">
            <div class="filter-group">
              <span class="filter-label">时间范围</span>
              <select v-model="filterTimeRange" class="filter-select" @change="onFilterChange">
                <option value="all">全部时间</option>
                <option value="today">今天</option>
                <option value="week">本周</option>
                <option value="month">本月</option>
                <option value="custom">自定义</option>
              </select>
            </div>
            <div class="filter-group" v-if="filterTimeRange === 'custom'">
              <input type="date" v-model="filterStartDate" class="filter-input" @change="onFilterChange" />
              <span class="filter-sep">至</span>
              <input type="date" v-model="filterEndDate" class="filter-input" @change="onFilterChange" />
            </div>
            <div class="filter-group">
              <span class="filter-label">结果筛选</span>
              <select v-model="filterResult" class="filter-select" @change="onFilterChange">
                <option value="all">全部结果</option>
                <option value="1">头游</option>
                <option value="2">二游</option>
                <option value="3">三游</option>
                <option value="4">末游</option>
              </select>
            </div>
            <button class="filter-reset" @click="resetFilters">重置</button>
          </div>
          <!-- 分页战绩列表 -->
          <div class="record-scrollview">
            <div v-for="(record, index) in recordList" :key="record.id" class="record-strip">
              <div class="strip-main" @click="toggleDetail(index)">
                <div class="strip-left">
                  <span class="strip-time">{{ formatTime(record.gameTime) }}</span>
                </div>
                <div class="strip-right">
                  <span class="strip-res" :class="getResultClass(formatResult(record.result))">{{ formatResult(record.result) }}</span>
                  <div class="fold-icon" :class="{ 'is-active': record.showDetail }"></div>
                </div>
              </div>

              <transition name="slide">
                <div v-if="record.showDetail" class="strip-detail">
                  <div class="detail-hint">
                    <span class="hint-label">战绩详情</span>
                    <span class="hint-value">展开查看本局完整数据</span>
                  </div>
                  <div class="table-wrapper">
                    <table class="poker-table">
                      <thead>
                      <tr><th>游戏ID</th><th>分数</th></tr>
                      </thead>
                      <tbody>
                      <tr>
                        <td>{{ record.id }}</td>
                        <td>{{ record.score }}</td>
                      </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </transition>
            </div>
          </div>
          <!-- 分页控制 -->
          <div class="pagination-bar" v-if="totalRecords > pageSize">
            <button class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
            <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
            <button class="page-btn" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
          </div>
        </div>

        <div v-else-if="activeNav === 'gameStats'" class="stats-layout">
          <h3 class="panel-title">对局统计</h3>
          <div class="stats-grid">
            <div class="stat-card">
              <div class="stat-label">总局数</div>
              <div class="stat-value">{{ playerStatistics.totalGames }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">头游局数</div>
              <div class="stat-value">{{ playerStatistics.winGames }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">头游率</div>
              <div class="stat-value">{{ playerStatistics.winRate }}%</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">当前等级</div>
              <div class="stat-value">Lv.{{ playerStatistics.levelCurrent }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ========== 联调说明 ==========
// 1. API 接口：getPlayerStatistics - 获取玩家统计信息（总局数/胜场/胜率/等级）
// 2. API 接口：getPlayerRecords - 获取玩家战绩列表（支持分页和时间范围/结果筛选）
// 3. 分页参数：page（当前页码） + pageSize（每页条数，默认10）
// 4. 筛选参数：timeRange(all/today/week/month/custom) + result(all/1/2/3/4)
// 5. 自定义时间范围：startDate + endDate（格式 yyyy-MM-dd）
// 6. 返回结构：Page 对象 { records: [], total: number }
// 7. 战绩详情组件：通过 RecordDetail.vue 嵌入，传入 record 对象
// 8. 胜率计算：优先使用后端返回的 winRate 字段，不存在时前端计算 winGames/totalGames
//
// 联调异常处理：
// - getPlayerStatistics 失败 → 控制台错误日志，playerStatistics 保持默认值
// - getPlayerRecords 失败 → 控制台错误日志，recordList 保持为空
// - records 或 total 为空 → 显示"暂无记录"文字，不渲染列表
// - 分页超出范围 → changePage 前置校验阻止无效请求
// ========== 回归验证点 ==========
// [TC-PERSONAL-001] 页面加载 → 自动调用 getPlayerStatistics 和 getPlayerRecords
// [TC-PERSONAL-002] 筛选条件变更 → 重置到第1页重新请求
// [TC-PERSONAL-003] 分页切换 → 请求对应页码数据
// [TC-PERSONAL-004] 重置筛选 → 清空所有条件并重新请求
// [TC-PERSONAL-005] API 异常 → 控制台错误，页面不崩溃

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// 1. 确保图标正确引入（单独引入方式）
import { User, PieChart, Calendar } from '@element-plus/icons-vue'
import { getPlayerStatistics, getPlayerRecords } from '@/api/game'

const router = useRouter()
const activeNav = ref('profile')
const navList = [
  { id: 'profile', name: '个人资料', icon: User },
  { id: 'gameStats', name: '对局统计', icon: PieChart },
  { id: 'recordStats', name: '战绩统计', icon: Calendar }
]

const userInfo = ref({
  nickname: localStorage.getItem('nickname') || '默认玩家',
  account: localStorage.getItem('username') || '000000',
  avatarBg: '#7b1113',
  avatarText: '玩'
})

// 玩家统计信息
const playerStatistics = ref({
  totalGames: 0,
  winGames: 0,
  winRate: 0,
  levelCurrent: 1
})

// 玩家战绩记录
const recordList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const totalRecords = ref(0)

// 筛选条件（拆分到独立状态块）
const filterTimeRange = ref('all')
const filterResult = ref('all')
const filterStartDate = ref('')
const filterEndDate = ref('')

// ===== 计算属性 =====

// 计算胜率
const winRate = computed(() => {
  if (!playerStatistics.value.totalGames) return 0
  return playerStatistics.value.winRate
    ? playerStatistics.value.winRate
    : Math.round((playerStatistics.value.winGames / playerStatistics.value.totalGames) * 100)
})

// 计算总页数
const totalPages = computed(() => {
  return Math.max(1, Math.ceil(totalRecords.value / pageSize.value))
})

// ===== 数据请求逻辑 =====

// 获取玩家统计信息
const fetchPlayerStatistics = async () => {
  try {
    const response = await getPlayerStatistics()
    console.log('获取玩家统计信息:', response)
    playerStatistics.value = response
  } catch (error) {
    console.error('获取玩家统计信息失败：', error)
  }
}

// 获取玩家战绩记录（支持筛选）
const fetchPlayerRecords = async (page = 1) => {
  try {
    console.log('开始获取玩家战绩记录...')
    const params = buildQueryParams(page)

    const response = await getPlayerRecords(params)
    console.log('API响应:', response)

    // response已经是Page对象，直接使用
    const { records, total } = response
    console.log('records:', records)
    console.log('total:', total)
    recordList.value = (records || []).map(record => ({
      ...record,
      showDetail: false
    }))
    totalRecords.value = total || 0
    currentPage.value = page
    console.log('recordList.value:', recordList.value)
  } catch (error) {
    console.error('获取玩家战绩记录失败：', error)
  }
}

// ===== 筛选逻辑 =====

// 构建查询参数
const buildQueryParams = (page) => {
  const params = { page, pageSize: pageSize.value }

  // 时间范围筛选
  if (filterTimeRange.value !== 'all') {
    if (filterTimeRange.value === 'custom') {
      if (filterStartDate.value) params.startDate = filterStartDate.value
      if (filterEndDate.value) params.endDate = filterEndDate.value
    } else {
      params.timeRange = filterTimeRange.value
    }
  }

  // 结果筛选
  if (filterResult.value !== 'all') {
    params.result = filterResult.value
  }

  return params
}

// 筛选条件变更（重置到第一页）
const onFilterChange = () => {
  currentPage.value = 1
  fetchPlayerRecords(1)
}

// 重置筛选条件
const resetFilters = () => {
  filterTimeRange.value = 'all'
  filterResult.value = 'all'
  filterStartDate.value = ''
  filterEndDate.value = ''
  onFilterChange()
}

// ===== 分页逻辑 =====

// 分页切换
const changePage = (page) => {
  if (page < 1 || page > totalPages.value) return
  fetchPlayerRecords(page)
}

// 页面加载时获取数据
onMounted(() => {
  fetchPlayerStatistics()
  fetchPlayerRecords()
})

const toggleDetail = (index) => recordList.value[index].showDetail = !recordList.value[index].showDetail

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
const showChangePassword = () => ElMessage.success('请重新设定您的密语')
</script>

<style scoped>
/* 字体部分 */
@font-face {
  font-family: "ChillRoundGothic";
  src:
      url('@/assets/fonts/ChillRoundGothic_Heavy.ttf') format('truetype'),
      local('Microsoft YaHei'),
      local('SimHei'),
      local('sans-serif');
  font-weight: normal;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: "ChillRoundGothic";
  src:
      url('@/assets/fonts/ChillRoundGothic_Heavy.ttf') format('truetype'),
      local('Microsoft YaHei Bold'),
      local('SimHei Bold'),
      local('sans-serif');
  font-weight: bold;
  font-style: normal;
  font-display: swap;
}

*:not([class*="el-icon-"]):not(svg):not(path):not(rect):not(text) {
  font-family: "ChillRoundGothic", sans-serif !important;
}

/* 根容器 */
.table-container {
  width: 100vw;
  min-height: 100vh;
  background:
      linear-gradient(rgba(44, 14, 0, 0.6), rgba(44, 14, 0, 0.6)),
      #d6ccc2 radial-gradient(circle, #e3d5ca 0%, #d5bdaf 100%);
  background-image:
      url('@/assets/images/bg.jpg'),
      linear-gradient(rgba(44, 14, 0, 0.6), rgba(44, 14, 0, 0.6)),
      radial-gradient(circle, #e3d5ca 0%, #d5bdaf 100%);
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  background-blend-mode: overlay;
  font-family: "ChillRoundGothic", sans-serif;
  color: #5d4037;
  padding: 30px 20px;
  box-sizing: border-box;
  position: relative;
}

/* 顶部标题栏 */
.table-header {
  max-width: 1200px;
  margin: 0 auto 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 10;
}

.vintage-back {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #8B4513;
  cursor: pointer;
  transition: all 0.2s;
}

.vintage-back:hover {
  color: #A0522D;
  transform: translateX(-3px);
}

.back-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  border: 2px solid #c19a6b;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.back-arrow {
  width: 0;
  height: 0;
  border-top: 8px solid transparent;
  border-bottom: 8px solid transparent;
  border-right: 12px solid #8B4513;
}

.title-ribbon {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  background: linear-gradient(to right, #8B4513, #A1887F);
  padding: 12px 80px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  border: 2px solid #c19a6b;
}

.page-title {
  color: #fff;
  font-size: 32px;
  margin: 0;
  text-shadow: 2px 2px 0px rgba(0,0,0,0.3);
  letter-spacing: 2px;
  text-align: center;
}

.user-currency {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8B4513;
  font-weight: bold;
}

.gold-coin {
  width: 30px;
  height: 30px;
  background: linear-gradient(to bottom, #D4AF37, #FFD700);
  border-radius: 50%;
  position: relative;
  box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.gold-coin::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 16px;
  height: 16px;
  background: linear-gradient(to bottom, #FFD700, #D4AF37);
  border-radius: 50%;
}

.coin-count {
  font-size: 18px;
}

/* 主内容布局 */
.main-body {
  display: flex;
  max-width: 1200px;
  margin: 0 auto;
  gap: 25px;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: center;
  position: relative;
  z-index: 10;
}

/* 侧边栏 */
.tab-sidebar {
  width: 220px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
  opacity: 0.98;
}

.poker-tab {
  height: 60px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  border: 2px solid #c19a6b;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5d4037;
  font-size: 18px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  gap: 10px;
}

.tab-icon {
  width: 20px;
  height: 20px;
  color: #8B4513;
  transition: all 0.2s ease;
}

.poker-tab.active .tab-icon {
  color: #fff;
}

.poker-tab.active {
  background: linear-gradient(to right, #8B4513, #A1887F);
  color: #fff;
  border-color: #D4AF37;
  box-shadow: 0 6px 12px rgba(139, 69, 19, 0.3);
  transform: translateY(-2px);
}

/* 右侧主面板 */
.game-panel {
  flex: 1;
  min-width: 500px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  border: 2px solid #c19a6b;
  padding: 40px;
  position: relative;
  opacity: 0.98;
}

.rivet {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #8B4513;
  border-radius: 50%;
  box-shadow: inset -2px -2px 2px rgba(0,0,0,0.2), 1px 1px 1px rgba(255,255,255,0.1);
  z-index: 1;
}
.lt { top: 10px; left: 10px; }
.rt { top: 10px; right: 10px; }
.lb { bottom: 10px; left: 10px; }
.rb { bottom: 10px; right: 10px; }

/* 个人资料面板 */
.profile-layout {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.avatar-module {
  text-align: center;
  margin-bottom: 40px;
}

.avatar-frame {
  width: 120px;
  height: 120px;
  border: 3px solid #c19a6b;
  border-radius: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
  position: relative;
  margin: 0 auto 15px;
}

.avatar-main {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 48px;
  font-weight: bold;
}

.rank-badge {
  position: absolute;
  bottom: -10px;
  right: -10px;
  background: linear-gradient(to right, #D4AF37, #FFD700);
  color: #8B4513;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.2);
  font-weight: bold;
}

.nickname-display {
  font-size: 28px;
  color: #8B4513;
  margin: 0;
}

.info-list {
  width: 100%;
  max-width: 500px;
  margin: 0 auto;
}

.info-cell {
  display: flex;
  justify-content: space-between;
  padding: 16px 10px;
  border-bottom: 1px dashed #c19a6b;
  font-size: 18px;
  color: #5d4037;
}

.cell-label {
  color: #6d4c41;
}

.cell-value {
  font-weight: bold;
  color: #8B4513;
}

.action-footer {
  text-align: center;
  margin-top: 30px;
}

.wood-btn {
  background: linear-gradient(to right, #8B4513, #A1887F);
  color: #ffffff;
  border: 1px solid rgba(255,255,255,0.4) !important;
  padding: 12px 40px;
  border-radius: 30px;
  font-size: 18px;
  cursor: pointer;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
  transition: all 0.2s ease;
  letter-spacing: 1px;
}

.wood-btn:hover {
  background: linear-gradient(to right, #A0522D, #BC8F8F);
  transform: translateY(-2px);
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.3);
}

/* ===== 战绩统计面板（重构） ===== */
.record-layout {
  padding: 10px 0;
}

.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #c19a6b;
}

.panel-title {
  font-size: 24px;
  color: #8B4513;
  margin: 0;
}

.record-count {
  font-size: 14px;
  color: #999;
}

/* 胜率概览条 */
.winrate-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(to bottom, #fff8e1, #ffe0b2);
  border: 1px solid #c19a6b;
  border-radius: 10px;
}

.winrate-item {
  flex: 1;
  text-align: center;
}

.winrate-label {
  display: block;
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.winrate-value {
  display: block;
  font-size: 22px;
  font-weight: bold;
  color: #8B4513;
}

.winrate-value.highlight {
  color: #D4AF37;
}

.record-scrollview {
  max-height: 600px;
  overflow-y: auto;
  padding-right: 10px;
}

.record-scrollview::-webkit-scrollbar {
  width: 8px;
}

.record-scrollview::-webkit-scrollbar-track {
  background: #f5e8d3;
  border-radius: 4px;
}

.record-scrollview::-webkit-scrollbar-thumb {
  background: #c19a6b;
  border-radius: 4px;
}

.record-scrollview::-webkit-scrollbar-thumb:hover {
  background: #8B4513;
}

.record-strip {
  background: linear-gradient(to bottom, #fff8e1, #ffe0b2);
  border: 1px solid #c19a6b;
  border-radius: 12px;
  margin-bottom: 12px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.strip-main {
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: background 0.2s;
}

.strip-main:hover {
  background: rgba(255,255,255,0.5);
}

.strip-left {
  font-size: 15px;
  color: #6d4c41;
}

.strip-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.strip-res {
  font-weight: bold;
  padding: 3px 10px;
  border-radius: 8px;
  font-size: 14px;
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

.fold-icon {
  width: 20px;
  height: 20px;
  position: relative;
  transition: transform 0.3s ease;
}

.fold-icon::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 10px;
  height: 2px;
  background: #8B4513;
}

.fold-icon::after {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 10px;
  background: #8B4513;
}

.fold-icon.is-active {
  transform: rotate(180deg);
}

.strip-detail {
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.8);
  border-top: 1px dashed #c19a6b;
}

.detail-hint {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}

.hint-label {
  font-weight: 600;
  color: #8B4513;
}

.hint-value {
  color: #999;
}

.table-wrapper {
  width: 100%;
  overflow-x: auto;
}

.poker-table {
  width: 100%;
  border-collapse: collapse;
}

.poker-table th {
  background: #8B4513;
  color: #fff;
  padding: 8px 10px;
  text-align: left;
}

.poker-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #e3d5ca;
  color: #5d4037;
}

/* 分页控制 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #c19a6b;
}

.page-btn {
  padding: 6px 16px;
  border: 1px solid #c19a6b;
  border-radius: 8px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  color: #8B4513;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: linear-gradient(to right, #8B4513, #A1887F);
  color: #fff;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #8B4513;
  font-weight: bold;
}

/* 筛选条件栏 */
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid #e3d5ca;
  border-radius: 8px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-label {
  font-size: 13px;
  color: #6d4c41;
  white-space: nowrap;
}

.filter-select {
  padding: 4px 8px;
  border: 1px solid #c19a6b;
  border-radius: 6px;
  background: #fff;
  color: #5d4037;
  font-size: 13px;
  outline: none;
}

.filter-input {
  padding: 4px 8px;
  border: 1px solid #c19a6b;
  border-radius: 6px;
  background: #fff;
  color: #5d4037;
  font-size: 13px;
  outline: none;
}

.filter-sep {
  color: #999;
  font-size: 13px;
}

.filter-reset {
  padding: 4px 12px;
  border: 1px solid #c19a6b;
  border-radius: 6px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  color: #8B4513;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-reset:hover {
  background: linear-gradient(to right, #8B4513, #A1887F);
  color: #fff;
}

/* 对局统计面板 */
.stats-layout {
  padding: 20px 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.stat-card {
  background: linear-gradient(to bottom, #fff8e1, #ffe0b2);
  border: 2px solid #c19a6b;
  border-radius: 16px;
  padding: 30px 20px;
  text-align: center;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
}

.stat-label {
  font-size: 18px;
  color: #6d4c41;
  margin-bottom: 10px;
}

.stat-value {
  font-size: 42px;
  font-weight: bold;
  color: #8B4513;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
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

/* 响应式 */
@media (max-width: 768px) {
  .table-header {
    flex-direction: column;
    gap: 20px;
  }

  .title-ribbon {
    position: static;
    transform: none;
    margin: 10px 0;
  }

  .main-body {
    flex-direction: column;
    align-items: center;
  }

  .tab-sidebar {
    width: 100%;
    max-width: 500px;
    flex-direction: row;
    justify-content: center;
  }

  .poker-tab {
    flex: 1;
    height: 55px;
    font-size: 16px;
    gap: 5px;
  }

  .tab-icon {
    width: 18px;
    height: 18px;
  }

  .game-panel {
    min-width: 100%;
    padding: 30px 20px;
  }

  .winrate-bar {
    flex-direction: column;
    gap: 8px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group {
    flex-wrap: wrap;
  }

  .pagination-bar {
    flex-wrap: wrap;
  }
}
</style>
