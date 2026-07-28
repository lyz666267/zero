import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器 — 添加 JWT Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器 — 统一错误处理
request.interceptors.response.use(
  response => response.data,
  error => {
    const data = error.response?.data
    const msg = data?.message || error.message || '请求失败'
    ElMessage.error(msg)
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('nickname')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request
