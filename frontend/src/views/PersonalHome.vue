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
