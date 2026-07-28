import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')

  function setUser(data) {
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname || data.username)
  }

  function logout() {
    token.value = ''
    username.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
  }

  function isLoggedIn() {
    return !!token.value
  }

  return { token, username, nickname, setUser, logout, isLoggedIn }
})
