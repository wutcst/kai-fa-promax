/**
 * Lobby.vue API Mock 配置
 *
 * 定义大厅页面组件所需的 API mock 响应数据，
 * 供单元测试和开发调试使用。
 */
export default {
  // 房间列表 Mock
  rooms: [
    { id: 1, roomNo: '100001', status: 0, playerCount: 2, userCount: 2, creatorId: 1 },
    { id: 2, roomNo: '100002', status: 1, playerCount: 4, userCount: 4, creatorId: 2 },
    { id: 3, roomNo: '100003', status: 0, playerCount: 0, userCount: 0, creatorId: 3 },
  ],

  // 创建房间响应
  createRoomResponse: {
    roomNo: '100004',
    message: '房间创建成功',
  },

  // 加入房间响应
  joinRoomResponse: {
    roomNo: '100001',
    playerId: 999,
    seatIndex: 3,
    message: '加入房间成功',
  },

  // 匹配结果响应
  matchResultResponse: {
    roomNo: '100005',
  },

  // 匹配队列状态
  matchStatus: {
    inQueue: false,
  },
}
