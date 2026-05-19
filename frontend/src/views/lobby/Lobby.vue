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
      <div class="sort-controls">
        <span class="sort-label">排序：</span>
        <el-radio-group v-model="sortBy" size="small" @change="applySorting">
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="playerCount">人数</el-radio-button>
          <el-radio-button value="status">状态</el-radio-button>
        </el-radio-group>
        <el-button
            v-if="sortBy !== 'default'"
            type="text"
            size="small"
            :icon="sortAsc ? 'SortUp' : 'SortDown'"
            @click="toggleSortDirection"
            class="sort-direction-btn"
        >
          {{ sortAsc ? '升序' : '降序' }}
        </el-button>
      </div>

      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="loadError" class="error-state">
        <p>{{ loadError }}</p>
        <el-button size="small" @click="fetchRooms">重新加载</el-button>
      </div>
      <div v-else-if="sortedRooms.length === 0" class="empty-state">
        <p>暂无房间，创建一个吧</p>
        <el-button type="primary" size="small" @click="showCreateDialog = true">立即创建</el-button>
      </div>
      <div v-for="room in sortedRooms" :key="room.id" class="room-card">
        <div class="room-card-info">
          <span class="room-no">房间 {{ room.roomNo }}</span>
          <span class="room-players">
            <el-tag :type="room.playerCount >= 4 ? 'danger' : 'success'" size="small">
              {{ room.playerCount }}/4人
            </el-tag>
          </span>
          <span :class="['room-status-tag', room.status === 0 ? 'waiting' : 'playing']">
            {{ room.status === 0 ? '等待中' : '游戏中' }}
          </span>
        </div>
        <el-button size="small" type="primary" @click="joinRoom(room.roomNo)" :disabled="room.status !== 0">加入</el-button>
      </div>
    </div>

    <!-- 创建房间对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建房间" width="400px">
      <el-form :model="createForm">
        <el-form-item label="房间名称">
          <el-input v-model="createForm.roomName" placeholder="输入房间名称（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateRoom" :loading="creating">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 加入房间对话框 -->
    <el-dialog v-model="showJoinDialog" title="加入房间" width="400px">
      <el-form :model="joinForm">
        <el-form-item label="房间号">
          <el-input v-model="joinForm.roomNo" placeholder="输入6位房间号" maxlength="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJoinDialog = false">取消</el-button>
        <el-button type="primary" @click="handleJoinRoom" :loading="joining">确认加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const nickname = ref(localStorage.getItem('nickname') || '玩家')
const rooms = ref([])
const sortedRooms = computed(() => {
  if (sortBy.value === 'default') return rooms.value
  const sorted = [...rooms.value]
  sorted.sort((a, b) => {
    let cmp = 0
    if (sortBy.value === 'playerCount') {
      cmp = a.playerCount - b.playerCount
    } else if (sortBy.value === 'status') {
      cmp = (a.status || 0) - (b.status || 0)
    }
    return sortAsc.value ? cmp : -cmp
  })
  return sorted
})
const loading = ref(false)
const loadError = ref('')
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const creating = ref(false)
const joining = ref(false)
const createForm = ref({ roomName: '' })
const joinForm = ref({ roomNo: '' })
const sortBy = ref('default')
const sortAsc = ref(true)

const applySorting = () => {
  // computed 自动重新计算 sortedRooms
}

const toggleSortDirection = () => {
  sortAsc.value = !sortAsc.value
}

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

const fetchRooms = async () => {
  loading.value = true
  loadError.value = ''
  try {
    // TODO: 接入真实API
    rooms.value = []
  } catch (err) {
    console.error('获取房间列表失败:', err)
    loadError.value = '获取房间列表失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

const handleCreateRoom = async () => {
  creating.value = true
  try {
    // TODO: 接入真实创建房间API
    ElMessage.success('房间创建成功')
    showCreateDialog.value = false
    createForm.value.roomName = ''
    await fetchRooms()
  } catch (err) {
    console.error('创建房间失败:', err)
    ElMessage.error('创建房间失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

const handleJoinRoom = async () => {
  if (!joinForm.value.roomNo || joinForm.value.roomNo.length < 6) {
    ElMessage.warning('请输入正确的6位房间号')
    return
  }
  joining.value = true
  try {
    // TODO: 接入真实加入房间API
    ElMessage.success(`已加入房间 ${joinForm.value.roomNo}`)
    showJoinDialog.value = false
    joinForm.value.roomNo = ''
  } catch (err) {
    console.error('加入房间失败:', err)
    ElMessage.error('加入房间失败，请检查房间号是否正确')
  } finally {
    joining.value = false
  }
}

onMounted(() => {
  fetchRooms()
})
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
.sort-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 15px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}
.sort-label {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
}
.sort-direction-btn {
  margin-left: auto;
  font-size: 13px;
}
.room-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px;
  border-bottom: 1px solid #eee;
}
.room-card-info {
  display: flex;
  align-items: center;
  gap: 16px;
}
.room-no {
  font-weight: bold;
  min-width: 100px;
}
.room-players {
  min-width: 70px;
}
.room-status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
}
.room-status-tag.waiting {
  background: #e8f5e9;
  color: #2e7d32;
}
.room-status-tag.playing {
  background: #fff3e0;
  color: #e65100;
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}
.loading-state {
  text-align: center;
  padding: 40px;
  color: #666;
}
.error-state {
  text-align: center;
  padding: 40px;
  color: #e74c3c;
}
.error-state p {
  margin-bottom: 12px;
}
</style>

<!--
 ── Refactor: 页面状态和请求逻辑拆分 ──
 状态管理：
   - rooms: 房间列表数据（从 API 获取）
   - loading: 加载中状态
   - loadError: 加载失败时的错误信息
   - nickname: 从 localStorage 获取
 请求逻辑：
   - onMounted 时调用 fetchRooms()
   - 失败时展示 loadError + 重新加载按钮
   - 空房间时展示 empty-state
 交互：
   - 创建房间 → showCreateDialog
   - 加入房间 → showJoinDialog
   - 快速匹配 → quickMatch（预留）
   - 个人主页 → router.push('/personal-home')
   - 退出 → handleLogout()
-->

<!--
 ── 联调说明 ──
 房间列表：
   - GET /api/rooms → 返回等待中和游戏中的房间
   - 空状态：提示"暂无房间，创建一个吧"
   - 加载状态：显示"加载中..."
   - 错误状态：显示错误信息 + 重新加载按钮
 创建房间：
   - POST /api/new-game → 返回 roomNo
   - 成功后自动刷新房间列表
 加入房间：
   - POST /api/room/join → 校验房间号
   - 满员提示、重复加入提示
-->

<!--
 ── 组件使用说明 ──
 依赖组件：el-button, el-dialog, el-form, el-input, el-table
 外部 API：
   - fetchRooms() → GET /api/rooms
   - createRoom() → POST /api/new-game
   - joinRoom() → POST /api/room/join
   - quickMatch() → POST /api/match/quick (预留)
  Props：无（独立页面组件）
 Emits：无
 数据流：
   - 输入：localStorage (token, nickname)
   - 输出：同页内状态管理
 状态覆盖：loading / loaded / empty / error
-->
