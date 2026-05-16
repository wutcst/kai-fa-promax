<template>
  <div class="lobby">
    <header class="lobby-header">
      <h2>游戏大厅</h2>
      <div class="user-info">
        <span>欢迎，{{ nickname }}</span>
        <el-button type="text" @click="goToPersonal">个人主页</el-button>
        <el-button type="text" @click="handleLogout">退出</el-button>
      </div>
    </header>

    <div class="room-actions">
      <el-button type="primary" @click="showCreateDialog = true">创建房间</el-button>
      <el-button @click="showJoinDialog = true">加入房间</el-button>
      <el-button type="success" @click="quickMatch">快速匹配</el-button>
    </div>

    <div class="room-list">
      <h3>房间列表</h3>
      <div v-if="rooms.length === 0" class="empty-state">暂无房间，创建一个吧</div>
      <div v-for="room in rooms" :key="room.id" class="room-card">
        <span>房间 {{ room.roomNo }}</span>
        <span>{{ room.playerCount }}/4人</span>
        <span>{{ room.status === 0 ? '等待中' : '游戏中' }}</span>
        <el-button size="small" @click="joinRoom(room.roomNo)">加入</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const nickname = ref(localStorage.getItem('nickname') || '玩家')
const rooms = ref([])
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)

const goToPersonal = () => router.push('/personal-home')

const handleLogout = () => {
  localStorage.clear()
  sessionStorage.clear()
  ElMessage.success('已退出')
  router.push('/login')
}

const quickMatch = () => {
  ElMessage.info('正在匹配中...')
}

const joinRoom = (roomNo) => {
  ElMessage.info(`加入房间 ${roomNo}`)
}
</script>

<style scoped>
.lobby {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.lobby-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.room-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 25px;
}
.room-list {
  background: #f9f9f9;
  border-radius: 8px;
  padding: 20px;
}
.room-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 12px;
  border-bottom: 1px solid #eee;
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}
</style>
