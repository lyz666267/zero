<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <div class="header-left">
        <h2>智能测试数据平台</h2>
      </div>
      <div class="header-right">
        <span class="user-info">{{ userStore.nickname }}</span>
        <el-button type="danger" text @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="layout-aside">
        <el-menu :default-active="activeMenu" router background-color="#304156" text-color="#bfcbd9"
          active-text-color="#409EFF">
          <el-menu-item index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/projects">
            <el-icon><FolderOpened /></el-icon>
            <span>项目管理</span>
          </el-menu-item>
          <el-menu-item index="/datasources">
            <el-icon><Coin /></el-icon>
            <span>数据源管理</span>
          </el-menu-item>
          <el-menu-item index="/testdata">
            <el-icon><MagicStick /></el-icon>
            <span>测试数据生成</span>
          </el-menu-item>
          <el-menu-item index="/testdata/task">
            <el-icon><List /></el-icon>
            <span>创建生成任务</span>
          </el-menu-item>
          <el-menu-item index="/agent-trace">
            <el-icon><Connection /></el-icon>
            <span>Agent 执行轨迹</span>
          </el-menu-item>
          <el-menu-item index="/privacy">
            <el-icon><Lock /></el-icon>
            <span>隐私脱敏配置</span>
          </el-menu-item>
          <el-menu-item index="/data-quality">
            <el-icon><TrendCharts /></el-icon>
            <span>数据质量评分</span>
          </el-menu-item>
          <el-menu-item index="/database-mask">
            <el-icon><DataBoard /></el-icon>
            <span>数据库脱敏</span>
          </el-menu-item>
          <el-menu-item index="/data-export">
            <el-icon><Download /></el-icon>
            <span>数据导出</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <div class="page-header">
          <h3>任务监控</h3>
          <div class="header-actions">
            <el-button v-if="task" type="primary" size="small" plain
              @click="$router.push(`/testdata/plan?taskId=${task.id}`)">
              <el-icon><Document /></el-icon>
              查看 AI 生成计划
            </el-button>
            <el-button v-if="task?.status === 'SUCCESS'" type="success" size="small"
              @click="$router.push(`/testdata/result?taskId=${task.id}`)">
              <el-icon><View /></el-icon>
              查看生成结果
            </el-button>
            <el-button text @click="$router.push('/testdata/task')">
              <el-icon><ArrowLeft /></el-icon>
              返回创建任务
            </el-button>
          </div>
        </div>

        <!-- 加载中 -->
        <el-card v-if="loading && !task" class="status-card">
          <div class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在加载任务信息...</p>
          </div>
        </el-card>

        <!-- 任务未找到 -->
        <el-card v-else-if="!loading && !task" class="status-card">
          <el-result icon="warning" title="未找到任务">
            <template #sub-title>请检查任务 ID 是否正确</template>
            <template #extra>
              <el-button type="primary" @click="$router.push('/testdata/task')">创建新任务</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 任务详情 -->
        <template v-if="task">
          <!-- 状态概览卡片 -->
          <el-card class="status-card">
            <template #header>
              <div class="card-header">
                <span>任务 #{{ task.id }}</span>
                <el-tag :type="statusTagType(task.status)" size="large" effect="dark">
                  {{ statusLabel(task.status) }}
                </el-tag>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="任务名称" :span="2">
                {{ task.taskName }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusTagType(task.status)" size="small">
                  {{ statusLabel(task.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="目标数量">
                {{ task.totalCount ?? '-' }} 条
              </el-descriptions-item>
              <el-descriptions-item label="成功数量">
                <span :class="task.successCount > 0 ? 'text-success' : ''">
                  {{ task.successCount ?? 0 }} 条
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="失败数量">
                <span :class="task.failCount > 0 ? 'text-danger' : ''">
                  {{ task.failCount ?? 0 }} 条
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">
                {{ formatTime(task.createTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="完成时间">
                {{ task.finishTime ? formatTime(task.finishTime) : '—' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="task.errorMessage" label="错误信息" :span="2">
                <span class="text-danger">{{ task.errorMessage }}</span>
              </el-descriptions-item>
            </el-descriptions>

            <!-- 进度条 -->
            <div v-if="task.totalCount > 0" class="progress-section">
              <el-progress
                :percentage="progressPercent"
                :status="progressStatus"
                :stroke-width="20"
                :text-inside="true"
              />
              <p class="progress-label">
                已完成 {{ finishedCount }} / {{ task.totalCount }} 条
                <template v-if="isRunning">（轮询中，每 3 秒刷新...）</template>
                <template v-else-if="isTerminal">（任务已结束，停止轮询）</template>
              </p>
            </div>
          </el-card>
        </template>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getTask } from '@/api/task'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

const task = ref(null)
const loading = ref(false)
const error = ref(false)
let pollTimer = null

/** 任务 ID（从 query 参数读取） */
const taskId = computed(() => {
  const id = Number(route.query.id)
  return Number.isNaN(id) ? null : id
})

/** 终端状态 */
const isTerminal = computed(() => {
  return task.value?.status === 'SUCCESS' || task.value?.status === 'FAILED'
})

/** 运行中状态 */
const isRunning = computed(() => {
  return task.value?.status === 'PENDING' || task.value?.status === 'RUNNING'
})

/** 已完成数量 */
const finishedCount = computed(() => {
  return (task.value?.successCount ?? 0) + (task.value?.failCount ?? 0)
})

/** 进度百分比 */
const progressPercent = computed(() => {
  if (!task.value?.totalCount || task.value.totalCount === 0) return 0
  return Math.round((finishedCount.value / task.value.totalCount) * 100)
})

/** 进度条状态 */
const progressStatus = computed(() => {
  if (task.value?.status === 'SUCCESS') return 'success'
  if (task.value?.status === 'FAILED') return 'exception'
  return ''
})

// ==================== 数据获取 ====================

onMounted(() => {
  if (taskId.value) {
    fetchTask()
  }
})

onUnmounted(() => {
  stopPolling()
})

/** 获取任务详情 */
async function fetchTask() {
  if (!taskId.value) return
  try {
    const res = await getTask(taskId.value)
    task.value = res.data
    error.value = false

    // 决定是否继续轮询
    if (isTerminal.value) {
      stopPolling()
    } else {
      startPolling()
    }
  } catch (e) {
    error.value = true
    stopPolling()
  } finally {
    loading.value = false
  }
}

// ==================== 轮询 ====================

/** 开始轮询 */
function startPolling() {
  stopPolling() // 先清除已有定时器
  pollTimer = setInterval(() => {
    fetchTask()
  }, 3000)
}

/** 停止轮询 */
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ==================== 工具方法 ====================

/** 状态标签颜色 */
function statusTagType(status) {
  const map = {
    PENDING: 'warning',
    RUNNING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

/** 状态标签文字 */
function statusLabel(status) {
  const map = {
    PENDING: '等待中',
    RUNNING: '运行中',
    SUCCESS: '已完成',
    FAILED: '失败'
  }
  return map[status] || status || '—'
}

/** 格式化时间 */
function formatTime(time) {
  if (!time) return '—'
  const d = new Date(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.layout-header { background: #304156; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; }
.header-left { display: flex; align-items: center; }
.header-left h2 { color: #fff; font-size: 18px; margin: 0; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-info { color: #bfcbd9; }
.layout-aside { background: #304156; }
.layout-main { background: #f0f2f5; padding: 24px; }
.page-header { margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; }
.page-header h3 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; }

.status-card { margin-bottom: 24px; max-width: 900px; }

.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

.progress-section { margin-top: 24px; }
.progress-label { margin-top: 8px; color: #909399; font-size: 13px; }

.text-success { color: #67C23A; font-weight: 600; }
.text-danger { color: #F56C6C; font-weight: 600; }
</style>
