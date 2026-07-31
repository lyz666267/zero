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
          <h3>数据库脱敏执行</h3>
          <span class="page-desc">对已有数据库业务数据进行安全脱敏 — 预览 → 确认 → 执行</span>
        </div>

        <!-- Step 1: 选择数据源和表 -->
        <el-card class="step-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">1</span> 选择目标数据源和表
            </div>
          </template>

          <el-row :gutter="20" align="middle">
            <el-col :span="6">
              <el-select v-model="selectedProjectId" placeholder="请选择项目" style="width:100%"
                @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-col>
            <el-col :span="6">
              <el-select v-model="selectedDsId" placeholder="请选择数据源" style="width:100%"
                :disabled="!selectedProjectId" @change="onDatasourceChange">
                <el-option v-for="ds in datasources" :key="ds.id"
                  :label="`${ds.name} (${ds.dbName})`" :value="ds.id" />
              </el-select>
            </el-col>
            <el-col :span="6">
              <el-select v-model="selectedTable" placeholder="请选择表" style="width:100%"
                :disabled="!selectedDsId" @change="onTableChange">
                <el-option v-for="t in tables" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
              </el-select>
            </el-col>
            <el-col :span="6">
              <el-button type="primary" :loading="analyzing" :disabled="!selectedTable"
                @click="handleAnalyze">
                <el-icon><Search /></el-icon>
                分析敏感字段
              </el-button>
            </el-col>
          </el-row>
        </el-card>

        <!-- Step 2: 敏感字段分析结果 -->
        <el-card v-if="previewResult" class="step-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">2</span> 敏感字段分析结果
              <el-tag type="warning" size="small" style="margin-left:8px">
                {{ previewResult.sensitiveFields?.length || 0 }} 个敏感字段
              </el-tag>
            </div>
          </template>

          <el-table :data="previewResult.sensitiveFields" border stripe style="width:100%">
            <el-table-column prop="columnName" label="字段名" width="150" />
            <el-table-column prop="typeLabel" label="敏感类型" width="100">
              <template #default="{ row }">
                <el-tag :type="sensitiveTypeColor(row.sensitiveType)" size="small">
                  {{ row.typeLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="strategy" label="脱敏策略" width="140">
              <template #default="{ row }">
                <code>{{ row.strategy }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="strategyDescription" label="策略说明" min-width="200" />
            <el-table-column label="脱敏示例" width="280">
              <template #default="{ row }">
                <div class="example-row">
                  <span class="example-label">原始：</span>
                  <code class="example-val">{{ row.exampleValue || '—' }}</code>
                </div>
                <div class="example-row">
                  <span class="example-label">脱敏：</span>
                  <code class="example-val masked">{{ row.maskedExample || '—' }}</code>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- Step 3: SQL 预览 -->
        <el-card v-if="previewResult" class="step-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">3</span> UPDATE SQL 预览
              <el-tag type="info" size="small" style="margin-left:8px">
                任务 ID: {{ previewResult.taskId }}
              </el-tag>
            </div>
          </template>

          <div class="sql-preview-box">
            <div class="sql-header">
              <span>生成的脱敏 SQL 语句</span>
              <el-button size="small" text @click="copySql">
                <el-icon><DocumentCopy /></el-icon>
                复制
              </el-button>
            </div>
            <pre class="sql-content"><code>{{ previewResult.sqlPreview }}</code></pre>
          </div>

          <div class="action-bar">
            <el-button type="success" size="large" :loading="executing"
              @click="handleExecute">
              <el-icon><Check /></el-icon>
              确认并执行脱敏
            </el-button>
            <el-button size="large" @click="resetAll">
              <el-icon><RefreshLeft /></el-icon>
              重新分析
            </el-button>
          </div>
        </el-card>

        <!-- Step 4: 执行结果 -->
        <el-card v-if="executeResult" class="step-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">4</span> 执行结果
              <el-tag :type="statusTagType(executeResult.status)" size="small" style="margin-left:8px">
                {{ executeResult.status === 'SUCCESS' ? '执行成功' :
                   executeResult.status === 'FAILED' ? '执行失败' : executeResult.status }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务 ID">{{ executeResult.taskId }}</el-descriptions-item>
            <el-descriptions-item label="目标表">{{ executeResult.tableName }}</el-descriptions-item>
            <el-descriptions-item label="影响行数">
              <el-tag type="warning">{{ executeResult.affectedRows || 0 }} 行</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="执行状态">
              <el-tag :type="statusTagType(executeResult.status)">{{ executeResult.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="执行结果" :span="2">
              {{ executeResult.executeResult || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getProjectList } from '@/api/project'
import { getDatasourceList, getSchema } from '@/api/datasource'
import { previewMask, executeMask, getMaskTask } from '@/api/databaseMask'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/database-mask')

// 项目 & 数据源选择
const projects = ref([])
const selectedProjectId = ref(null)
const datasources = ref([])
const selectedDsId = ref(null)

// 表选择
const tables = ref([])
const selectedTable = ref(null)

// 分析 & 执行状态
const analyzing = ref(false)
const executing = ref(false)
const previewResult = ref(null)
const executeResult = ref(null)

onMounted(async () => {
  try {
    const res = await getProjectList(1, 100)
    projects.value = res.data.records
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      await onProjectChange()
    }
  } catch (e) { /* handled by interceptor */ }

  // 如果 URL 带了 taskId，自动加载已有任务结果
  const queryTaskId = router.currentRoute.value.query.taskId
  if (queryTaskId) {
    try {
      const res = await getMaskTask(Number(queryTaskId))
      if (res.data) {
        previewResult.value = res.data
        if (res.data.status !== 'PREVIEW') {
          executeResult.value = res.data
        }
      }
    } catch (e) { /* ignore */ }
  }
})

/** 项目切换 → 加载数据源列表 */
async function onProjectChange() {
  datasources.value = []
  selectedDsId.value = null
  tables.value = []
  selectedTable.value = null
  if (!selectedProjectId.value) return

  try {
    const res = await getDatasourceList(selectedProjectId.value)
    datasources.value = res.data || []
  } catch (e) { /* handled by interceptor */ }
}

/** 数据源切换 → 加载表列表 */
async function onDatasourceChange() {
  tables.value = []
  selectedTable.value = null
  if (!selectedDsId.value) return

  try {
    const res = await getSchema(selectedDsId.value)
    tables.value = res.data?.tables || []
  } catch (e) {
    ElMessage.error('加载表列表失败')
  }
}

/** 表切换（清除上次结果） */
function onTableChange() {
  previewResult.value = null
  executeResult.value = null
}

/** 分析敏感字段 → 生成预览 SQL */
async function handleAnalyze() {
  if (!selectedDsId.value || !selectedTable.value) return
  analyzing.value = true
  previewResult.value = null
  executeResult.value = null

  try {
    const res = await previewMask({
      datasourceId: selectedDsId.value,
      tableName: selectedTable.value
    })
    previewResult.value = res.data
    ElMessage.success(`分析完成：检测到 ${res.data.sensitiveFields?.length || 0} 个敏感字段`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '敏感字段分析失败')
  } finally {
    analyzing.value = false
  }
}

/** 执行脱敏 */
async function handleExecute() {
  if (!previewResult.value?.taskId) return
  executing.value = true
  executeResult.value = null

  try {
    const res = await executeMask({ taskId: previewResult.value.taskId })
    executeResult.value = res.data

    if (res.data.status === 'SUCCESS') {
      ElMessage.success(`脱敏执行成功！影响 ${res.data.affectedRows || 0} 行数据`)
    } else {
      ElMessage.error(`脱敏执行失败：${res.data.executeResult || '未知错误'}`)
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '脱敏执行失败')
  } finally {
    executing.value = false
  }
}

/** 复制 SQL */
function copySql() {
  if (!previewResult.value?.sqlPreview) return
  navigator.clipboard.writeText(previewResult.value.sqlPreview).then(() => {
    ElMessage.success('SQL 已复制到剪贴板')
  }).catch(() => {
    ElMessage.warning('复制失败，请手动复制')
  })
}

/** 重置 */
function resetAll() {
  previewResult.value = null
  executeResult.value = null
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

/** 敏感类型标签颜色 */
function sensitiveTypeColor(type) {
  const map = { PHONE: 'danger', EMAIL: 'warning', ID_CARD: 'danger', NAME: 'info', ADDRESS: '', BANK_CARD: 'warning' }
  return map[type] || ''
}

/** 状态标签颜色 */
function statusTagType(status) {
  const map = { PREVIEW: 'info', EXECUTING: 'warning', SUCCESS: 'success', FAILED: 'danger' }
  return map[status] || 'info'
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
.page-desc { color: #909399; font-size: 13px; }

.step-card { margin-bottom: 20px; }

.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }

.step-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: #409EFF; color: #fff; font-size: 14px; font-weight: bold;
  flex-shrink: 0;
}

/* SQL 预览 */
.sql-preview-box {
  border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden;
}
.sql-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 16px; background: #f5f7fa; border-bottom: 1px solid #dcdfe6;
  font-size: 13px; color: #606266;
}
.sql-content {
  margin: 0; padding: 16px; background: #1e1e1e; color: #d4d4d4;
  font-family: 'Consolas', 'Courier New', monospace; font-size: 13px;
  line-height: 1.8; overflow-x: auto; max-height: 400px; white-space: pre-wrap;
}

/* 示例行 */
.example-row { display: flex; align-items: center; gap: 4px; }
.example-label { font-size: 12px; color: #909399; flex-shrink: 0; }
.example-val { font-size: 12px; }
.example-val.masked { color: #e6a23c; }

/* 操作栏 */
.action-bar { margin-top: 16px; display: flex; gap: 12px; }
</style>
