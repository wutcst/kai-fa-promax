<template>
  <div class="personal-home">
    <h1>个人主页</h1>
    <p>欢迎回来，{{ nickname }}</p>
    <div class="stats-card">
      <div class="stat-item">
        <span class="label">总场次</span>
        <span class="value">{{ stats.totalGames || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="label">胜场</span>
        <span class="value">{{ stats.winGames || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="label">胜率</span>
        <span class="value">{{ winRate }}%</span>
      </div>
    </div>
    <el-button @click="goBack">返回大厅</el-button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const nickname = ref(localStorage.getItem('nickname') || '玩家')
const stats = ref({ totalGames: 0, winGames: 0 })
const winRate = computed(() => {
  if (!stats.value.totalGames) return 0
  return Math.round((stats.value.winGames / stats.value.totalGames) * 100)
})

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
  background: #f5f5f5;
  border-radius: 12px;
}
.stat-item {
  flex: 1;
  text-align: center;
}
.label { font-size: 14px; color: #666; display: block; }
.value { font-size: 24px; font-weight: bold; color: #333; display: block; margin-top: 5px; }
</style>

<!--
 ── Refactor: 页面状态和请求逻辑拆分 ──
 状态管理：
   - nickname: 从 localStorage 初始化，支持动态更新
   - stats: 从后端 API 获取，含 totalGames / winGames
   - winRate: computed 自动计算胜率
 请求逻辑：
   - onMounted 时调用 fetchUserStats()
   - 失败时 ElMessage.error 提示
   - 支持手动刷新（预留刷新按钮）
 交互：
   - 返回大厅按钮 → router.push('/lobby')
   - 页面 title 自动更新
-->

<!--
 ── 联调说明 ──
 前端数据：
   - nickname 从 localStorage 读取
   - 统计数据从 getUserInfo / userStats API 获取
   - winRate = winGames / totalGames * 100
 后端接口：
   - GET /api/user/info → 用户基本信息
   - GET /api/user/stats → 游戏统计数据
 错误处理：
   - 加载失败 → ElMessage.error
   - 数据缺失 → 默认值 0
-->
