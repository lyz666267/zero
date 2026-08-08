<template>
  <div class="page-header">
          <h3>AI 生成计划</h3>
          <div class="header-actions">
            <el-button text @click="$router.back()">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
          </div>
        </div>

        <!-- 加载中 -->
        <el-card v-if="loading" class="plan-card">
          <div class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在加载生成计划...</p>
          </div>
        </el-card>

        <!-- 无 taskId -->
        <el-card v-else-if="!taskId" class="plan-card">
          <el-result icon="warning" title="缺少任务 ID">
            <template #sub-title>请从任务监控页面进入</template>
            <template #extra>
              <el-button type="primary" @click="$router.push('/task-monitor')">前往任务监控</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 任务不存在 -->
        <el-card v-else-if="taskNotFound" class="plan-card">
          <el-result icon="error" title="任务不存在">
            <template #sub-title>请检查任务 ID 是否正确</template>
            <template #extra>
              <el-button type="primary" @click="$router.push('/task-monitor')">前往任务监控</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 无计划 -->
        <el-card v-else-if="!planData" class="plan-card">
          <el-result icon="info" title="暂无生成计划">
            <template #sub-title>
              任务 #{{ taskId }} 暂未生成 AI 计划，或计划数据不可用
            </template>
          </el-result>
        </el-card>

        <!-- 计划内容 -->
        <template v-if="planData">
          <!-- 任务概览 -->
          <el-card class="plan-card">
            <template #header>
              <div class="card-header">
                <span>任务概览</span>
                <el-tag type="primary" effect="plain">任务 #{{ taskId }}</el-tag>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="任务名称" :span="2">
                <strong>{{ planData.plan?.taskName || '—' }}</strong>
              </el-descriptions-item>
              <el-descriptions-item label="计划表数">
                {{ planData.plan?.tables?.length ?? 0 }} 张表
              </el-descriptions-item>
              <el-descriptions-item label="Mock 模式">
                <el-tag :type="planData.mock ? 'warning' : 'success'" size="small">
                  {{ planData.mock ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 表级计划 -->
          <el-card v-if="planData.plan?.tables?.length > 0" class="plan-card">
            <template #header>
              <div class="card-header">
                <span>表级生成计划</span>
                <span class="card-subtitle">共 {{ planData.plan.tables.length }} 张表</span>
              </div>
            </template>

            <div class="table-plans">
              <el-card
                v-for="(tablePlan, idx) in planData.plan.tables"
                :key="idx"
                class="table-plan-card"
                shadow="hover"
              >
                <template #header>
                  <div class="table-plan-header">
                    <div class="table-plan-title">
                      <el-icon :size="18"><Grid /></el-icon>
                      <span class="table-name">{{ tablePlan.table }}</span>
                      <el-tag size="small" type="info">生成 {{ tablePlan.count ?? 0 }} 行</el-tag>
                    </div>
                    <el-tag size="small" effect="plain">
                      {{ tablePlan.fields?.length ?? 0 }} 个字段
                    </el-tag>
                  </div>
                </template>

                <!-- 字段策略表格 -->
                <el-table
                  v-if="tablePlan.fields?.length > 0"
                  :data="tablePlan.fields"
                  size="small"
                  border
                  stripe
                  style="width: 100%"
                >
                  <el-table-column prop="name" label="字段名" min-width="140">
                    <template #default="{ row }">
                      <div class="field-name-cell">
                        <span>{{ row.name }}</span>
                        <el-tag
                          v-if="row.foreignKey"
                          size="small"
                          type="warning"
                          effect="plain"
                          class="fk-tag"
                        >
                          FK → {{ row.foreignKey.table }}.{{ row.foreignKey.column }}
                        </el-tag>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="generator" label="生成器" min-width="150">
                    <template #default="{ row }">
                      <code class="generator-code">{{ row.generator || '—' }}</code>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型" width="100">
                    <template #default="{ row }">
                      {{ generatorType(row.generator) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="范围" width="130">
                    <template #default="{ row }">
                      {{ rangeText(row) }}
                    </template>
                  </el-table-column>
                </el-table>

                <el-empty v-else description="该表无字段计划" :image-size="60" />
              </el-card>
            </div>
          </el-card>

          <!-- 无表计划 -->
          <el-card v-else class="plan-card">
            <el-empty description="计划中无表信息" />
          </el-card>
        </template>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getTaskPlan } from '@/api/task'
import { listExportableTasks } from '@/api/export'

const route = useRoute()


const planData = ref(null)
const loading = ref(false)
const taskNotFound = ref(false)
const fallbackTaskId = ref(null)

/** 任务 ID（从 query 参数读取） */
const taskId = computed(() => {
  const id = Number(route.query.taskId)
  return Number.isNaN(id) ? fallbackTaskId.value : id
})

// ==================== 数据获取 ====================

onMounted(async () => {
  if (taskId.value) {
    fetchPlan()
    return
  }
  await loadLatestTask()
})

/** 没有任务 ID 时自动加载最近一次已完成任务的生成计划 */
async function loadLatestTask() {
  try {
    const res = await listExportableTasks()
    const tasks = res.data || []
    if (tasks.length > 0) {
      fallbackTaskId.value = tasks[0].id
      fetchPlan()
    } else {
      loading.value = false
    }
  } catch (e) {
    console.error(e)
    loading.value = false
  }
}

/** 获取生成计划 */
async function fetchPlan() {
  if (!taskId.value) return
  loading.value = true
  taskNotFound.value = false
  try {
    const res = await getTaskPlan(taskId.value)
    // res = { code, message, data: { success, data: {...plan...} } }
    const inner = res.data
    if (inner?.data) {
      planData.value = inner.data
    } else {
      planData.value = null
    }
  } catch (e) {
    // 404 → 任务不存在
    if (e.response?.status === 404 || e.response?.data?.code === 404) {
      taskNotFound.value = true
      ElMessage.error('任务不存在或已删除')
    } else {
      planData.value = null
      ElMessage.error('操作失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

// ==================== 工具方法 ====================

/** 根据生成器名称推导类型标签 */
function generatorType(gen) {
  if (!gen) return '—'
  if (gen.startsWith('faker.')) {
    const part = gen.substring(6)
    const map = { name: '姓名', email: '邮箱', phone: '电话', word: '文本' }
    return map[part] || part
  }
  const map = {
    'random.integer': '整数',
    'random.decimal': '小数',
    'random.boolean': '布尔',
    'enum.values': '枚举',
    'time.past_datetime': '日期时间',
    'uuid': 'UUID'
  }
  return map[gen] || gen
}

/** 格式化范围显示 */
function rangeText(field) {
  if (field?.range?.min != null && field?.range?.max != null) {
    return `${field.range.min}-${field.range.max}`
  }
  return '—'
}



</script>

<style scoped>
.page-header { margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; }
.page-header h3 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; }

.plan-card { margin-bottom: 24px; max-width: 1000px; }

.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
.card-subtitle { font-weight: 400; font-size: 13px; color: #909399; }

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

/* 表级计划列表 */
.table-plans { display: flex; flex-direction: column; gap: 16px; }

.table-plan-card {
  border: 1px solid #e4e7ed;
  transition: box-shadow 0.2s;
}

.table-plan-header { display: flex; align-items: center; justify-content: space-between; }
.table-plan-title { display: flex; align-items: center; gap: 8px; }
.table-name {
  font-weight: 600;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 15px;
  color: #303133;
}

/* 字段名列 */
.field-name-cell {
  display: flex; align-items: center; gap: 8px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}
.fk-tag { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; font-size: 11px; }

/* 生成器代码样式 */
.generator-code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #606266;
}
</style>
