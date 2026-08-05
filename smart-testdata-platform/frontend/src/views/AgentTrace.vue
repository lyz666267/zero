<template>
        <div class="page-header">
          <h3>Agent 执行轨迹</h3>
          <div class="header-actions">
            <el-button v-if="data?.taskId" type="primary" size="small" plain
              @click="$router.push(`/task-monitor?id=${data.taskId}`)">
              <el-icon><Monitor /></el-icon>
              查看任务详情
            </el-button>
            <el-button text @click="$router.push('/testdata/task')">
              <el-icon><ArrowLeft /></el-icon>
              返回创建任务
            </el-button>
          </div>
        </div>

        <!-- 搜索区 -->
        <el-card class="search-card">
          <el-form :inline="true">
            <el-form-item label="任务 ID">
              <el-input v-model="taskIdInput" placeholder="请输入任务 ID" clearable
                @keyup.enter="handleQuery" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery" :loading="loading">
                <el-icon><Search /></el-icon>
                查询
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 空状态 -->
        <el-card v-if="queried && !data" class="empty-card">
          <el-result icon="warning" title="未找到执行记录" sub-title="该任务暂无 Agent 执行日志，请确认任务 ID 是否正确">
            <template #extra>
              <el-button type="primary" @click="$router.push('/testdata/task')">创建新任务</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 时间线展示 -->
        <el-card v-if="data && data.steps && data.steps.length > 0" class="timeline-card">
          <template #header>
            <div class="card-header">
              <span>任务 #{{ data.taskId }} — 执行过程</span>
              <el-tag type="primary">{{ data.steps.length }} 步</el-tag>
            </div>
          </template>

          <div class="timeline-container">
            <!-- 步骤列表 -->
            <div v-for="(step, index) in data.steps" :key="step.stepNumber" class="timeline-step">
              <!-- 时间线节点 -->
              <div class="timeline-node-area">
                <div class="timeline-dot" :class="statusDotClass(step.status)">
                  <el-icon v-if="step.status === 'SUCCESS'" :size="14"><Check /></el-icon>
                  <el-icon v-else-if="step.status === 'FAILED'" :size="14"><Close /></el-icon>
                  <el-icon v-else-if="step.status === 'SKIPPED'" :size="14"><Remove /></el-icon>
                  <el-icon v-else :size="14"><MoreFilled /></el-icon>
                </div>
                <div v-if="index < data.steps.length - 1" class="timeline-line" :class="statusLineClass(step.status)"></div>
              </div>

              <!-- 步骤内容 -->
              <div class="timeline-content">
                <div class="step-header">
                  <span class="step-number">Step {{ step.stepNumber }}</span>
                  <span class="step-action">{{ step.action }}</span>
                  <el-tag :type="stepTagType(step.status)" size="small" effect="plain">
                    {{ statusLabel(step.status) }}
                  </el-tag>
                </div>

                <div class="step-meta">
                  <span v-if="step.toolName" class="meta-item">
                    <el-icon :size="12"><Tools /></el-icon>
                    工具：{{ step.toolName }}
                  </span>
                  <span v-if="step.stepType" class="meta-item">
                    <el-icon :size="12"><Memo /></el-icon>
                    类型：{{ step.stepType }}
                  </span>
                  <span v-if="step.executionTime > 0" class="meta-item">
                    <el-icon :size="12"><Timer /></el-icon>
                    耗时：{{ formatDuration(step.executionTime) }}
                  </span>
                  <span v-else-if="step.status === 'SKIPPED'" class="meta-item skipped">
                    <el-icon :size="12"><Remove /></el-icon>
                    已跳过
                  </span>
                </div>

                <!-- 输入/输出数据（可折叠） -->
                <el-collapse v-if="step.inputData || step.outputData" class="step-detail">
                  <el-collapse-item v-if="step.inputData" title="输入数据">
                    <pre class="json-preview">{{ formatJson(step.inputData) }}</pre>
                  </el-collapse-item>
                  <el-collapse-item v-if="step.outputData" title="输出数据">
                    <pre class="json-preview">{{ formatJson(step.outputData) }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 步骤为空 -->
        <el-card v-if="data && data.steps && data.steps.length === 0" class="empty-card">
          <el-result icon="info" title="暂无执行步骤" sub-title="该任务尚未记录任何 Agent 执行步骤" />
        </el-card>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgentLogs } from '@/api/agent'
import {
  DataAnalysis, FolderOpened, Coin, MagicStick, List, Monitor, Connection,
  Search, ArrowLeft, Document, Check, Close, Remove, MoreFilled, Tools, Memo, Timer, View, Lock
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const taskIdInput = ref('')
const data = ref(null)
const loading = ref(false)
const queried = ref(false)

// ==================== 查询 ====================

/** 从 query 参数初始化 */
onMounted(() => {
  const id = route.query.taskId
  if (id) {
    taskIdInput.value = String(id)
    fetchLogs(Number(id))
  }
})

/** 监听 query 变化 */
watch(() => route.query.taskId, (newId) => {
  if (newId) {
    taskIdInput.value = String(newId)
    fetchLogs(Number(newId))
  }
})

function handleQuery() {
  const id = Number(taskIdInput.value.trim())
  if (Number.isNaN(id) || id <= 0) {
    return
  }
  // 同步 URL
  router.replace({ query: { taskId: id } })
  fetchLogs(id)
}

/** 获取 Agent 执行日志 */
async function fetchLogs(taskId) {
  loading.value = true
  queried.value = true
  try {
    const res = await getAgentLogs(taskId)
    if (res.code === 200) {
      data.value = res.data
    } else {
      data.value = null
    }
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}

// ==================== 工具方法 ====================

/** 状态节点样式 */
function statusDotClass(status) {
  const map = {
    SUCCESS: 'dot-success',
    FAILED: 'dot-failed',
    SKIPPED: 'dot-skipped',
    RUNNING: 'dot-running'
  }
  return map[status] || 'dot-default'
}

/** 连接线样式 */
function statusLineClass(status) {
  const map = {
    SUCCESS: 'line-success',
    FAILED: 'line-failed',
  }
  return map[status] || 'line-default'
}

/** 状态标签颜色 */
function stepTagType(status) {
  const map = {
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info',
    RUNNING: 'warning'
  }
  return map[status] || 'info'
}

/** 状态标签中文 */
function statusLabel(status) {
  const map = {
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED: '已跳过',
    RUNNING: '执行中'
  }
  return map[status] || status
}

/** 格式化耗时 */
function formatDuration(ms) {
  if (ms == null || ms === 0) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} 秒`
  return `${Math.floor(ms / 60000)} 分 ${Math.round((ms % 60000) / 1000)} 秒`
}

/** 格式化 JSON（预处理，可能已经是字符串） */
function formatJson(raw) {
  if (!raw) return ''
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(raw)
  }
}

</script>

<style scoped>
.page-header { margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; }
.page-header h3 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; }

/* 搜索区 */
.search-card { margin-bottom: 24px; max-width: 900px; }

/* 空状态 */
.empty-card { max-width: 900px; }

/* 时间线卡片 */
.timeline-card { max-width: 900px; }
.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }

/* 时间线容器 */
.timeline-container { padding: 8px 0; }

/* 时间线步骤 */
.timeline-step { display: flex; gap: 16px; }

/* 时间线节点区域 */
.timeline-node-area {
  display: flex; flex-direction: column; align-items: center;
  width: 40px; flex-shrink: 0;
}

.timeline-dot {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; color: #fff;
}
.dot-success { background: #67C23A; }
.dot-failed { background: #F56C6C; }
.dot-skipped { background: #C0C4CC; }
.dot-running { background: #409EFF; }
.dot-default { background: #909399; }

.timeline-line {
  width: 2px; flex: 1; min-height: 40px; margin: 4px 0;
}
.line-success { background: #67C23A; }
.line-failed { background: #F56C6C; }
.line-default { background: #DCDFE6; }

/* 时间线内容 */
.timeline-content {
  flex: 1; padding-bottom: 24px; min-width: 0;
}

.step-header {
  display: flex; align-items: center; gap: 10px; margin-bottom: 6px;
}
.step-number {
  font-weight: 700; color: #409EFF; font-size: 13px;
  background: #ECF5FF; padding: 2px 8px; border-radius: 4px;
}
.step-action {
  font-size: 15px; font-weight: 600; color: #303133;
}

.step-meta {
  display: flex; flex-wrap: wrap; gap: 16px; margin-top: 6px;
}
.meta-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; color: #909399;
}
.meta-item.skipped { color: #C0C4CC; }

/* 折叠详情 */
.step-detail { margin-top: 12px; }
.json-preview {
  background: #f5f7fa; border: 1px solid #e4e7ed;
  border-radius: 6px; padding: 12px; margin: 0;
  font-size: 12px; line-height: 1.6; color: #303133;
  white-space: pre-wrap; word-break: break-all;
  max-height: 240px; overflow-y: auto;
}
</style>
