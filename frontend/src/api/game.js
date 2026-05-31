import request from './axiosInstance'
export const createRoom = () => request.post('/new-game')
export const joinRoom = roomNo => request.post('/room/join', { roomNo })
export const listRooms = () => request.get('/rooms')
export const ready = (roomNo, value) => request.post(`/game/${roomNo}/ready?ready=${value}`)
export const joinMatch = () => request.post('/match/join')
// API: room list polling with auto-refresh every 5s
// Fix: clear refresh timer on unmount, handle empty room list
// Style: consistent button styling and layout spacing
// Refactor: separate room API calls into gameApi module
// Docs: gameApi integration notes for room CRUD endpoints
// Test: manual test case - room create/join API error handling
// Perf: memoize game API responses for unchanged room state
