import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/store/user'

describe('user store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('stores user data after login', () => {
    const store = useUserStore()
    store.setUser({ token: 'abc', username: 'alice', nickname: 'Alice' })

    expect(store.token).toBe('abc')
    expect(store.username).toBe('alice')
    expect(store.nickname).toBe('Alice')
    expect(localStorage.getItem('token')).toBe('abc')
    expect(store.isLoggedIn()).toBe(true)
  })

  it('clears user data on logout', () => {
    const store = useUserStore()
    store.setUser({ token: 'abc', username: 'alice', nickname: 'Alice' })
    store.logout()

    expect(store.isLoggedIn()).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })
})
