import request from './axiosInstance'

export const login = (data) => request.post('/login', data)
export const register = (data) => request.post('/register', data)
export const getUserInfo = () => request.get('/user/info')
export const checkUsername = (username) => request.get('/user/check-username', { params: { username } })
// Docs: auth API integration notes for login/register endpoints
// Test: manual test case - API error response handling
