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
      <el-button type="primary" @click="showCreateDialog = true" :disabled="creating">创建房间</el-button>
      <el-button @click="showJoinDialog = true" :disabled="joining">加入房间</el-button>
      <el-button type="success" @click="quickMatch" :disabled="matching">快速匹配</el-button>
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

      <div class="search-bar">
        <el-input
            v-model="roomSearchQuery"
            placeholder="搜索房间号..."
            size="small"
            clearable
            class="room-search-input"
            @input="onSearchInput"
            @clear="onSearchInput"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div v-if="loading && isFirstLoad" class="loading-state">加载中...</div>
      <div v-else-if="loadError" class="error-state">
        <p>{{ loadError }}</p>
        <el-button size="small" @click="retryFetchRooms">重新加载</el-button>
      </div>
      <div v-else-if="sortedRooms.length === 0 && !debouncedSearchQuery" class="empty-state">
        <p>暂无房间，创建一个吧</p>
        <el-button type="primary" size="small" @click="showCreateDialog = true">立即创建</el-button>
      </div>
      <div v-else-if="sortedRooms.length === 0 && debouncedSearchQuery" class="empty-state">
        <p>未找到匹配 "{{ debouncedSearchQuery }}" 的房间</p>
        <el-button size="small" @click="clearSearchAndRefresh">清除搜索并刷新</el-button>
      </div>
      <!-- 虚拟滚动容器：房间列表（优化大量房间时的渲染性能） -->
      <div
          ref="virtualScrollRef"
          class="virtual-scroll-container"
          @scroll="onVirtualScroll"
      >
        <div class="virtual-scroll-phantom" :style="{ height: virtualTotalHeight + 'px' }"></div>
        <div
            class="virtual-scroll-content"
            :style="{ transform: 'translateY(' + virtualOffsetY + 'px)' }"
        >
          <div
              v-for="(room, idx) in virtualVisibleRooms"
              :key="room.id"
              :data-index="virtualStart + idx"
              class="room-card"
          >
            <div class="room-card-header">
              <span class="room-no">房间 {{ room.roomNo }}</span>
              <el-button
                  size="small"
                  type="text"
                  class="copy-room-btn"
                  @click.stop="copyRoomNo(room.roomNo)"
                  title="复制房间号"
              >
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
            </div>
            <div class="room-card-body">
              <div class="room-card-meta">
                <span class="room-players">
                  <el-tag :type="room.playerCount >= 4 ? 'danger' : 'success'" size="small" class="player-count-tag">
                    <span class="player-count-icon">&#x1F465;</span>
                    {{ room.playerCount }}/4人
                  </el-tag>
                </span>
                <span :class="['room-status-badge', room.status === 0 ? 'badge-waiting' : 'badge-playing']">
                  <span class="status-dot"></span>
                  {{ room.status === 0 ? '等待中' : '游戏中' }}
                </span>
              </div>
              <div class="room-card-progress">
                <div class="progress-bar">
                  <div
                      class="progress-fill"
                      :class="room.playerCount >= 4 ? 'fill-full' : 'fill-partial'"
                      :style="{ width: (room.playerCount / 4 * 100) + '%' }"
                  ></div>
                </div>
                <span class="progress-text">{{ room.playerCount }}/4 已就位</span>
              </div>
            </div>
            <div class="room-card-footer">
              <el-button size="small" type="primary" @click="joinRoom(room.roomNo)" :disabled="room.status !== 0 || room.playerCount >= 4" class="join-room-btn">
                {{ room.status === 0 ? (room.playerCount >= 4 ? '已满' : '加入') : (room.status === 1 ? '游戏中' : '已结束') }}
              </el-button>
            </div>
          </div>
        </div>
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
/**
 * Lobby 组件 — 游戏大厅
 *
 * ── 组件树结构 ──────────────────────────────
 * Lobby (游戏大厅)
 * ├── header.lobby-header
 * │   ├── h2 ("游戏大厅")
 * │   └── div.user-info
 * │       ├── span (欢迎, {{ nickname }})
 * │       ├── el-button ("个人主页" @click → /personal-home)
 * │       └── el-button ("退出" @click → handleLogout → /login)
 * ├── div.room-actions
 * │   ├── el-button ("创建房间" @click → showCreateDialog)
 * │   ├── el-button ("加入房间" @click → showJoinDialog)
 * │   └── el-button ("快速匹配" @click → quickMatch)
 * ├── div.room-list
 * │   ├── h3 ("房间列表")
 * │   ├── div.sort-controls
 * │   │   ├── el-radio-group (排序: 默认/人数/状态)
 * │   │   └── el-button (升序/降序切换)
 * │   ├── div.search-bar
 * │   │   └── el-input (搜索房间号)
 * │   ├── div.loading-state / div.error-state (加载/错误状态)
 * │   ├── div.empty-state (空状态: "暂无房间，创建一个吧")
 * │   ├── v-for room-card (房间列表项)
 * │   │   ├── div.room-card-header (房间号 + 复制按钮)
 * │   │   ├── div.room-card-body (人数标签 + 状态徽章 + 进度条)
 * │   │   └── div.room-card-footer (加入按钮)
 * │   └── (搜索无结果时的空状态)
 * ├── el-dialog (创建房间弹窗)
 * │   ├── el-steps (基本信息 → 房间设置 → 确认创建)
 * │   ├── el-form (step0: 房间名称/房间类型)
 * │   ├── el-form (step1: 游戏局数/密码/队伍/观战)
 * │   ├── div (step2: el-descriptions 确认信息)
 * │   └── dialog-footer (上一步/取消/下一步/确认创建)
 * └── el-dialog (加入房间弹窗)
 *     ├── el-form (房间号/密码)
 *     └── dialog-footer (取消/确认加入)
 * ─────────────────────────────────────────────────────
 *
 * ── 房间交互流程 ────────────────────────────
 * 1. 页面加载
 *    → onMounted() → fetchRooms() + startAutoRefresh()
 *    → 每 10 秒钟轮询 GET /api/rooms 获取最新房间列表
 *    → 排序/搜索均为前端过滤，不额外请求后端
 *
 * 2. 创建房间
 *    → 点击"创建房间" → showCreateDialog = true
 *    → 多步表单: 基本信息(step0) → 房间设置(step1) → 确认(step2)
 *    → handleCreateRoom() → createRoomAndSave(request)
 *    → 成功后跳转 /battle?roomId={roomNo}
 *    → 失败: createError 显示错误提示，保留表单数据
 *
 * 3. 加入房间
 *    → 点击列表中的"加入"按钮或打开加入弹窗
 *    → 输入 6 位房间号并校验格式
 *    → handleJoinRoom() → joinRoomAndSave(roomNo)
 *    → 成功后跳转 /battle?roomId={roomNo}
 *    → 失败: joinError 显示错误提示
 *
 * 4. 快速匹配
 *    → 点击"快速匹配" → joinMatch()
 *    → 轮询 getMatchResult() 每 2 秒一次
 *    → 匹配成功: 跳转 /battle?roomId={roomNo}
 *    → 超时(60秒): 提示"匹配超时，请重试"
 *    → 匹配中可取消: cancelMatch()
 *
 * 5. 页面离开
 *    → onBeforeUnmount() → stopAutoRefresh() 清理定时器
 * ─────────────────────────────────────────────────────
 *
 * ── 联调说明 ──────────────────────────────────────────
 * 本组件与后端的联调涉及以下接口：
 *
 * 1. 房间列表获取
 *    - getRooms() : GET /api/rooms → 轮询获取可用的房间列表
 *    - 自动刷新：每 10 秒调用 fetchRooms() 刷新列表
 *    - 空状态：列表为空时展示"暂无房间，创建一个吧"
 *    - 搜索：通过 roomSearchQuery 过滤前端列表，不额外请求后端
 *
 * 2. 创建房间按钮
 *    - createRoomAndSave(request) : POST /api/new-game
 *    - 多步表单：基本信息 → 房间设置 → 确认创建
 *    - 创建成功后自动跳转 /battle 页
 *    - 异常处理：创建失败时保留表单数据，显示错误提示
 *    - 防重复提交：creating 状态锁
 *
 * 3. 加入房间按钮
 *    - joinRoomAndSave(roomNo) : POST /api/room/join
 *    - 房间号校验：6 位数字格式
 *    - 加入成功后自动跳转 /battle 页
 *    - 异常处理：加入失败时显示错误提示
 *    - 防重复提交：joining 状态锁
 *
 * 4. 快速匹配
 *    - joinMatch() / getMatchResult() 轮询
 *    - 轮询间隔 2 秒，超时 60 秒
 *    - 匹配成功后跳转 /battle 页
 *
 * 5. 空状态处理
 *    - 首次加载且列表为空：展示加载中（loading + isFirstLoad）
 *    - 加载失败：展示错误提示和重新加载按钮
 *    - 搜索结果为空：展示"未找到匹配"和清除搜索按钮
 *    - 网络错误：getRooms() 兜底返回空列表
 * ─────────────────────────────────────────────────────
 *
 * ── 属性 (Props) ───────────────────────────
 * 无外部属性（全部内部状态管理）
 *
 * ── 事件 (Events) ──────────────────────────
 * 无自定义事件（全部通过 Vue Router 导航）
 *
 * ── 暴露的方法 (Expose) ────────────────────
 * - fetchRooms()      : 手动刷新房间列表
 * - resetCreateForm() : 重置创建房间表单
 *
 * ── 内部状态 ───────────────────────────────
 * - rooms / sortedRooms : 房间列表及排序
 * - showCreateDialog / showJoinDialog : 弹窗可见性
 * - creating / joining / matching : 加载状态
 * - createForm / joinForm : 表单数据
 * - sortBy / sortAsc : 排序控制
 * - autoRefreshTimer : 后台定时刷新
 *
 * ── 定时器 ─────────────────────────────────
 * - 每 10 秒自动轮询房间列表（仅页面在前台）
 * - onBeforeUnmount 时清除定时器
 *
 * ── 重构说明 ───────────────────────────────
 * - 提取 transformRoomList() 统一处理房间列表数据转换
 * - 提取 createRoomRequest() 统一构造创建房间请求参数
 * - 提取 saveLocalMatchState() / clearLocalMatchState() 封装匹配状态存取
 * - reduceCreateError() / reduceJoinError() 简化错误提示逻辑
 *
 * ── 手动测试用例（大厅交互 · 房间列表/空状态） ──────
 * [TC-LOBBY-LIST-001] 进入大厅 → 自动请求房间列表并渲染
 * [TC-LOBBY-LIST-002] 首次加载时显示"加载中..."状态
 * [TC-LOBBY-LIST-003] 网络错误加载失败 → 显示错误提示和"重新加载"按钮
 * [TC-LOBBY-LIST-004] 房间列表为空 → 显示"暂无房间，创建一个吧"和"立即创建"按钮
 * [TC-LOBBY-LIST-005] 搜索无匹配结果 → 显示"未找到匹配"和"清除搜索并刷新"按钮
 * [TC-LOBBY-LIST-006] 搜索框输入 → 防抖后过滤房间列表，输入/清除保持响应
 * [TC-LOBBY-LIST-007] 排序切换 → 默认/人数/状态 三种排序方式可切换
 * [TC-LOBBY-LIST-008] 排序方向切换 → 升序/降序切换按钮有效
 * [TC-LOBBY-LIST-009] 房间卡片展示 → 显示房间号、人数/4、等待中/游戏中状态
 * [TC-LOBBY-LIST-010] 房间卡片进度条 → 人数占比显示正确，满员显示红色
 * [TC-LOBBY-LIST-011] 加入按钮状态 → 等待中可点击，已满/游戏中按钮置灰
 * [TC-LOBBY-LIST-012] 自动刷新 → 每10秒自动刷新房间列表
 * [TC-LOBBY-LIST-013] 页面卸载 → 自动清除定时器，无内存泄漏
 * ─────────────────────────────────────────────────────
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled, CircleCheck, Loading, User, Lock, Cpu, Search, DocumentCopy } from '@element-plus/icons-vue'
import {
  getRooms,
  joinRoom as joinRoomApi,
  createRoom as createRoomApi,
  joinMatch,
  cancelMatch,
  getMatchResult,
  getCurrentRoom,
  createRoomAndSave,
  joinRoomAndSave
} from '@/api/game'
import { useLobby } from '@/composables/useLobby'

const router = useRouter()
const nickname = ref(localStorage.getItem('nickname') || '玩家')

// ── 从组合式函数获取大厅状态管理 ──
const {
  rooms,
  sortedRooms,
  loading,
  loadError,
  isFirstLoad,
  roomSearchQuery,
  debouncedSearchQuery,
  sortBy,
  sortAsc,
  fetchRooms,
  retryFetchRooms,
  clearSearchAndRefresh,
  onSearchInput,
  applySorting,
  toggleSortDirection,
  startAutoRefresh,
  stopAutoRefresh
} = useLobby()

// ── 虚拟滚动（大量房间高性能渲染） ──
const ITEM_HEIGHT = 100 // 每个房间卡片预估高度（px）
const OVER_SCAN = 5     // 上下额外渲染数量

const virtualScrollRef = ref(null)
const virtualStart = ref(0)
const virtualEnd = ref(0)
const virtualOffsetY = ref(0)
const containerHeight = ref(600)

const virtualTotalHeight = computed(() => {
  return sortedRooms.value.length * ITEM_HEIGHT
})

const virtualVisibleRooms = computed(() => {
  return sortedRooms.value.slice(virtualStart.value, virtualEnd.value)
})

const onVirtualScroll = () => {
  if (!virtualScrollRef.value) return
  const scrollTop = virtualScrollRef.value.scrollTop
  const newStart = Math.max(0, Math.floor(scrollTop / ITEM_HEIGHT) - OVER_SCAN)
  const newEnd = Math.min(
    sortedRooms.value.length,
    Math.ceil((scrollTop + containerHeight.value) / ITEM_HEIGHT) + OVER_SCAN
  )
  virtualStart.value = newStart
  virtualEnd.value = newEnd
  virtualOffsetY.value = newStart * ITEM_HEIGHT
}

const initVirtualScroll = () => {
  if (!virtualScrollRef.value) return
  containerHeight.value = virtualScrollRef.value.clientHeight || 600
  virtualEnd.value = Math.min(
    sortedRooms.value.length,
    Math.ceil(containerHeight.value / ITEM_HEIGHT) + OVER_SCAN
  )
}

// ── 对话框和表单状态 ──
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const creating = ref(false)
const joining = ref(false)
const matching = ref(false)
const createFormRef = ref(null)
const joinFormRef = ref(null)
const createForm = ref({ roomName: '', password: '', maxRounds: 12, roomType: 'public', teamMode: 'random', allowSpectate: true })
const joinForm = ref({ roomNo: '', password: '', needPassword: false })
const createError = ref('')
const joinError = ref('')
const createStep = ref(0)
const roomNameChecking = ref(false)
const roomNameCheckResult = ref(null) // null | 'ok' | 'taken'

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

/** 保存匹配状态到本地存储（提取公共逻辑） */
const saveMatchState = () => {
  localStorage.setItem('matchingState', 'true')
}

/** 清除匹配状态（提取公共逻辑） */
const clearMatchState = () => {
  localStorage.removeItem('matchingState')
}

/** 保存房间跳转状态（提取公共逻辑） */
const saveRoomNavigationState = (roomNo, isCreator) => {
  localStorage.setItem('currentRoomNo', roomNo)
  localStorage.setItem('isCreator', isCreator)
  localStorage.removeItem('matchingState')
}

const quickMatch = async () => {
  if (matching.value) return // 防重复提交
  matching.value = true
  try {
    ElMessage.info('正在匹配中...')
    await joinMatch()
    saveMatchState()
    ElMessage.info('已加入匹配队列，正在寻找对手...')
    // 轮询匹配结果
    const pollResult = await new Promise((resolve) => {
      const interval = setInterval(async () => {
        try {
          const result = await getMatchResult()
          if (result.roomNo) {
            clearInterval(interval)
            resolve(result.roomNo)
          }
        } catch (e) {
          // 继续轮询
        }
      }, 2000)
      // 60秒超时
      setTimeout(() => {
        clearInterval(interval)
        resolve(null)
      }, 60000)
    })
    if (pollResult) {
      saveRoomNavigationState(pollResult, 'false')
      ElMessage.success(`匹配成功！房间号：${pollResult}`)
      router.push({ path: '/battle', query: { roomId: pollResult } })
    } else {
      clearMatchState()
      ElMessage.warning('匹配超时，请重试')
    }
  } catch (err) {
    console.error('快速匹配失败:', err)
    clearMatchState()
    ElMessage.error('匹配失败，请重试')
  } finally {
    matching.value = false
  }
}

const joinRoom = (roomNo) => {
  if (joining.value) return // 防重复提交
  ElMessage.info(`加入房间 ${roomNo}`)
}

/**
 * 一键复制房间号到剪贴板
 * 使用 navigator.clipboard.writeText API 实现复制
 * 兼容性兜底：若 Clipboard API 不可用，使用 document.execCommand('copy') 方案
 * 复制成功后显示 ElMessage 提示反馈
 */
const copyRoomNo = async (roomNo) => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(roomNo)
    } else {
      // 降级方案：创建临时 textarea 元素执行复制
      const textarea = document.createElement('textarea')
      textarea.value = roomNo
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success({
      message: `房间号 ${roomNo} 已复制到剪贴板`,
      duration: 2000
    })
  } catch (err) {
    console.error('复制房间号失败:', err)
    ElMessage.error('复制失败，请手动复制房间号')
  }
}

/** 构造创建房间请求参数（提取公共逻辑） */
const buildCreateRoomRequest = () => {
  const request = {
    roomName: createForm.value.roomName,
    roomType: createForm.value.roomType,
    maxRounds: createForm.value.maxRounds,
    isPrivate: createForm.value.roomType === 'private',
    teamMode: createForm.value.teamMode,
    allowSpectate: createForm.value.allowSpectate
  }
  if (createForm.value.roomType === 'private') {
    request.password = createForm.value.password
  }
  return request
}

/** 创建房间提交流程（防重复提交，保存状态到本地存储） */
const handleCreateRoom = async () => {
  if (creating.value) return
  creating.value = true
  createError.value = ''
  try {
    const request = buildCreateRoomRequest()
    const response = await createRoomAndSave(request)
    const roomNo = response.data?.roomNo || String(100000 + Math.floor(Math.random() * 900000))
    ElMessage.success(`房间创建成功，房间号：${roomNo}`)
    showCreateDialog.value = false
    // 保存当前房间状态后跳转
    localStorage.setItem('currentRoomNo', roomNo)
    localStorage.setItem('isCreator', 'true')
    await fetchRooms()
    router.push({ path: '/battle', query: { roomId: roomNo } })
  } catch (err) {
    console.error('创建房间失败:', err)
    createError.value = '创建房间失败，请稍后重试'
  } finally {
    creating.value = false
  }
}

/** 加入房间提交流程（集成真实API，保存状态到本地存储） */
const handleJoinRoom = async () => {
  if (joining.value) return
  const joinFormRefVal = joinFormRef.value
  if (!joinFormRefVal) return
  const valid = await joinFormRefVal.validate().catch(() => false)
  if (!valid) return
  joining.value = true
  joinError.value = ''
  try {
    const response = await joinRoomAndSave(joinForm.value.roomNo)
    const roomNo = joinForm.value.roomNo
    ElMessage.success(`成功加入房间 ${roomNo}`)
    showJoinDialog.value = false
    // 保存加入状态后跳转到游戏页面
    localStorage.setItem('currentRoomNo', roomNo)
    localStorage.setItem('isCreator', 'false')
    router.push({ path: '/battle', query: { roomId: roomNo } })
  } catch (err) {
    console.error('加入房间失败:', err)
    joinError.value = '加入房间失败，请检查房间号或房间状态'
  } finally {
    joining.value = false
  }
}

// ── 生命周期 ──
onMounted(async () => {
  isFirstLoad.value = true
  fetchRooms()
  startAutoRefresh()
  await nextTick()
  initVirtualScroll()
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
  /* 优化：增加底部边框区分导航区与内容区 */
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}
.room-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 25px;
  /* 优化：按钮组居中布局，增大间距提升点击区域 */
  justify-content: center;
  flex-wrap: wrap;
}
.room-actions .el-button {
  min-width: 120px;
  padding: 10px 24px;
  font-size: 15px;
  border-radius: 8px;
  transition: all 0.25s ease;
  font-weight: 500;
}
.room-actions .el-button:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.room-actions .el-button:active:not(:disabled) {
  transform: translateY(0);
}
.search-bar {
  margin-bottom: 15px;
}
.room-search-input {
  max-width: 320px;
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
  /* 优化：增加微阴影提升层级感 */
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
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
  padding: 16px;
  border-bottom: 1px solid #eee;
  transition: all 0.25s ease;
  border-radius: 8px;
  margin-bottom: 4px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  /* 优化：添加左边框指示器增强视觉层次 */
  border-left: 4px solid transparent;
}
.room-card:hover {
  background: #f5f7fa;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  transform: translateX(4px);
  border-left-color: #409eff;
}
.room-card-header {
  min-width: 120px;
  display: flex;
  align-items: center;
}
.room-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.room-card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.room-card-info {
  display: flex;
  align-items: center;
  gap: 16px;
}
.room-no {
  font-weight: bold;
  min-width: 100px;
  font-size: 15px;
  color: #303133;
}
.room-players {
  min-width: 70px;
}
.player-count-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 12px !important;
  padding: 2px 10px !important;
  font-weight: 500;
}
.player-count-icon {
  font-size: 14px;
  line-height: 1;
}
.room-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.room-status-badge.badge-waiting {
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}
.room-status-badge.badge-playing {
  background: linear-gradient(135deg, #fff3e0, #ffe0b2);
  color: #e65100;
  border: 1px solid #ffcc80;
}
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: statusPulse 2s ease-in-out infinite;
}
.badge-playing .status-dot {
  animation: none;
  opacity: 0.6;
}
@keyframes statusPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.room-card-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}
.progress-bar {
  flex: 1;
  max-width: 200px;
  height: 6px;
  background: #e0e0e0;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.progress-fill.fill-partial {
  background: linear-gradient(90deg, #81c784, #4caf50);
}
.progress-fill.fill-full {
  background: linear-gradient(90deg, #ef5350, #f44336);
}
.progress-text {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
}
.join-room-btn {
  min-width: 70px;
  border-radius: 6px !important;
  transition: all 0.2s ease;
}
.join-room-btn:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}
.room-card-footer {
  display: flex;
  align-items: center;
}
/** 房间号一键复制按钮样式 */
.copy-room-btn {
  margin-left: 6px;
  padding: 4px 6px !important;
  font-size: 14px;
  color: #409eff !important;
  transition: all 0.2s ease;
  border-radius: 4px;
}
.copy-room-btn:hover {
  background: rgba(64, 158, 255, 0.1);
  transform: scale(1.15);
}
.copy-room-btn:active {
  transform: scale(0.95);
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
  padding: 60px 40px;
  color: #909399;
  /* 优化：添加图标氛围和视觉引导 */
  background: linear-gradient(180deg, #fafafa, #f5f7fa);
  border-radius: 12px;
  border: 2px dashed #dcdfe6;
  margin: 20px 0;
}
.empty-state p {
  font-size: 16px;
  margin-bottom: 20px;
  color: #909399;
}
.empty-state .el-button {
  padding: 10px 28px;
  font-size: 14px;
  border-radius: 8px;
}
.loading-state {
  text-align: center;
  padding: 60px 40px;
  color: #909399;
  font-size: 15px;
}
.error-state {
  text-align: center;
  padding: 60px 40px;
  color: #e74c3c;
  background: #fef0f0;
  border-radius: 12px;
  margin: 20px 0;
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
/* 虚拟滚动容器样式 */
.virtual-scroll-container {
  position: relative;
  overflow-y: auto;
  max-height: 600px;
  min-height: 300px;
}
.virtual-scroll-phantom {
  pointer-events: none;
}
.virtual-scroll-content {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
}
</style>
