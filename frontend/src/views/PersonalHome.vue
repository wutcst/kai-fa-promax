<template>
  <div class="personal-home">
    <h2>个人中心</h2>
    <div class="user-card">
      <p><strong>{{ username }}</strong></p>
      <p>总场次：{{ totalGames }}</p>
      <p>胜率：{{ winRate }}%</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const username = ref('玩家')
const totalGames = ref(0)
const winRate = ref(0)

onMounted(() => {
  username.value = localStorage.getItem('username') || '玩家'
})
</script>

<style scoped>
.personal-home { max-width: 480px; margin: 40px auto; padding: 24px; }
.user-card { background: #f5f7fa; padding: 16px; border-radius: 8px; margin-top: 16px; }
</style>
// Refactor: extract personal home page request logic into composable
