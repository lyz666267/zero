<template>
  <div class="page-header">
          <h3>任务结果</h3>
          <div class="header-actions">
            <el-button text @click="$router.push(`/task-monitor?id=${taskId}`)">
              <el-icon><ArrowLeft /></el-icon>
              返回任务监控
            </el-button>
          </div>
        </div>

        <!-- 参数缺失 -->
        <el-card v-if="!taskId" class="result-card">
          <el-empty description="缺少任务 ID 参数">
            <template #extra>
              <el-button type="primary" @click="$router.push('/testdata/task')">创建新任务</el-button>
            </template>
          </el-empty>
        </el-card>

        <!-- 加载中 -->
        <el-card v-else-if="loading" class="result-card">
          <div class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在加载任务结果...</p>
          </div>
        </el-card>

        <!-- 加载失败 -->
        <el-card v-else-if="loadError" class="result-card">
          <el-result icon="error" title="加载失败" :sub-title="loadError">
            <template #extra>
              <el-button @click="fetchResult">重新加载</el-button>
              <el-button type="primary" @click="$router.push('/testdata/task')">创建新任务</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 空结果 -->
        <el-card v-else-if="tables.length === 0" class="result-card">
          <el-empty description="暂无生成结果">
            <template #extra>
              <el-button type="primary" @click="$router.push('/testdata/task')">创建新任务</el-button>
            </template>
          </el-empty>
        </el-card>

        <!-- 多表结果 -->
        <el-card v-else class="result-card">
          <template #header>
            <div class="card-header">
              <span>任务 #{{ taskId }} 生成结果</span>
              <el-tag type="success" effect="dark" size="small">{{ tables.length }} 张表</el-tag>
            </div>
          </template>

          <el-tabs v-model="activeTable" type="border-card">
            <el-tab-pane
              v-for="(table, index) in tables"
              :key="index"
              :label="table.tableName"
              :name="String(index)"
            >
              <div class="table-toolbar">
                <span class="table-info">
                  <el-icon><Grid /></el-icon>
                  {{ table.tableName }} · {{ table.rows.length }} 条数据
                </span>
              </div>

              <el-table
                :data="table.rows"
                border
                stripe
                max-height="500"
                style="width: 100%"
                empty-text="暂无数据"
              >
                <el-table-column
                  v-for="col in getColumns(table)"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="140"
                  show-overflow-tooltip
                />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getTaskResult } from '@/api/task'

const route = useRoute()


const tables = ref([])
const activeTable = ref('0')
const loading = ref(false)
const loadError = ref('')

/** 任务 ID（从 query 参数读取） */
const taskId = computed(() => {
  const id = Number(route.query.taskId)
  return Number.isNaN(id) ? null : id
})

// ==================== 数据获取 ====================

onMounted(() => {
  if (taskId.value) {
    fetchResult()
  }
})

/** 获取任务生成结果 */
async function fetchResult() {
  if (!taskId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const res = await getTaskResult(taskId.value)
    // 响应拦截器已解包: res = { code, message, data: { success, tables } }
    const data = res.data
    if (data && data.tables) {
      tables.value = data.tables
    } else {
      tables.value = []
    }
    if (data.tables && data.tables.length > 0) {
      activeTable.value = '0'
    }
  } catch (e) {
    loadError.value = e.response?.data?.message || e.message || '加载失败'
    tables.value = []
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
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


</script>

<style scoped>
.page-header { margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; }
.page-header h3 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; }

.result-card { max-width: 1100px; }

.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

.table-toolbar { margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; }
.table-info { color: #606266; font-size: 14px; display: flex; align-items: center; gap: 6px; }
</style>
