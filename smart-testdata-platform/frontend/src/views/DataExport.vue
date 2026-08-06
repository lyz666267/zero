<template>
        <div class="page-header">
          <h3>数据导出</h3>
        </div>

        <!-- 步骤 1: 选择任务 -->
        <el-card class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">1</span> 选择任务
            </div>
          </template>

          <el-form label-width="80px" style="max-width: 600px">
            <el-form-item label="已完成任务">
              <el-select v-model="selectedTaskId" placeholder="请选择任务" style="width: 400px"
                :loading="taskLoading" @change="onTaskChange">
                <el-option v-for="t in taskList" :key="t.id"
                  :label="`#${t.id} — ${t.taskName} (${t.successCount || 0} 条)`"
                  :value="t.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 步骤 2: 数据预览 -->
        <el-card v-if="selectedTaskId" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">2</span> 数据预览
              <el-tag v-if="previewTables.length > 0" type="info" size="small" style="margin-left: 8px">
                {{ previewTables.length }} 张表 · {{ totalPreviewRows }} 条
              </el-tag>
            </div>
          </template>

          <div v-if="previewLoading" class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>加载预览数据...</p>
          </div>

          <el-empty v-else-if="previewTables.length === 0" description="该任务暂无数据" />

          <el-tabs v-else v-model="activePreviewTable" type="border-card">
            <el-tab-pane v-for="(table, idx) in previewTables" :key="idx"
              :label="table.tableName" :name="String(idx)">
              <el-table :data="table.rows.slice(0, 20)" border stripe
                max-height="400" style="width: 100%" empty-text="暂无数据">
                <el-table-column v-for="col in getColumns(table)" :key="col"
                  :prop="col" :label="col" min-width="120" show-overflow-tooltip />
              </el-table>
              <div v-if="table.rows.length > 20" class="more-hint">
                仅展示前 20 条，共 {{ table.rows.length }} 条
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 步骤 3: 选择格式 -->
        <el-card v-if="selectedTaskId" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">3</span> 选择导出格式
            </div>
          </template>

          <el-radio-group v-model="exportFormat" @change="onFormatChange" size="large">
            <el-radio-button value="JSON">
              <el-icon><Document /></el-icon> JSON
            </el-radio-button>
            <el-radio-button value="CSV">
              <el-icon><Grid /></el-icon> CSV
            </el-radio-button>
            <el-radio-button value="SQL">
              <el-icon><Coin /></el-icon> SQL INSERT
            </el-radio-button>
          </el-radio-group>
        </el-card>

        <!-- 步骤 4: 导出预览 + 下载 -->
        <el-card v-if="selectedTaskId" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">4</span> 导出内容预览
            </div>
          </template>

          <div v-if="exportPreviewLoading" class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>加载导出预览...</p>
          </div>

          <div v-else>
            <!-- 导出内容预览代码块 -->
            <div class="code-block">
              <pre><code>{{ exportPreviewContent }}</code></pre>
            </div>

            <!-- 操作按钮 -->
            <div style="margin-top: 16px; display: flex; gap: 12px">
              <el-button type="primary" size="large" :loading="exportLoading"
                @click="handleExport">
                <el-icon><Download /></el-icon>
                下载导出文件
              </el-button>
              <el-button size="large" @click="handleCopy" :disabled="!exportPreviewContent">
                <el-icon><CopyDocument /></el-icon>
                复制到剪贴板
              </el-button>
            </div>

            <!-- 导出成功提示 -->
            <el-alert v-if="exportSuccess" type="success" title="导出成功"
              :description="`文件已开始下载: ${exportFileName}`"
              style="margin-top: 12px" show-icon closable @close="exportSuccess = false" />
          </div>
        </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listExportableTasks, exportTaskData, previewExportData } from '@/api/export'
import { getTaskResult } from '@/api/task'


// ==================== 任务选择 ====================
const taskList = ref([])
const taskLoading = ref(false)
const selectedTaskId = ref(null)

// ==================== 数据预览 ====================
const previewTables = ref([])
const previewLoading = ref(false)
const activePreviewTable = ref('0')

const totalPreviewRows = computed(() => {
  return previewTables.value.reduce((sum, t) => sum + (t.rows?.length || 0), 0)
})

// ==================== 格式选择 ====================
const exportFormat = ref('JSON')

// ==================== 导出预览 ====================
const exportPreviewContent = ref('')
const exportPreviewLoading = ref(false)
const exportLoading = ref(false)
const exportSuccess = ref(false)
const exportFileName = ref('')

// ==================== 初始化 ====================
onMounted(async () => {
  taskLoading.value = true
  try {
    const res = await listExportableTasks()
    taskList.value = res.data || []
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    taskLoading.value = false
  }
})

// ==================== 任务选择回调 ====================
async function onTaskChange(taskId) {
  previewTables.value = []
  exportPreviewContent.value = ''
  exportSuccess.value = false

  if (!taskId) return

  // 加载表数据预览
  previewLoading.value = true
  try {
    const res = await getTaskResult(taskId)
    const data = res.data
    if (data && data.tables) {
      previewTables.value = data.tables
      activePreviewTable.value = '0'
    } else {
      previewTables.value = []
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败，请稍后重试')
    previewTables.value = []
  } finally {
    previewLoading.value = false
  }

  // 加载导出内容预览
  await onFormatChange()
}

// ==================== 格式切换回调 ====================
async function onFormatChange() {
  if (!selectedTaskId.value) return
  exportSuccess.value = false
  exportPreviewLoading.value = true
  try {
    const content = await previewExportData(selectedTaskId.value, exportFormat.value)
    exportPreviewContent.value = content || ''
  } catch (e) {
    exportPreviewContent.value = '预览加载失败: ' + (e.response?.data?.message || e.message || '未知错误')
    ElMessage.error('预览加载失败，请稍后重试')
  } finally {
    exportPreviewLoading.value = false
  }
}

// ==================== 导出下载 ====================
async function handleExport() {
  if (!selectedTaskId.value) return
  exportLoading.value = true
  exportSuccess.value = false
  try {
    const blob = await exportTaskData(selectedTaskId.value, exportFormat.value)
    // 构建文件名
    const ext = exportFormat.value === 'SQL' ? '.sql' : exportFormat.value === 'CSV' ? '.csv' : '.json'
    const fileName = `task_${selectedTaskId.value}_${timestamp()}${ext}`
    exportFileName.value = fileName

    // 触发浏览器下载
    downloadBlob(blob, fileName)
    exportSuccess.value = true
    ElMessage.success(`导出成功: ${fileName}`)
  } catch (e) {
    ElMessage.error('导出失败: ' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    exportLoading.value = false
  }
}

// ==================== 复制到剪贴板 ====================
async function handleCopy() {
  if (!exportPreviewContent.value) return
  try {
    await navigator.clipboard.writeText(exportPreviewContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = exportPreviewContent.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch (err) {
      ElMessage.error('复制失败')
    }
    document.body.removeChild(textarea)
  }
}

// ==================== 工具方法 ====================

/** 从 rows 中提取所有列名（去重并保持首次出现顺序） */
function getColumns(table) {
  const keys = new Set()
  const cols = []
  for (const row of (table.rows || [])) {
    for (const key of Object.keys(row)) {
      if (!keys.has(key)) {
        keys.add(key)
        cols.push(key)
      }
    }
  }
  return cols
}

/** 生成时间戳字符串 */
function timestamp() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const h = String(now.getHours()).padStart(2, '0')
  const min = String(now.getMinutes()).padStart(2, '0')
  const s = String(now.getSeconds()).padStart(2, '0')
  return `${y}${m}${d}_${h}${min}${s}`
}

/** 触发浏览器下载 Blob */
function downloadBlob(blob, fileName) {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

</script>

<style scoped>
.page-header { margin-bottom: 20px; }
.page-header h3 { margin: 0; font-size: 20px; }

.config-card { margin-bottom: 24px; }

.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }

.step-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: #409EFF; color: #fff; font-size: 14px; font-weight: bold;
}

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

.code-block {
  background: #1e1e1e; color: #d4d4d4; border-radius: 6px;
  padding: 16px; max-height: 400px; overflow: auto;
}
.code-block pre { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.6; }
.code-block code { font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace; color: #d4d4d4; }

.more-hint {
  text-align: center; padding: 8px; color: #909399; font-size: 13px;
}
</style>
