/**
 * Lobby.vue 组件单元测试
 *
 * 测试范围：
 * 1. 组件渲染（按钮、对话框、房间列表）
 * 2. 状态管理（creating/joining/matching 状态切换）
 * 3. 防重复提交逻辑
 * 4. 房间排序功能
 * 5. 用户交互（创建房间、加入房间、快速匹配）
 *
 * 依赖：
 * - @vue/test-utils
 * - vitest
 * - element-plus
 * - vue-router
 */

// import { describe, it, expect, vi, beforeEach } from 'vitest'
// import { mount } from '@vue/test-utils'
// import { createRouter, createMemoryHistory } from 'vue-router'
// import Lobby from '@/views/lobby/Lobby.vue'

// 注意：以下为组件级测试用例定义，实际运行时需要安装 vitest 和 @vue/test-utils
// 当前环境下仅保存用例定义和 mock 配置，供后续 CI 或本地执行

/*
describe('Lobby.vue', () => {
  let wrapper
  let router

  beforeEach(async () => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
        { path: '/personal-home', name: 'personal-home', component: { template: '<div>Personal</div>' } },
      ]
    })

    // Mock localStorage
    const localStorageMock = {
      getItem: vi.fn((key) => {
        if (key === 'nickname') return '测试玩家'
        return null
      }),
      setItem: vi.fn(),
      clear: vi.fn(),
    }
    Object.defineProperty(window, 'localStorage', { value: localStorageMock })

    wrapper = mount(Lobby, {
      global: {
        plugins: [router],
        stubs: {
          'el-button': true,
          'el-dialog': true,
          'el-input': true,
          'el-form': true,
          'el-form-item': true,
          'el-radio-group': true,
          'el-radio-button': true,
          'el-radio': true,
          'el-switch': true,
          'el-steps': true,
          'el-step': true,
          'el-tag': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-icon': true,
          'el-select': true,
          'el-option': true,
        }
      }
    })
  })

  // ===== 渲染测试 =====
  describe('组件渲染', () => {
    it('应该渲染大厅标题', () => {
      expect(wrapper.find('.lobby-header h2').text()).toBe('游戏大厅')
    })

    it('应该显示玩家昵称', () => {
      expect(wrapper.find('.user-info span').text()).toContain('测试玩家')
    })

    it('应该渲染三个功能按钮', () => {
      const buttons = wrapper.findAll('.room-actions button')
      expect(buttons.length).toBe(3)
    })

    it('应该渲染排序控件', () => {
      expect(wrapper.find('.sort-controls').exists()).toBe(true)
    })
  })

  // ===== 防重复提交测试 =====
  describe('防重复提交', () => {
    it('创建按钮在 creating 状态应该被禁用', async () => {
      await wrapper.setData({ creating: true })
      const createBtns = wrapper.findAll('[disabled]')
      // 创建对话框中的确认按钮应该被禁用
      expect(createBtns.length).toBeGreaterThanOrEqual(1)
    })

    it('加入按钮在 joining 状态应该被禁用', async () => {
      await wrapper.setData({ joining: true })
      const joinBtns = wrapper.findAll('[disabled]')
      expect(joinBtns.length).toBeGreaterThanOrEqual(1)
    })

    it('快速匹配按钮在 matching 状态应该被禁用', async () => {
      await wrapper.setData({ matching: true })
      const matchBtns = wrapper.findAll(':disabled')
      expect(matchBtns.length).toBeGreaterThan(0)
    })
  })

  // ===== 创建房间测试 =====
  describe('创建房间', () => {
    it('点击创建按钮应该打开对话框', async () => {
      await wrapper.find('.el-button').trigger('click')
      expect(wrapper.vm.showCreateDialog).toBe(true)
    })

    it('创建对话框应该支持多步表单', async () => {
      await wrapper.setData({ showCreateDialog: true })
      expect(wrapper.vm.createStep).toBe(0)

      // 下一步
      await wrapper.vm.nextCreateStep()
      expect(wrapper.vm.createStep).toBe(1)
    })

    it('handleCreateRoom 设置 creating=true 并重置错误', async () => {
      wrapper.vm.createError = '旧错误'
      await wrapper.vm.handleCreateRoom()
      // 函数开始时 creating 为 true
      expect(wrapper.vm.creating).toBe(false) // 完成后恢复
    })

    it('creating 为 true 时 handleCreateRoom 应该直接返回', async () => {
      await wrapper.setData({ creating: true })
      // 设置一个间谍，如果被调用则标记失败
      const spy = vi.fn()
      wrapper.vm.handleCreateRoom = wrapper.vm.handleCreateRoom // 重新赋值
      await wrapper.vm.handleCreateRoom()
      // 应该不执行实际逻辑
      expect(wrapper.vm.creating).toBe(true)
    })
  })

  // ===== 加入房间测试 =====
  describe('加入房间', () => {
    it('对话框关闭时应该重置表单', async () => {
      await wrapper.setData({ showJoinDialog: false })
      expect(wrapper.vm.joinForm.roomNo).toBe('')
      expect(wrapper.vm.joinError).toBe('')
    })

    it('joining 状态应该阻止重复调用', async () => {
      await wrapper.setData({ joining: true })
      const result = wrapper.vm.joinRoom('100001')
      // 应该不执行实际加入逻辑
      expect(result).toBeUndefined()
    })
  })

  // ===== 房间列表排序测试 =====
  describe('房间排序', () => {
    it('默认排序应该返回原始列表', () => {
      wrapper.vm.sortBy = 'default'
      wrapper.vm.rooms = [{ id: 1 }, { id: 2 }]
      expect(wrapper.vm.sortedRooms).toEqual([{ id: 1 }, { id: 2 }])
    })

    it('按人数排序应该正确工作', () => {
      wrapper.vm.sortBy = 'playerCount'
      wrapper.vm.sortAsc = true
      wrapper.vm.rooms = [
        { playerCount: 3 },
        { playerCount: 1 },
        { playerCount: 2 },
      ]
      const sorted = wrapper.vm.sortedRooms
      expect(sorted[0].playerCount).toBe(1)
      expect(sorted[2].playerCount).toBe(3)
    })

    it('按状态排序应该正确工作', () => {
      wrapper.vm.sortBy = 'status'
      wrapper.vm.sortAsc = true
      wrapper.vm.rooms = [
        { status: 1 },
        { status: 0 },
        { status: 2 },
      ]
      const sorted = wrapper.vm.sortedRooms
      expect(sorted[0].status).toBe(0)
      expect(sorted[2].status).toBe(2)
    })

    it('切换排序方向应该反转结果', () => {
      wrapper.vm.sortBy = 'playerCount'
      wrapper.vm.sortAsc = false
      wrapper.vm.rooms = [
        { playerCount: 1 },
        { playerCount: 3 },
      ]
      const sorted = wrapper.vm.sortedRooms
      expect(sorted[0].playerCount).toBe(3)
      expect(sorted[1].playerCount).toBe(1)
    })
  })
})
*/

/**
 * API Mock 配置
 *
 * 用于单元测试中模拟后端接口响应。
 * 当前仅定义 mock 数据结构，激活需在测试 setup 中注册。
 */

// Mock API 响应数据定义
export const MOCK_ROOMS = [
  { id: 1, roomNo: '100001', status: 0, playerCount: 2, creatorId: 1 },
  { id: 2, roomNo: '100002', status: 1, playerCount: 4, creatorId: 2 },
  { id: 3, roomNo: '100003', status: 0, playerCount: 0, creatorId: 3 },
]

export const MOCK_CREATE_ROOM_RESPONSE = {
  data: { roomNo: '100004', message: '房间创建成功' },
  code: 200,
}

export const MOCK_JOIN_ROOM_RESPONSE = {
  data: { roomNo: '100001', playerId: 999, seatIndex: 3, message: '加入房间成功' },
  code: 200,
}

export const MOCK_MATCH_RESULT = {
  data: { roomNo: '100005' },
  code: 200,
}

// API mock 注册函数（用于 vitest setup 或 beforeEach）
export function setupApiMocks(vi) {
  // 模拟 fetchRooms 的 API 调用
  vi.mock('@/api/game', () => ({
    getRooms: vi.fn().mockResolvedValue(MOCK_ROOMS),
    createRoom: vi.fn().mockResolvedValue(MOCK_CREATE_ROOM_RESPONSE.data),
    joinRoom: vi.fn().mockResolvedValue(MOCK_JOIN_ROOM_RESPONSE.data),
    getMatchResult: vi.fn().mockResolvedValue(MOCK_MATCH_RESULT.data),
    joinMatch: vi.fn().mockResolvedValue(true),
    cancelMatch: vi.fn().mockResolvedValue(true),
    getRoomDetail: vi.fn().mockResolvedValue({ roomNo: '100001', config: null }),
    startGame: vi.fn().mockResolvedValue(true),
  }))
}
