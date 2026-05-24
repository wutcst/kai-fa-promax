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
    // computed 自动重新计算 sortedRooms
  }

  const toggleSortDirection = () => {
    sortAsc.value = !sortAsc.value
  }

  const fetchRooms = async () => {
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
    stopAutoRefresh
  }
}
