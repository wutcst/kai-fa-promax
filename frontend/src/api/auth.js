import apiClient from './axiosInstance'

export const login = (params) => {
  return apiClient.post('/login', params)
}

export const register = (params) => {
  return apiClient.post('/register', params)
}

export const logout = () => {
  return apiClient.post('/user/logout')
}

export const getUserInfo = () => {
  return apiClient.get('/user/info')
}
