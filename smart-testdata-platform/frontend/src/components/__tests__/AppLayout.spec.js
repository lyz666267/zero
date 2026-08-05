import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLayout from '@/components/AppLayout.vue'

const { push, logout } = vi.hoisted(() => ({ push: vi.fn(), logout: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard' }),
  useRouter: () => ({ push })
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ nickname: 'tester', logout })
}))

describe('AppLayout', () => {
  it('renders the app shell and signs out', async () => {
    const wrapper = mount(AppLayout, {
      global: {
        stubs: {
          RouterView: true,
          'el-container': { template: '<div class="el-container"><slot /></div>' },
          'el-header': { template: '<header class="el-header"><slot /></header>' },
          'el-aside': { template: '<aside class="el-aside"><slot /></aside>' },
          'el-main': { template: '<main class="el-main"><slot /></main>' },
          'el-menu': { template: '<nav><slot /></nav>' },
          'el-menu-item': { template: '<a><slot /></a>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-button': {
            template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
          },
          DataAnalysis: true,
          FolderOpened: true,
          Coin: true,
          MagicStick: true,
          List: true,
          Monitor: true,
          Connection: true,
          Lock: true,
          TrendCharts: true,
          DataBoard: true,
          Download: true
        }
      }
    })

    expect(wrapper.find('.layout-header').exists()).toBe(true)
    expect(wrapper.find('.layout-aside').exists()).toBe(true)
    expect(wrapper.text()).toContain('tester')

    await wrapper.find('.header-right button').trigger('click')

    expect(logout).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/login')
  })
})
