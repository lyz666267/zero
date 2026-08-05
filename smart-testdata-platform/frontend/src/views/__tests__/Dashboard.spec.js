import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Dashboard from '@/views/Dashboard.vue'

// ---------- mocks ----------
const { push, getDashboardStats } = vi.hoisted(() => ({
  push: vi.fn(),
  getDashboardStats: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

vi.mock('@/api/project', () => ({
  getDashboardStats: (...args) => getDashboardStats(...args)
}))

// ---------- helpers ----------
function factory(statsOverride) {
  const defaultStats = {
    projectCount: 12,
    taskCount: 156,
    successTaskCount: 143
  }
  getDashboardStats.mockResolvedValue({ data: statsOverride ?? defaultStats })

  return mount(Dashboard, {
    global: {
      stubs: { RouterView: true }
    }
  })
}

// ---------- tests ----------
describe('Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('page rendering', () => {
    it('renders the stat cards section', async () => {
      const wrapper = factory()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      // With real Element Plus, stat cards use el-card components
      const cards = wrapper.findAll('.el-card')
      expect(cards.length).toBeGreaterThanOrEqual(3)
    })

    it('renders exactly three stat cards', async () => {
      const wrapper = factory()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      // Find el-card instances rendered within el-row's el-col
      const statRows = wrapper.findAll('.stat-card')
      // Each of the 3 el-col wraps an el-card with class stat-card
      expect(statRows.length).toBe(3)
    })
  })

  describe('stat cards display', () => {
    it('displays project count', async () => {
      const wrapper = factory({ projectCount: 42, taskCount: 0, successTaskCount: 0 })
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      expect(wrapper.text()).toContain('42')
      expect(wrapper.text()).toContain('项目数')
    })

    it('displays task count', async () => {
      const wrapper = factory({ projectCount: 0, taskCount: 88, successTaskCount: 0 })
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      expect(wrapper.text()).toContain('88')
      expect(wrapper.text()).toContain('任务总数')
    })

    it('displays success task count', async () => {
      const wrapper = factory({ projectCount: 0, taskCount: 0, successTaskCount: 77 })
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      expect(wrapper.text()).toContain('77')
      expect(wrapper.text()).toContain('成功任务')
    })

    it('displays all three stat values simultaneously', async () => {
      const wrapper = factory()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      expect(wrapper.text()).toContain('12')
      expect(wrapper.text()).toContain('156')
      expect(wrapper.text()).toContain('143')
    })
  })

  describe('quick actions', () => {
    it('renders the quick actions card with header', async () => {
      const wrapper = factory()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      expect(wrapper.text()).toContain('快捷操作')
      expect(wrapper.text()).toContain('新建项目')
    })

    it('has a button linking to projects page', async () => {
      const wrapper = factory()
      await flushPromises()
      await new Promise((r) => setTimeout(r, 100))

      // With real Element Plus, the button renders as an el-button
      const buttons = wrapper.findAll('button')
      const newProjectBtn = buttons.find((b) => b.text().includes('新建项目'))
      expect(newProjectBtn).toBeTruthy()
    })
  })

  describe('data fetching', () => {
    it('calls getDashboardStats on mount', () => {
      factory()
      expect(getDashboardStats).toHaveBeenCalledTimes(1)
    })

    it('uses default zero values before API resolves', () => {
      getDashboardStats.mockReturnValue(new Promise(() => {})) // never resolves
      const wrapper = mount(Dashboard, {
        global: {
          stubs: { RouterView: true }
        }
      })

      expect(wrapper.vm.stats).toEqual({
        projectCount: 0,
        taskCount: 0,
        successTaskCount: 0
      })
    })
  })
})
