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
  },
  {
    path: '/testdata/task',
    name: 'TestDataTask',
    component: () => import('@/views/TestDataTask.vue'),
    meta: { title: '创建生成任务', requireAuth: true }
  },
  {
    path: '/task-monitor',
    name: 'TaskMonitor',
    component: () => import('@/views/TaskMonitor.vue'),
    meta: { title: '任务监控', requireAuth: true }
  },
  {
    path: '/testdata/result',
    name: 'TestDataResult',
    component: () => import('@/views/TestDataResult.vue'),
    meta: { title: '生成结果', requireAuth: true }
  },
  {
    path: '/schema/view',
    name: 'SchemaView',
    component: () => import('@/views/SchemaView.vue'),
    meta: { title: 'Schema 结构', requireAuth: true }
  },
  {
    path: '/schema/relation',
    name: 'RelationGraph',
    component: () => import('@/views/RelationGraph.vue'),
    meta: { title: '数据库关系图', requireAuth: true }
  },
  {
    path: '/testdata/plan',
    name: 'GenerationPlan',
    component: () => import('@/views/GenerationPlan.vue'),
    meta: { title: 'AI 生成计划', requireAuth: true }
  },
  {
    path: '/agent-trace',
    name: 'AgentTrace',
    component: () => import('@/views/AgentTrace.vue'),
    meta: { title: 'Agent 执行轨迹', requireAuth: true }
  },
  {
    path: '/privacy',
    name: 'MaskConfig',
    component: () => import('@/views/MaskConfig.vue'),
    meta: { title: '隐私脱敏配置', requireAuth: true }
  },
  {
    path: '/data-quality',
    name: 'DataQuality',
    component: () => import('@/views/DataQuality.vue'),
    meta: { title: '数据质量评分', requireAuth: true }
  },
  {
    path: '/database-mask',
    name: 'DatabaseMask',
    component: () => import('@/views/DatabaseMask.vue'),
    meta: { title: '数据库脱敏', requireAuth: true }
  },
  {
    path: '/data-export',
    name: 'DataExport',
    component: () => import('@/views/DataExport.vue'),
    meta: { title: '数据导出', requireAuth: true }
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
