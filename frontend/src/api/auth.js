import request from './axiosInstance'

export const login = (data) => request.post('/login', data)
export const register = (data) => request.post('/register', data)
