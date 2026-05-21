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

    <!-- 创建房间对话框（多步表单） -->
    <el-dialog v-model="showCreateDialog" title="创建房间" width="460px" :close-on-click-modal="false" @close="resetCreateForm" @open="onCreateDialogOpen">
      <el-steps :active="createStep" align-center finish-status="success" class="create-steps">
        <el-step title="基本信息" />
        <el-step title="房间设置" />
        <el-step title="确认创建" />
      </el-steps>

      <!-- 步骤1：基本信息 -->
      <el-form v-show="createStep === 0" ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px" @submit.prevent>
        <el-form-item label="房间名称" prop="roomName">
          <el-input
              v-model="createForm.roomName"
              placeholder="输入房间名称（2-20个字符）"
              maxlength="20"
              show-word-limit
              :disabled="creating"
              @input="onRoomNameInput"
          />
          <div v-if="roomNameChecking" class="field-checking">
            <el-icon class="is-loading"><Loading /></el-icon> 检查可用性...
          </div>
          <div v-else-if="roomNameCheckResult === 'ok'" class="field-valid">
            <el-icon><CircleCheck /></el-icon> 名称可用
          </div>
          <div v-else-if="roomNameCheckResult === 'taken'" class="field-invalid">
            <el-icon><WarningFilled /></el-icon> 名称已被占用
          </div>
        </el-form-item>
        <el-form-item label="房间类型" prop="roomType">
          <el-radio-group v-model="createForm.roomType">
            <el-radio-button value="public">
              <el-icon><User /></el-icon> 公开
            </el-radio-button>
            <el-radio-button value="private">
              <el-icon><Lock /></el-icon> 私密
            </el-radio-button>
            <el-radio-button value="ai">
              <el-icon><Cpu /></el-icon> 人机
            </el-radio-button>
          </el-radio-group>
          <div class="room-type-desc">
            {{ createForm.roomType === 'public' ? '所有玩家可见，可自由加入' : createForm.roomType === 'private' ? '仅受邀玩家可加入，需密码' : '与AI机器人对战，练习模式' }}
          </div>
        </el-form-item>
      </el-form>

      <!-- 步骤2：房间设置 -->
      <el-form v-show="createStep === 1" :model="createForm" label-width="90px">
        <el-form-item label="游戏局数">
          <el-radio-group v-model="createForm.maxRounds" :disabled="creating">
            <el-radio :value="8">
              <span>8局</span>
              <span class="radio-desc">快速</span>
            </el-radio>
            <el-radio :value="12">
              <span>12局</span>
              <span class="radio-desc">标准</span>
            </el-radio>
            <el-radio :value="16">
              <span>16局</span>
              <span class="radio-desc">完整</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="房间密码" prop="password" v-if="createForm.roomType === 'private'">
          <el-input
              v-model="createForm.password"
              placeholder="请输入4-8位数字密码"
              maxlength="8"
              show-password
              :disabled="creating"
          />
        </el-form-item>
        <el-form-item label="队伍分配">
          <el-radio-group v-model="createForm.teamMode" :disabled="creating">
            <el-radio value="random">随机分配</el-radio>
            <el-radio value="manual">手动分配</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="观战模式">
          <el-switch v-model="createForm.allowSpectate" :disabled="creating" active-text="允许观战" />
        </el-form-item>
      </el-form>

      <!-- 步骤3：确认 -->
      <div v-show="createStep === 2" class="confirm-step">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="房间名称">{{ createForm.roomName }}</el-descriptions-item>
          <el-descriptions-item label="房间类型">
            <el-tag :type="createForm.roomType === 'public' ? 'success' : createForm.roomType === 'private' ? 'warning' : 'info'" size="small">
              {{ createForm.roomType === 'public' ? '公开' : createForm.roomType === 'private' ? '私密' : '人机' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="游戏局数">{{ createForm.maxRounds }}局</el-descriptions-item>
          <el-descriptions-item v-if="createForm.roomType === 'private'" label="房间密码">{{ createForm.password }}</el-descriptions-item>
          <el-descriptions-item label="队伍分配">{{ createForm.teamMode === 'random' ? '随机分配' : '手动分配' }}</el-descriptions-item>
          <el-descriptions-item label="观战模式">{{ createForm.allowSpectate ? '允许' : '禁止' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-form-item v-if="createError" class="form-error-item">
        <div class="form-error-message">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ createError }}</span>
        </div>
      </el-form-item>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="createStep > 0" @click="prevCreateStep" :disabled="creating" :loading="creating">
            上一步
          </el-button>
          <el-button @click="showCreateDialog = false" :disabled="creating" :loading="creating">
            取消
          </el-button>
          <el-button v-if="createStep < 2" type="primary" @click="nextCreateStep" :disabled="creating">
            下一步
          </el-button>
          <el-button v-else type="primary" @click="handleCreateRoom" :loading="creating" :disabled="creating">
            {{ creating ? '创建中...' : '确认创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 加入房间对话框 -->
    <el-dialog v-model="showJoinDialog" title="加入房间" width="400px" :close-on-click-modal="false" @close="resetJoinForm">
      <el-form ref="joinFormRef" :model="joinForm" :rules="joinRules" label-width="80px" @submit.prevent>
        <el-form-item label="房间号" prop="roomNo">
          <el-input
              v-model="joinForm.roomNo"
              placeholder="输入6位房间号"
              maxlength="6"
              show-word-limit
              :disabled="joining"
          />
        </el-form-item>
        <el-form-item v-if="joinForm.needPassword" label="房间密码" prop="password">
          <el-input
              v-model="joinForm.password"
              placeholder="请输入房间密码"
              maxlength="8"
              show-password
              :disabled="joining"
          />
        </el-form-item>
        <el-form-item v-if="joinError" class="form-error-item">
          <div class="form-error-message">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ joinError }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showJoinDialog = false" :disabled="joining">取消</el-button>
          <el-button type="primary" @click="handleJoinRoom" :loading="joining" :disabled="joining">
            {{ joining ? '加入中...' : '确认加入' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled, CircleCheck, Loading, User, Lock, Cpu } from '@element-plus/icons-vue'

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
const createFormRef = ref(null)
const joinFormRef = ref(null)
const createForm = ref({ roomName: '', password: '', maxRounds: 12, roomType: 'public', teamMode: 'random', allowSpectate: true })
const joinForm = ref({ roomNo: '', password: '', needPassword: false })
const createError = ref('')
const joinError = ref('')
const createStep = ref(0)
const roomNameChecking = ref(false)
const roomNameCheckResult = ref(null) // null | 'ok' | 'taken'
const sortBy = ref('default')
const sortAsc = ref(true)
const autoRefreshTimer = ref(null)
const isFirstLoad = ref(true)

const applySorting = () => {
  // computed 自动重新计算 sortedRooms
}

const toggleSortDirection = () => {
  sortAsc.value = !sortAsc.value
}

const createRules = {
  roomName: [
    { required: true, message: '请输入房间名称', trigger: 'blur' },
    { min: 2, max: 20, message: '房间名称为2-20个字符', trigger: 'blur' }
  ],
  password: [
    { pattern: /^$|^\d{4,8}$/, message: '密码为4-8位数字（留空则不设密码）', trigger: 'blur' }
  ]
}

const joinRules = {
  roomNo: [
    { required: true, message: '请输入房间号', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '房间号为6位数字', trigger: 'blur' }
  ]
}

const resetCreateForm = () => {
  createForm.value = { roomName: '', password: '', maxRounds: 12, roomType: 'public', teamMode: 'random', allowSpectate: true }
  createError.value = ''
  createStep.value = 0
  roomNameCheckResult.value = null
  roomNameChecking.value = false
  if (createFormRef.value) createFormRef.value.resetFields()
}

const resetJoinForm = () => {
  joinForm.value = { roomNo: '', password: '', needPassword: false }
  joinError.value = ''
  if (joinFormRef.value) joinFormRef.value.resetFields()
}

const onCreateDialogOpen = () => {
  createStep.value = 0
  roomNameCheckResult.value = null
}

const onRoomNameInput = () => {
  roomNameCheckResult.value = null
}

const nextCreateStep = async () => {
  if (createStep.value === 0) {
    if (!createFormRef.value) return
    const valid = await createFormRef.value.validate().catch(() => false)
    if (!valid) return
  }
  createStep.value++
}

const prevCreateStep = () => {
  if (createStep.value > 0) createStep.value--
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
  // 首次加载或数据为空时重新获取；避免每次导航触发自动刷新
  if (!isFirstLoad.value && rooms.value.length > 0) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    // TODO: 接入真实API
    rooms.value = []
    isFirstLoad.value = false
  } catch (err) {
    console.error('获取房间列表失败:', err)
    loadError.value = '获取房间列表失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

// 启动后台定时刷新（仅页面在前台运行时有效）
const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefreshTimer.value = setInterval(() => {
    isFirstLoad.value = false
    loading.value = true
    // TODO: 接入真实API
    rooms.value = []
    loading.value = false
  }, 10000)
}

// 停止定时刷新
const stopAutoRefresh = () => {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

onMounted(() => {
  isFirstLoad.value = true
  fetchRooms()
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
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
.form-error-item {
  margin-bottom: 0 !important;
}
.form-error-message {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e74c3c;
  font-size: 13px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 4px;
  width: 100%;
}
.form-error-message .el-icon {
  font-size: 16px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.create-steps {
  margin-bottom: 25px;
  padding: 10px 0;
}
.radio-desc {
  font-size: 11px;
  color: #999;
  margin-left: 4px;
}
.room-type-desc {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  padding-left: 4px;
}
.confirm-step {
  padding: 10px 0;
}
.field-checking,
.field-valid,
.field-invalid {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  margin-top: 4px;
}
.field-checking { color: #909399; }
.field-valid { color: #67c23a; }
.field-invalid { color: #e74c3c; }
</style>
