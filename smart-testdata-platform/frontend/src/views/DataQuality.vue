<template>
        <div class="page-header">
          <h3>数据质量评分</h3>
        </div>

        <!-- 任务选择 -->
        <el-card class="config-card">
          <el-form :inline="true">
            <el-form-item label="任务 ID">
              <el-input-number v-model="queryTaskId" :min="1" placeholder="输入任务 ID" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="evaluating" @click="handleEvaluate">
                <el-icon><DataAnalysis /></el-icon>
                执行质量评估
              </el-button>
              <el-button :loading="loading" @click="handleQuery">
                <el-icon><Search /></el-icon>
                查询报告
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 加载中 -->
        <el-card v-if="loading || evaluating" class="result-card">
          <div class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>{{ evaluating ? '正在执行质量评估...' : '正在加载质量报告...' }}</p>
          </div>
        </el-card>

        <!-- 错误提示 -->
        <el-card v-else-if="errorMsg" class="result-card">
          <el-result icon="error" title="加载失败" :sub-title="errorMsg">
            <template #extra>
              <el-button @click="handleEvaluate">执行质量评估</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 质量报告 -->
        <template v-else-if="report">
          <!-- 综合评分总览 -->
          <el-card class="overview-card">
            <div class="overview-wrapper">
              <div class="score-circle" :style="{ borderColor: gradeColor }">
                <div class="score-value" :style="{ color: gradeColor }">{{ report.totalScore }}</div>
                <div class="score-label">综合评分</div>
              </div>
              <div class="grade-badge" :style="{ background: gradeColor }">
                {{ report.grade }}
              </div>
              <div class="overview-meta">
                <span>任务 #{{ report.taskId }}</span>
              </div>
            </div>
          </el-card>

          <!-- 五项指标 ECharts 雷达图 -->
          <el-card class="chart-card">
            <template #header>
              <div class="card-header">
                <span>五维指标评分</span>
              </div>
            </template>
            <div ref="chartRef" class="chart-box"></div>
          </el-card>

          <!-- 指标明细卡片 -->
          <el-card class="metrics-card">
            <template #header>
              <div class="card-header">
                <span>指标明细</span>
              </div>
            </template>
            <el-row :gutter="16">
              <el-col :span="12" v-for="item in metricItems" :key="item.key" style="margin-bottom: 12px">
                <div class="metric-item">
                  <div class="metric-header">
                    <span class="metric-name">{{ item.label }}</span>
                    <span class="metric-weight">{{ item.weight }}</span>
                  </div>
                  <el-progress
                    :percentage="item.value"
                    :color="item.color"
                    :stroke-width="18"
                    :text-inside="true"
                  />
                </div>
              </el-col>
            </el-row>
          </el-card>

          <!-- 问题列表 -->
          <el-card v-if="report.details && report.details.length > 0" class="issues-card">
            <template #header>
              <div class="card-header">
                <span>发现问题</span>
                <el-tag type="danger" size="small">{{ report.details.length }} 个</el-tag>
              </div>
            </template>
            <el-table :data="report.details" border stripe style="width: 100%" empty-text="暂无问题">
              <el-table-column prop="category" label="指标类别" width="120">
                <template #default="{ row }">
                  <el-tag :type="categoryTagType(row.category)" size="small">{{ categoryLabel(row.category) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="level" label="级别" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.level === 'error' ? 'danger' : 'warning'" size="small">
                    {{ row.level === 'error' ? '错误' : '警告' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="tableName" label="表名" width="140" />
              <el-table-column prop="fieldName" label="字段" width="140" />
              <el-table-column prop="message" label="问题描述" min-width="220" show-overflow-tooltip />
              <el-table-column prop="suggestion" label="改进建议" min-width="200" show-overflow-tooltip />
            </el-table>
          </el-card>

          <!-- 改进建议汇总 -->
          <el-card v-if="suggestions.length > 0" class="suggestions-card">
            <template #header>
              <div class="card-header">
                <span>改进建议</span>
              </div>
            </template>
            <div v-for="(s, idx) in suggestions" :key="idx" class="suggestion-item">
              <el-icon :size="18" color="#409EFF"><CircleCheck /></el-icon>
              <span>{{ s }}</span>
            </div>
          </el-card>
        </template>

        <!-- 空状态 -->
        <el-card v-else class="result-card">
          <el-empty description="请输入任务 ID 并执行评估或查询报告" />
        </el-card>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { evaluateQuality, getQualityReport } from '@/api/quality'
import * as echarts from 'echarts'

const route = useRoute()

// ==================== 查询参数 ====================

const queryTaskId = ref(null)
const loading = ref(false)
const evaluating = ref(false)
const errorMsg = ref('')
const report = ref(null)

// ==================== ECharts ====================

const chartRef = ref(null)
let chartInstance = null

/** 初始化雷达图 */
function initChart() {
  if (!chartRef.value) return

  // 销毁旧实例
  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

/** 更新雷达图数据 */
function updateChart() {
  if (!chartInstance || !report.value) return

  const metrics = report.value.metrics
  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      data: ['当前评分'],
      bottom: 0
    },
    radar: {
      center: ['50%', '55%'],
      radius: '70%',
      indicator: [
        { name: '完整性', max: 100 },
        { name: '唯一性', max: 100 },
        { name: '一致性', max: 100 },
        { name: '合法性', max: 100 },
        { name: '隐私安全', max: 100 }
      ],
      axisName: {
        color: '#606266',
        fontSize: 13
      }
    },
    series: [
      {
        name: '质量指标',
        type: 'radar',
        data: [
          {
            value: [
              metrics.completeness || 0,
              metrics.uniqueness || 0,
              metrics.consistency || 0,
              metrics.validity || 0,
              metrics.privacy || 0
            ],
            name: '当前评分',
            areaStyle: {
              color: 'rgba(64, 158, 255, 0.3)'
            },
            lineStyle: {
              color: '#409EFF',
              width: 2
            },
            itemStyle: {
              color: '#409EFF'
            }
          }
        ]
      }
    ]
  }

  chartInstance.setOption(option)
}

// 窗口大小变化时重绘
function handleResize() {
  if (chartInstance) chartInstance.resize()
}

// 监听 report 变化，更新图表
watch(report, (val) => {
  if (val) {
    nextTick(() => {
      initChart()
      window.addEventListener('resize', handleResize)
    })
  }
})

onBeforeUnmount(() => {
  if (chartInstance) chartInstance.dispose()
  window.removeEventListener('resize', handleResize)
})

// ==================== 计算属性 ====================

/** 等级对应颜色 */
const gradeColor = computed(() => {
  const map = {
    '优秀': '#67C23A',
    '良好': '#409EFF',
    '合格': '#E6A23C',
    '不合格': '#F56C6C'
  }
  return map[report.value?.grade] || '#909399'
})

/** 指标列表（用于进度条展示） */
const metricItems = computed(() => {
  if (!report.value?.metrics) return []
  return [
    { key: 'completeness', label: '数据完整性', weight: '25%', value: report.value.metrics.completeness || 0, color: '#67C23A' },
    { key: 'uniqueness', label: '数据唯一性', weight: '20%', value: report.value.metrics.uniqueness || 0, color: '#409EFF' },
    { key: 'consistency', label: '关联一致性', weight: '25%', value: report.value.metrics.consistency || 0, color: '#E6A23C' },
    { key: 'validity', label: '格式合法性', weight: '15%', value: report.value.metrics.validity || 0, color: '#909399' },
    { key: 'privacy', label: '隐私安全', weight: '15%', value: report.value.metrics.privacy || 0, color: '#F56C6C' }
  ]
})

/** 提取唯一的改进建议 */
const suggestions = computed(() => {
  if (!report.value?.details) return []
  const seen = new Set()
  return report.value.details
    .filter(d => d.suggestion && !seen.has(d.suggestion) && seen.add(d.suggestion))
    .map(d => d.suggestion)
})

// ==================== 操作方法 ====================

/** 执行质量评估 */
async function handleEvaluate() {
  if (!queryTaskId.value) {
    ElMessage.warning('请输入任务 ID')
    return
  }

  evaluating.value = true
  errorMsg.value = ''
  report.value = null

  try {
    // 评估需要 datasourceId，先通过任务 ID 获取
    const res = await evaluateQuality(queryTaskId.value, queryTaskId.value)
    report.value = res.data
    ElMessage.success(`质量评估完成 — ${report.value.totalScore} 分 · ${report.value.grade}`)
  } catch (e) {
    const msg = e.response?.data?.message || e.message || '评估失败'
    errorMsg.value = msg
    ElMessage.error(msg)
  } finally {
    evaluating.value = false
  }
}

/** 查询已有质量报告 */
async function handleQuery() {
  if (!queryTaskId.value) {
    ElMessage.warning('请输入任务 ID')
    return
  }

  loading.value = true
  errorMsg.value = ''
  report.value = null

  try {
    const res = await getQualityReport(queryTaskId.value)
    report.value = res.data
    ElMessage.success('质量报告加载完成')
  } catch (e) {
    const msg = e.response?.data?.message || e.message || '加载失败'
    errorMsg.value = msg
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

// ==================== 工具方法 ====================

function categoryTagType(cat) {
  const map = {
    completeness: 'success',
    uniqueness: '',
    consistency: 'warning',
    validity: 'info',
    privacy: 'danger'
  }
  return map[cat] || 'info'
}

function categoryLabel(cat) {
  const map = {
    completeness: '完整性',
    uniqueness: '唯一性',
    consistency: '一致性',
    validity: '合法性',
    privacy: '隐私安全'
  }
  return map[cat] || cat
}


// ==================== 从 URL 参数读取 taskId ====================

const urlTaskId = computed(() => {
  const id = Number(route.query.taskId)
  return Number.isNaN(id) ? null : id
})

// 初次加载时，如果 URL 携带 taskId，自动查询
if (urlTaskId.value) {
  queryTaskId.value = urlTaskId.value
  handleQuery()
}
</script>

<style scoped>
.page-header { margin-bottom: 20px; }
.page-header h3 { margin: 0; font-size: 20px; }

.config-card { margin-bottom: 24px; }
.result-card { margin-bottom: 24px; }

/* 加载状态 */
.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

/* 综合评分总览 */
.overview-card { margin-bottom: 24px; text-align: center; }
.overview-wrapper {
  display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 20px 0;
}
.score-circle {
  width: 160px; height: 160px; border-radius: 50%;
  border: 6px solid #409EFF;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: #f0f9ff;
}
.score-value { font-size: 42px; font-weight: 700; color: #409EFF; line-height: 1.2; }
.score-label { font-size: 14px; color: #909399; margin-top: 4px; }
.grade-badge {
  padding: 8px 28px; border-radius: 20px; color: #fff;
  font-size: 22px; font-weight: 700; letter-spacing: 4px;
}
.overview-meta { color: #909399; font-size: 14px; }

/* 雷达图 */
.chart-card { margin-bottom: 24px; }
.chart-box { width: 100%; height: 400px; }

/* 指标明细卡片 */
.metrics-card { margin-bottom: 24px; }
.metric-item { padding: 8px 0; }
.metric-header { display: flex; justify-content: space-between; margin-bottom: 6px; }
.metric-name { font-size: 14px; color: #303133; font-weight: 500; }
.metric-weight { font-size: 12px; color: #909399; }

/* 问题列表 */
.issues-card { margin-bottom: 24px; }
.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }

/* 改进建议 */
.suggestions-card { margin-bottom: 24px; }
.suggestion-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 0; border-bottom: 1px solid #ebeef5;
  font-size: 14px; color: #606266;
}
.suggestion-item:last-child { border-bottom: none; }
</style>
