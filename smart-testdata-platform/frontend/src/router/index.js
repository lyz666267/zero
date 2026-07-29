import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '工作台', requireAuth: true }
  },
  {
    path: '/projects',
    name: 'ProjectList',
    component: () => import('@/views/ProjectList.vue'),
    meta: { title: '项目管理', requireAuth: true }
  },
  {
    path: '/datasources',
    name: 'DatasourceManage',
    component: () => import('@/views/DatasourceManage.vue'),
    meta: { title: '数据源管理', requireAuth: true }
  },
  {
    path: '/testdata',
    name: 'TestDataGenerate',
    component: () => import('@/views/TestDataGenerate.vue'),
    meta: { title: '测试数据生成', requireAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requireAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录用户访问登录页，重定向到工作台
    next('/dashboard')
  } else {
    next()
  }
})

export default router
