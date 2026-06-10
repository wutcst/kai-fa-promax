/**
 * useLobby — 游戏大厅状态管理组合式函数
 *
 * ── 职责 ─────────────────────────────────────────
 * 封装 Lobby.vue 中的房间列表管理、搜索、排序、刷新
 * 等职责相关的响应式状态和方法。
 *
 * ── 导出 ─────────────────────────────────────────
 * - rooms              : 原始房间列表
 * - sortedRooms        : 排序后的房间列表
 * - loading            : 加载状态
 * - loadError          : 加载错误信息
 * - isFirstLoad        : 是否首次加载
 * - roomSearchQuery    : 搜索输入框绑定
 * - debouncedSearchQuery : 防抖搜索值
 * - sortBy             : 排序字段
 * - sortAsc            : 排序方向
 * - autoRefreshTimer   : 自动刷新定时器
 *
 * - fetchRooms()            : 获取房间列表
 * - retryFetchRooms()       : 重试获取
 * - clearSearchAndRefresh() : 清空搜索并刷新
 * - onSearchInput(value)    : 搜索输入处理
 * - applySorting()          : 应用排序
 * - toggleSortDirection()   : 切换排序方向
 * - startAutoRefresh()      : 启动自动刷新
 * - stopAutoRefresh()       : 停止自动刷新
 *
 * ── 新增功能 ─────────────────────────────────────
 * - WebSocket 消息缓冲合并在短时间内将多条 WS 消息合并为一次更新
 * - 消息缓冲队列管理(BUFFER_WINDOW=300ms)
 * - flush 机制确保队列在窗口结束后统一处理
 *
 * @author 陈懋任
 * @since 1.1.0
 */
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getRooms } from '@/api/game'

export function useLobby() {
  const rooms = ref([])
  const filteredRooms = ref([])
  const loading = ref(false)
  const loadError = ref('')
  const isFirstLoad = ref(true)
  const autoRefreshTimer = ref(null)

  // ── 房间搜索防抖 ──
  const roomSearchQuery = ref('')
  const debouncedSearchQuery = ref('')
  let searchDebounceTimer = null

  // ── 排序控制 ──
  const sortBy = ref('default')
  const sortAsc = ref(true)

  // ── WebSocket 消息缓冲系统 ──
  /**
   * 消息缓冲队列：短时间内多条 WS 消息合并为一次更新
   * 缓冲窗口：300ms
   * 适用场景：大厅房间列表频繁变动（创建/加入/离开），防止频繁 set rooms 触发大量 re-render
   */
  const MSG_BUFFER_WINDOW = 300 // 消息缓冲窗口（ms）
  let messageBufferQueue = []    // 缓冲队列
  let messageBufferTimer = null  // 缓冲定时器

  /**
   * 将 WebSocket 消息加入缓冲队列
   * 在短时间内（BUFFER_WINDOW 内）的多条消息会被合并为一次更新
   * @param {string} type - 消息类型（'create' | 'update' | 'delete'）
   * @param {object} payload - 消息数据（房间对象或房间ID）
   */
  const enqueueWSMessage = (type, payload) => {
    messageBufferQueue.push({ type, payload, timestamp: Date.now() })
    // 如果缓冲定时器未启动，启动它
    if (!messageBufferTimer) {
      messageBufferTimer = setTimeout(() => {
        flushMessageBuffer()
      }, MSG_BUFFER_WINDOW)
    }
  }

  /**
   * 刷新缓冲队列：将队列中的所有消息合并执行一次更新
   * 合并策略：
   * - create: 新增房间（去重）
   * - update: 更新房间信息（找 roomNo 匹配后覆盖）
   * - delete: 移除房间
   * 同 roomNo 的多条消息以最后一次操作为准
   */
  const flushMessageBuffer = () => {
    if (messageBufferQueue.length === 0) {
      messageBufferTimer = null
      return
    }

    // 用 Map 聚合去重：key=roomNo, value={type, payload}
    const mergedMap = new Map()
    for (const msg of messageBufferQueue) {
      const roomNo = msg.payload?.roomNo || msg.payload
      if (roomNo) {
        mergedMap.set(String(roomNo), msg)
      }
    }
    messageBufferQueue = []

    // 执行合并后的更新
    const currentRooms = [...rooms.value]
    for (const [, msg] of mergedMap) {
      const roomNo = String(msg.payload?.roomNo || msg.payload)
      const existIdx = currentRooms.findIndex(r => String(r.roomNo) === roomNo)
      if (msg.type === 'delete') {
        if (existIdx !== -1) currentRooms.splice(existIdx, 1)
      } else if (msg.type === 'create') {
        if (existIdx === -1) {
          currentRooms.push(msg.payload)
        }
      } else if (msg.type === 'update') {
        if (existIdx !== -1) {
          currentRooms[existIdx] = { ...currentRooms[existIdx], ...msg.payload }
        } else {
          currentRooms.push(msg.payload)
        }
      }
    }
    rooms.value = currentRooms

    messageBufferTimer = null
    // 如果队列在 flush 期间又有新消息，重新启动定时器
    if (messageBufferQueue.length > 0) {
      messageBufferTimer = setTimeout(() => {
        flushMessageBuffer()
      }, MSG_BUFFER_WINDOW)
    }
  }

  /**
   * 强制立即刷新缓冲队列（用于页面卸载或手动刷新前）
   */
  const forceFlushMessageBuffer = () => {
    if (messageBufferTimer) {
      clearTimeout(messageBufferTimer)
      messageBufferTimer = null
    }
    flushMessageBuffer()
  }

  /**
   * 清空消息缓冲队列（用于重置状态）
   */
  const clearMessageBuffer = () => {
    if (messageBufferTimer) {
      clearTimeout(messageBufferTimer)
      messageBufferTimer = null
    }
    messageBufferQueue = []
  }

  // ── 虚拟滚动窗口复用计算 ──
  /**
   * 虚拟滚动：只渲染可见区域的房间卡片
   * 在 sortedRooms 之上增加窗口切片，Lobby.vue 的 virtualVisibleRooms 复用此逻辑
   */
  const virtualScrollWindow = ref({ start: 0, end: 50 })
  const ITEM_HEIGHT = 100     // 每个房间卡片高度
  const OVER_SCAN_COUNT = 5    // 上下额外渲染数量
  const WINDOW_SIZE = 50       // 默认窗口大小

  /**
   * 更新虚拟滚动窗口
   * @param {number} scrollTop - 滚动容器 scrollTop
   * @param {number} containerHeight - 容器可视高度
   */
  const updateVirtualWindow = (scrollTop, containerHeight) => {
    const totalItems = sortedRooms.value.length
    if (totalItems === 0) {
      virtualScrollWindow.value = { start: 0, end: 0 }
      return
    }
    const rawStart = Math.floor(scrollTop / ITEM_HEIGHT) - OVER_SCAN_COUNT
    const rawEnd = Math.ceil((scrollTop + containerHeight) / ITEM_HEIGHT) + OVER_SCAN_COUNT
    const start = Math.max(0, rawStart)
    const end = Math.min(totalItems, rawEnd)
    virtualScrollWindow.value = { start, end }
  }

  /**
   * 可见窗口内的房间列表（计算属性，供模板直接使用）
   */
  const virtualVisibleRooms = computed(() => {
    const { start, end } = virtualScrollWindow.value
    return sortedRooms.value.slice(start, end)
  })

  /**
   * 虚拟滚动总高度（计算属性）
   */
  const virtualTotalHeight = computed(() => {
    return sortedRooms.value.length * ITEM_HEIGHT
  })

  /**
   * 虚拟滚动偏移量（translateY）
   */
  const virtualOffsetY = computed(() => {
    return virtualScrollWindow.value.start * ITEM_HEIGHT
  })

  /**
   * 重置虚拟滚动窗口到初始状态
   */
  const resetVirtualWindow = () => {
    virtualScrollWindow.value = { start: 0, end: WINDOW_SIZE }
  }

  /**
   * 排序后的房间列表（计算属性）
   * 当存在搜索条件时基于 filteredRooms 排序，否则基于全量 rooms
   */
  const sortedRooms = computed(() => {
    const source = filteredRooms.value.length > 0 ? filteredRooms.value : rooms.value
    if (sortBy.value === 'default') return source
    const sorted = [...source]
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

  const onSearchInput = (value) => {
    roomSearchQuery.value = value
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
    }
    searchDebounceTimer = setTimeout(() => {
      debouncedSearchQuery.value = value
      applyRoomFilter()
      resetVirtualWindow()
    }, 300)
  }

  const applyRoomFilter = () => {
    const query = debouncedSearchQuery.value.trim()
    if (!query) {
      filteredRooms.value = []
      return
    }
    filteredRooms.value = rooms.value.filter(room => {
      return String(room.roomNo).includes(query)
    })
  }

  const applySorting = () => {
    resetVirtualWindow()
  }

  const toggleSortDirection = () => {
    sortAsc.value = !sortAsc.value
  }

  const fetchRooms = async () => {
    // 在拉取新数据前强制刷新缓冲队列，确保数据一致
    forceFlushMessageBuffer()
    loading.value = true
    loadError.value = ''
    try {
      const response = await getRooms()
      const roomList = response.data || response || []
      rooms.value = roomList.map(room => ({
        id: room.id || room.roomNo,
        roomNo: room.roomNo || room.id,
        playerCount: room.playerCount || room.userCount || 0,
        status: room.status !== undefined ? room.status : 0,
        creatorId: room.creatorId
      }))
      // 重置虚拟滚动窗口
      resetVirtualWindow()
      // 处理空状态
      if (rooms.value.length === 0 && debouncedSearchQuery.value) {
        ElMessage.info('未找到匹配的房间，请尝试其他房间号')
      }
      isFirstLoad.value = false
    } catch (err) {
      console.error('获取房间列表失败:', err)
      loadError.value = '获取房间列表失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  /** 出错时重试获取房间列表 */
  const retryFetchRooms = () => {
    setTimeout(() => {
      fetchRooms()
    }, 300)
  }

  /** 清除搜索条件并刷新列表 */
  const clearSearchAndRefresh = () => {
    roomSearchQuery.value = ''
    debouncedSearchQuery.value = ''
    filteredRooms.value = []
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
      searchDebounceTimer = null
    }
    resetVirtualWindow()
    fetchRooms()
  }

  /** 启动后台定时刷新 */
  const startAutoRefresh = () => {
    stopAutoRefresh()
    autoRefreshTimer.value = setInterval(async () => {
      isFirstLoad.value = false
      loading.value = true
      try {
        const response = await getRooms()
        const roomList = response.data || response || []
        rooms.value = roomList.map(room => ({
          id: room.id || room.roomNo,
          roomNo: room.roomNo || room.id,
          playerCount: room.playerCount || room.userCount || 0,
          status: room.status !== undefined ? room.status : 0,
          creatorId: room.creatorId
        }))
        resetVirtualWindow()
      } catch (e) {
        console.error('刷新房间列表失败:', e)
      } finally {
        loading.value = false
      }
    }, 10000)
  }

  /** 停止定时刷新 */
  const stopAutoRefresh = () => {
    if (autoRefreshTimer.value) {
      clearInterval(autoRefreshTimer.value)
      autoRefreshTimer.value = null
    }
  }

  // 组件卸载时自动清理
  onBeforeUnmount(() => {
    stopAutoRefresh()
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
      searchDebounceTimer = null
    }
    clearMessageBuffer()
    forceFlushMessageBuffer()
  })

  return {
    // 状态
    rooms,
    filteredRooms,
    sortedRooms,
    loading,
    loadError,
    isFirstLoad,
    roomSearchQuery,
    debouncedSearchQuery,
    sortBy,
    sortAsc,
    autoRefreshTimer,

    // 方法
    fetchRooms,
    retryFetchRooms,
    clearSearchAndRefresh,
    onSearchInput,
    applySorting,
    toggleSortDirection,
    startAutoRefresh,
    stopAutoRefresh,

    // WebSocket 消息缓冲
    enqueueWSMessage,
    forceFlushMessageBuffer,
    clearMessageBuffer,

    // 虚拟滚动窗口复用
    virtualScrollWindow,
    virtualVisibleRooms,
    virtualTotalHeight,
    virtualOffsetY,
    updateVirtualWindow,
    resetVirtualWindow
  }
}
