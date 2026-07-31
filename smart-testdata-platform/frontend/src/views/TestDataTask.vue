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
          <el-menu-item index="/task-monitor">
            <el-icon><Monitor /></el-icon>
            <span>任务监控</span>
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
          <h3>创建测试数据生成任务</h3>
        </div>

        <el-card class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">1</span> 配置任务参数
            </div>
          </template>

          <el-form :model="form" label-width="100px" style="max-width: 700px">
            <!-- 项目选择 -->
            <el-form-item label="所属项目">
              <el-select v-model="selectedProjectId" placeholder="请选择项目" style="width: 300px" @change="fetchDatasources">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>

            <!-- 数据源选择 -->
            <el-form-item label="数据源">
              <el-select v-model="form.datasourceId" placeholder="请先选择项目" style="width: 300px"
                :disabled="!selectedProjectId">
                <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.dbName})`" :value="ds.id" />
              </el-select>
            </el-form-item>

            <!-- 需求描述 -->
            <el-form-item label="需求描述" prop="requirement">
              <el-input v-model="form.requirement" type="textarea" :rows="4"
                placeholder="例如：生成用户订单测试数据500条，保持外键关系，手机号脱敏" />
            </el-form-item>

            <!-- 生成数量 -->
            <el-form-item label="生成数量" prop="totalCount">
              <el-input-number v-model="form.totalCount" :min="1" :max="100000" :step="100"
                placeholder="请输入生成数量" style="width: 300px" />
            </el-form-item>

            <!-- 提交按钮 -->
            <el-form-item>
              <el-button type="primary" size="large" :loading="submitting" :disabled="!canSubmit"
                @click="handleSubmit">
                <el-icon><Upload /></el-icon>
                创建任务
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 创建成功提示 -->
        <el-card v-if="createdTask" class="result-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">✓</span> 任务创建成功
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务 ID">{{ createdTask.id }}</el-descriptions-item>
            <el-descriptions-item label="任务名称">{{ createdTask.taskName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(createdTask.status)">{{ createdTask.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="目标数量">{{ createdTask.totalCount }} 条</el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 16px">
            <el-button type="primary" @click="goToMonitor">
              <el-icon><Monitor /></el-icon>
              查看任务监控
            </el-button>
            <el-button @click="resetForm">创建新任务</el-button>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getProjectList } from '@/api/project'
import { getDatasourceList } from '@/api/datasource'
import { createTask } from '@/api/task'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/testdata/task')

// 项目选择
const projects = ref([])
const selectedProjectId = ref(null)

// 数据源选择
const datasources = ref([])

// 表单
const form = reactive({
  datasourceId: null,
  requirement: '',
  totalCount: 100
})

// 提交状态
const submitting = ref(false)
const createdTask = ref(null)

// 能否提交
const canSubmit = computed(() => {
  return form.datasourceId && form.requirement.trim() && form.totalCount > 0
})

onMounted(async () => {
  try {
    const res = await getProjectList(1, 100)
    projects.value = res.data.records
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      fetchDatasources()
    }
  } catch (e) { /* handled by interceptor */ }
})

/** 加载数据源列表 */
async function fetchDatasources() {
  if (!selectedProjectId.value) return
  try {
    const res = await getDatasourceList(selectedProjectId.value)
    datasources.value = res.data || []
    form.datasourceId = null
  } catch (e) { /* handled by interceptor */ }
}

/** 提交任务 */
async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  createdTask.value = null
  try {
    const res = await createTask({
      taskName: form.requirement.trim(),
      datasourceId: form.datasourceId,
      totalCount: form.totalCount
    })
    createdTask.value = res.data
    ElMessage.success(`任务创建成功，任务 ID: ${res.data.id}`)
  } catch (e) {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

/** 跳转任务监控页 */
function goToMonitor() {
  if (createdTask.value) {
    router.push(`/task-monitor?id=${createdTask.value.id}`)
  }
}

/** 重置表单，创建新任务 */
function resetForm() {
  createdTask.value = null
  form.requirement = ''
  form.totalCount = 100
}

/** 状态标签颜色 */
function statusTagType(status) {
  const map = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'info'
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
.page-header { margin-bottom: 20px; }
.page-header h3 { margin: 0; font-size: 20px; }

.config-card { margin-bottom: 24px; }
.result-card { margin-bottom: 24px; }

.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }

.step-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: #409EFF; color: #fff; font-size: 14px; font-weight: bold;
}
</style>
