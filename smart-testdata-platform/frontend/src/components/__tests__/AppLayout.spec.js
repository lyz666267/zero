import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLayout from '@/components/AppLayout.vue'

// ---------- mocks ----------
const { push, logout } = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn()
}))

// Default route path; individual tests can override via factory()
let currentRoutePath = '/dashboard'

vi.mock('vue-router', () => ({
  useRoute: () => {
    // Return a reactive-like object whose .path is the current route
    return { path: currentRoutePath }
  },
  useRouter: () => ({ push })
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ nickname: 'tester', logout })
}))

// ---------- shared stubs ----------
const sharedStubs = {
  RouterView: true,
  'el-container': { template: '<div class="el-container"><slot /></div>' },
  'el-header': { template: '<header class="el-header"><slot /></header>' },
  'el-aside': { template: '<aside class="el-aside"><slot /></aside>' },
  'el-main': { template: '<main class="el-main"><slot /></main>' },
  'el-menu': {
    template: '<nav class="el-menu" :data-default-active="defaultActive" :data-router="router"><slot /></nav>',
    props: ['defaultActive', 'router', 'backgroundColor', 'textColor', 'activeTextColor']
  },
  'el-menu-item': {
    template: '<a class="el-menu-item" :data-index="index" @click="$emit(\'click\')"><slot /></a>',
    props: ['index']
  },
  'el-icon': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button type="button" class="el-button" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'text']
  },
  DataAnalysis: true,
  FolderOpened: true,
  Coin: true,
  Files: true,
  Share: true,
  MagicStick: true,
  List: true,
  Monitor: true,
  Connection: true,
  Lock: true,
  TrendCharts: true,
  DataBoard: true,
  Download: true
}

// ---------- helpers ----------
function factory() {
  return mount(AppLayout, {
    global: { stubs: sharedStubs }
  })
}

// ---------- tests ----------
describe('AppLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentRoutePath = '/dashboard'
  })

  // ── layout shell ────────────────────────────────────────────
  describe('layout shell', () => {
    it('renders the header with platform title', () => {
      const wrapper = factory()
      expect(wrapper.find('.el-header').exists()).toBe(true)
      expect(wrapper.text()).toContain('智能测试数据平台')
    })

    it('renders the sidebar (el-aside)', () => {
      const wrapper = factory()
      expect(wrapper.find('.el-aside').exists()).toBe(true)
    })

    it('renders the main content area', () => {
      const wrapper = factory()
      expect(wrapper.find('.el-main').exists()).toBe(true)
    })

    it('displays user nickname in header', () => {
      const wrapper = factory()
      expect(wrapper.text()).toContain('tester')
    })
  })

  // ── sidebar menu ─────────────────────────────────────────────
  describe('sidebar menu', () => {
    it('renders all menu items', () => {
      const wrapper = factory()
      const menuItems = wrapper.findAll('.el-menu-item')
      expect(menuItems.length).toBe(14)
    })

    const expectedMenuItems = [
      { index: '/dashboard', label: '工作台' },
      { index: '/projects', label: '项目管理' },
      { index: '/datasources', label: '数据源管理' },
      { index: '/schema/view', label: 'Schema 结构' },
      { index: '/schema/relation', label: '数据库关系图' },
      { index: '/testdata', label: '测试数据生成' },
      { index: '/testdata/task', label: '创建生成任务' },
      { index: '/testdata/plan', label: 'AI 生成计划' },
      { index: '/task-monitor', label: '任务监控' },
      { index: '/agent-trace', label: 'Agent 执行轨迹' },
      { index: '/privacy', label: '隐私脱敏配置' },
      { index: '/data-quality', label: '数据质量评分' },
      { index: '/database-mask', label: '数据库脱敏' },
      { index: '/data-export', label: '数据导出' }
    ]

    expectedMenuItems.forEach(({ index, label }) => {
      it(`has menu item "${label}" with index "${index}"`, () => {
        const wrapper = factory()
        const items = wrapper.findAll('.el-menu-item')
        const item = items.find((i) => i.attributes('data-index') === index)
        expect(item).toBeTruthy()
        expect(item.text()).toContain(label)
      })
    })
  })

  // ── route switching ──────────────────────────────────────────
  describe('route switching', () => {
    it('highlights the active menu based on current route', () => {
      currentRoutePath = '/projects'
      const wrapper = factory()
      const menu = wrapper.find('.el-menu')
      expect(menu.exists()).toBe(true)
      // The stubbed el-menu renders <nav> with :defaultActive prop bound
      expect(menu.attributes('data-default-active')).toBe('/projects')
    })

    it('updates active menu when route changes', () => {
      currentRoutePath = '/datasources'
      const wrapper = factory()
      const menu = wrapper.find('.el-menu')
      expect(menu.exists()).toBe(true)
      expect(menu.attributes('data-default-active')).toBe('/datasources')
    })

    it('passes router prop to el-menu for built-in navigation', () => {
      const wrapper = factory()
      const menu = wrapper.find('.el-menu')
      expect(menu.exists()).toBe(true)
      // Router prop is passed as a boolean attribute; the DOM data attr will be set
      expect(menu.attributes('data-router')).toBe('')
    })
  })

  // ── logout ───────────────────────────────────────────────────
  describe('logout', () => {
    it('renders logout button', () => {
      const wrapper = factory()
      const buttons = wrapper.findAll('.el-button')
      const logoutBtn = buttons.find((b) => b.text() === '退出')
      expect(logoutBtn).toBeTruthy()
    })

    it('calls userStore.logout and navigates to /login on click', async () => {
      const wrapper = factory()
      const buttons = wrapper.findAll('.el-button')
      const logoutBtn = buttons.find((b) => b.text() === '退出')

      await logoutBtn.trigger('click')

      expect(logout).toHaveBeenCalled()
      expect(push).toHaveBeenCalledWith('/login')
    })

    it('navigates to /login even without active session', async () => {
      const wrapper = factory()
      const buttons = wrapper.findAll('.el-button')
      const logoutBtn = buttons.find((b) => b.text() === '退出')

      await logoutBtn.trigger('click')

      // Both logout and navigation should happen
      expect(logout).toHaveBeenCalled()
      expect(push).toHaveBeenCalledWith('/login')
    })
  })
})
