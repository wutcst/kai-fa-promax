import { ref, computed } from 'vue'
import { getPlayerStatistics, getPlayerRecords, getPlayerTrend } from '@/api/game'

/**
 * 玩家统计组合式函数
 * 封装胜率统计、战绩分页、筛选逻辑
 *
 * 用法：
 *   const { playerStatistics, recordList, ... } = usePlayerStats()
 *   onMounted(() => { fetchPlayerStatistics(); fetchPlayerRecords() })
 */
export function usePlayerStats() {
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

  // 筛选条件
  const filterTimeRange = ref('all')
  const filterResult = ref('all')
  const filterStartDate = ref('')
  const filterEndDate = ref('')

  // 趋势数据
  const playerTrend = ref({
    streakCount: 0,
    streakType: 'none',
    maxWinStreak: 0,
    maxLoseStreak: 0,
    recentResults: [],
    dailyStats: [],
    hourlyDistribution: []
  })

  // ===== 计算属性 =====

  /** 计算胜率 */
  const winRate = computed(() => {
    if (!playerStatistics.value.totalGames) return 0
    return playerStatistics.value.winRate ||
      Math.round((playerStatistics.value.winGames / playerStatistics.value.totalGames) * 100)
  })

  /** 计算总页数 */
  const totalPages = computed(() => {
    return Math.max(1, Math.ceil(totalRecords.value / pageSize.value))
  })

  // ===== 数据请求 =====

  /** 获取玩家统计信息 */
  const fetchPlayerStatistics = async () => {
    try {
      const response = await getPlayerStatistics()
      console.log('获取玩家统计信息:', response)
      playerStatistics.value = response
    } catch (error) {
      console.error('获取玩家统计信息失败：', error)
    }
  }

  /** 获取玩家趋势统计 */
  const fetchPlayerTrend = async () => {
    try {
      const response = await getPlayerTrend()
      console.log('获取玩家趋势统计:', response)
      if (response) {
        playerTrend.value = response
      }
    } catch (error) {
      console.error('获取玩家趋势统计失败：', error)
    }
  }

  /** 获取玩家战绩记录 */
  const fetchPlayerRecords = async (page = 1) => {
    try {
      console.log('开始获取玩家战绩记录...')
      const params = buildQueryParams(page)
      const response = await getPlayerRecords(params)
      console.log('API响应:', response)

      const { records, total } = response
      recordList.value = (records || []).map(record => ({
        ...record,
        showDetail: false
      }))
      totalRecords.value = total || 0
      currentPage.value = page
    } catch (error) {
      console.error('获取玩家战绩记录失败：', error)
    }
  }

  // ===== 筛选逻辑 =====

  /** 构建查询参数 */
  const buildQueryParams = (page) => {
    const params = { page, pageSize: pageSize.value }

    if (filterTimeRange.value !== 'all') {
      if (filterTimeRange.value === 'custom') {
        if (filterStartDate.value) params.startDate = filterStartDate.value
        if (filterEndDate.value) params.endDate = filterEndDate.value
      } else {
        params.timeRange = filterTimeRange.value
      }
    }

    if (filterResult.value !== 'all') {
      params.result = filterResult.value
    }

    return params
  }

  /** 筛选条件变更（重置到第一页） */
  const onFilterChange = () => {
    currentPage.value = 1
    fetchPlayerRecords(1)
  }

  /** 重置筛选条件 */
  const resetFilters = () => {
    filterTimeRange.value = 'all'
    filterResult.value = 'all'
    filterStartDate.value = ''
    filterEndDate.value = ''
    onFilterChange()
  }

  // ===== 分页逻辑 =====

  /** 分页切换 */
  const changePage = (page) => {
    if (page < 1 || page > totalPages.value) return
    fetchPlayerRecords(page)
  }

  return {
    // 状态
    playerStatistics,
    recordList,
    currentPage,
    pageSize,
    totalRecords,
    filterTimeRange,
    filterResult,
    filterStartDate,
    filterEndDate,
    // 计算属性
    winRate,
    totalPages,
    // 方法
    fetchPlayerStatistics,
    fetchPlayerRecords,
    buildQueryParams,
    onFilterChange,
    resetFilters,
    changePage,
    // 趋势数据
    playerTrend,
    fetchPlayerTrend
  }
}
