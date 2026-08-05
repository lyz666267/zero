import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Login from '@/views/Login.vue'

// ---------- mocks ----------
const { push, setUser, loginApi, registerApi } = vi.hoisted(() => ({
  push: vi.fn(),
  setUser: vi.fn(),
  loginApi: vi.fn(),
  registerApi: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

vi.mock('@/api/auth', () => ({
  login: (...args) => loginApi(...args),
  register: (...args) => registerApi(...args)
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ setUser })
}))

// ---------- helpers ----------
function factory() {
  return mount(Login, {
    global: {
      stubs: { RouterView: true }
    }
  })
}

// ---------- tests ----------
describe('Login.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('form rendering', () => {
    it('renders the platform title and subtitle', () => {
      const wrapper = factory()
      expect(wrapper.text()).toContain('智能测试数据生成平台')
      expect(wrapper.text()).toContain('基于大模型 Agent 的测试数据生成与隐私脱敏')
    })

    it('renders login and register tabs', () => {
      const wrapper = factory()
      // Tab labels are rendered by Element Plus
      expect(wrapper.find('.el-tabs').exists()).toBe(true)
      // The login tab pane has label="登录"
      const tabs = wrapper.findAll('.el-tabs__item')
      const tabTexts = tabs.map((t) => t.text())
      expect(tabTexts.some((t) => t === '登录')).toBe(true)
      expect(tabTexts.some((t) => t === '注册')).toBe(true)
    })

    it('renders username and password inputs in the login form', () => {
      const wrapper = factory()
      // el-input renders an <input> inside it with the placeholder
      const inputs = wrapper.findAll('input')
      const placeholders = inputs.map((i) => i.attributes('placeholder'))
      expect(placeholders).toContain('请输入用户名')
      expect(placeholders).toContain('请输入密码')
    })

    it('renders the login submit button', () => {
      const wrapper = factory()
      // Button text has a space: "登 录"
      const buttons = wrapper.findAll('button')
      const loginBtn = buttons.find((b) => b.text().includes('登'))
      expect(loginBtn).toBeTruthy()
    })
  })

  describe('validation rules', () => {
    it('username rule is required', () => {
      const wrapper = factory()
      // Access reactive loginRules from the component
      expect(wrapper.vm.loginRules.username[0].required).toBe(true)
      expect(wrapper.vm.loginRules.username[0].message).toBe('请输入用户名')
    })

    it('password rule is required', () => {
      const wrapper = factory()
      expect(wrapper.vm.loginRules.password[0].required).toBe(true)
      expect(wrapper.vm.loginRules.password[0].message).toBe('请输入密码')
    })

    it('form ref is available for validation', () => {
      const wrapper = factory()
      expect(wrapper.vm.loginFormRef).toBeDefined()
    })
  })

  describe('login button trigger', () => {
    it('calls login API with form data on success', async () => {
      loginApi.mockResolvedValue({
        data: { token: 'tok-123', username: 'alice', nickname: 'Alice' }
      })

      const wrapper = factory()

      // Fill username and password
      const usernameInput = wrapper.find('input[placeholder="请输入用户名"]')
      const passwordInput = wrapper.find('input[placeholder="请输入密码"]')
      await usernameInput.setValue('alice')
      await passwordInput.setValue('password123')

      // Click the login button "登 录"
      const buttons = wrapper.findAll('button')
      const submitBtn = buttons.find((b) => b.text().includes('登'))
      expect(submitBtn).toBeTruthy()

      await submitBtn.trigger('click')

      // Wait for form validation + async login call
      await flushPromises()
      await new Promise((r) => setTimeout(r, 200))

      expect(loginApi).toHaveBeenCalledWith({
        username: 'alice',
        password: 'password123'
      })
    })

    it('sets loading true during request', async () => {
      let resolvePromise
      const deferred = new Promise((resolve) => {
        resolvePromise = resolve
      })
      loginApi.mockReturnValue(deferred)

      const wrapper = factory()

      const usernameInput = wrapper.find('input[placeholder="请输入用户名"]')
      const passwordInput = wrapper.find('input[placeholder="请输入密码"]')
      await usernameInput.setValue('alice')
      await passwordInput.setValue('password123')

      const buttons = wrapper.findAll('button')
      const submitBtn = buttons.find((b) => b.text().includes('登'))
      await submitBtn.trigger('click')

      await flushPromises()
      await new Promise((r) => setTimeout(r, 200))

      expect(wrapper.vm.loading).toBe(true)

      // Resolve
      resolvePromise({ data: { token: 'tok', username: 'x', nickname: 'x' } })
      await flushPromises()
      await new Promise((r) => setTimeout(r, 200))

      expect(wrapper.vm.loading).toBe(false)
    })

    it('calls setUser and navigates after successful login', async () => {
      loginApi.mockResolvedValue({
        data: { token: 'tok-abc', username: 'bob', nickname: 'Bob' }
      })

      const wrapper = factory()

      await wrapper.find('input[placeholder="请输入用户名"]').setValue('bob')
      await wrapper.find('input[placeholder="请输入密码"]').setValue('pass')

      const buttons = wrapper.findAll('button')
      const submitBtn = buttons.find((b) => b.text().includes('登'))
      await submitBtn.trigger('click')

      await flushPromises()
      await new Promise((r) => setTimeout(r, 200))

      expect(setUser).toHaveBeenCalledWith({
        token: 'tok-abc',
        username: 'bob',
        nickname: 'Bob'
      })
      expect(push).toHaveBeenCalledWith('/dashboard')
    })
  })
})
