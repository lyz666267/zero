import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    meta: { requireAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/ProjectList.vue'),
        meta: { title: '项目管理' }
      },
      {
        path: 'datasources',
        name: 'DatasourceManage',
        component: () => import('@/views/DatasourceManage.vue'),
        meta: { title: '数据源管理' }
      },
      {
        path: 'testdata',
        name: 'TestDataGenerate',
        component: () => import('@/views/TestDataGenerate.vue'),
        meta: { title: '测试数据生成' }
      },
      {
        path: 'testdata/task',
        name: 'TestDataTask',
        component: () => import('@/views/TestDataTask.vue'),
        meta: { title: '创建生成任务' }
      },
      {
        path: 'task-monitor',
        name: 'TaskMonitor',
        component: () => import('@/views/TaskMonitor.vue'),
        meta: { title: '任务监控' }
      },
      {
        path: 'testdata/result',
        name: 'TestDataResult',
        component: () => import('@/views/TestDataResult.vue'),
        meta: { title: '生成结果' }
      },
      {
        path: 'schema/view',
        name: 'SchemaView',
        component: () => import('@/views/SchemaView.vue'),
        meta: { title: 'Schema 结构' }
      },
      {
        path: 'schema/relation',
        name: 'RelationGraph',
        component: () => import('@/views/RelationGraph.vue'),
        meta: { title: '数据库关系图' }
      },
      {
        path: 'testdata/plan',
        name: 'GenerationPlan',
        component: () => import('@/views/GenerationPlan.vue'),
        meta: { title: 'AI 生成计划' }
      },
      {
        path: 'agent-trace',
        name: 'AgentTrace',
        component: () => import('@/views/AgentTrace.vue'),
        meta: { title: 'Agent 执行轨迹' }
      },
      {
        path: 'privacy',
        name: 'MaskConfig',
        component: () => import('@/views/MaskConfig.vue'),
        meta: { title: '隐私脱敏配置' }
      },
      {
        path: 'data-quality',
        name: 'DataQuality',
        component: () => import('@/views/DataQuality.vue'),
        meta: { title: '数据质量评分' }
      },
      {
        path: 'database-mask',
        name: 'DatabaseMask',
        component: () => import('@/views/DatabaseMask.vue'),
        meta: { title: '数据库脱敏' }
      },
      {
        path: 'data-export',
        name: 'DataExport',
        component: () => import('@/views/DataExport.vue'),
        meta: { title: '数据导出' }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  // to.matched 会包含所有匹配的路由（父 + 子），检查是否有 requireAuth
  const requiresAuth = to.matched.some(record => record.meta.requireAuth)
  if (requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录用户访问登录页，重定向到工作台
    next('/dashboard')
  } else {
    next()
  }
})

export default router
