<template>
  <div class="page-header">
          <h3>数据库关系图</h3>
        </div>

        <!-- 选择数据源 -->
        <el-card class="config-card">
          <el-form :inline="true">
            <el-form-item label="数据源">
              <el-select
                v-model="selectedDsId"
                placeholder="请选择数据源"
                style="width: 320px"
                @change="fetchRelation"
              >
                <el-option
                  v-for="ds in datasources"
                  :key="ds.id"
                  :label="`${ds.name} (${ds.dbName || ''})`"
                  :value="ds.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 加载中 -->
        <el-card v-if="loading" class="result-card">
          <div class="loading-box">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在加载关系分析数据...</p>
          </div>
        </el-card>

        <!-- 加载错误 -->
        <el-card v-else-if="loadError" class="result-card">
          <el-result icon="error" title="加载失败" :sub-title="loadError">
            <template #extra>
              <el-button @click="fetchRelation">重新加载</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 空状态 -->
        <el-card v-else-if="!selectedDsId" class="result-card">
          <el-empty description="请选择数据源查看数据库关系图" />
        </el-card>

        <!-- 关系图 + 生成顺序 -->
        <template v-if="relationData && !loading">
          <!-- ECharts 关系图 -->
          <el-card class="result-card">
            <template #header>
              <div class="card-header">
                <span>外键关系图</span>
                <el-tag v-if="relationData.graph?.nodes?.length" type="info" effect="dark" size="small">
                  {{ relationData.graph.nodes.length }} 张表
                </el-tag>
              </div>
            </template>

            <div v-if="!hasEdges" class="no-relation-box">
              <el-empty description="该数据源没有外键关系，所有表独立生成" :image-size="120" />
            </div>

            <div ref="chartRef" class="chart-box" v-show="hasEdges"></div>
          </el-card>

          <!-- 生成顺序 -->
          <el-card class="result-card">
            <template #header>
              <div class="card-header">
                <span>表生成顺序</span>
                <el-tag type="success" effect="dark" size="small">
                  拓扑排序
                </el-tag>
              </div>
            </template>

            <template v-if="relationData.generationOrder?.length">
              <div class="order-list">
                <div
                  v-for="(table, index) in relationData.generationOrder"
                  :key="table"
                  class="order-item"
                >
                  <el-tag class="order-index" type="primary" effect="dark" size="small">
                    {{ index + 1 }}
                  </el-tag>
                  <span class="order-table">{{ table }}</span>
                  <el-icon v-if="index < relationData.generationOrder.length - 1" class="order-arrow">
                    <ArrowDown />
                  </el-icon>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无表信息" :image-size="80" />
          </el-card>

          <!-- 外键关系明细 -->
          <el-card v-if="relationData.relations?.length" class="result-card">
            <template #header>
              <div class="card-header">
                <span>外键关系明细</span>
                <el-tag type="warning" effect="dark" size="small">
                  {{ relationData.relations.length }} 条
                </el-tag>
              </div>
            </template>

            <el-table
              :data="relationData.relations"
              border
              stripe
              style="width: 100%"
              empty-text="暂无外键关系"
            >
              <el-table-column label="源表 (FK)" min-width="200">
                <template #default="{ row }">
                  <span class="col-name">{{ row.table }}.{{ row.column }}</span>
                </template>
              </el-table-column>
              <el-table-column label="关联关系" width="80" align="center">
                <template #default>
                  <el-tag type="warning" size="small" effect="plain">→ FK →</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="引用表 (PK)" min-width="200">
                <template #default="{ row }">
                  <span class="col-name">{{ row.referencedTable }}.{{ row.referencedColumn }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'


import { getSchemaRelation } from '@/api/schema'
import { getDatasourceList } from '@/api/datasource'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'



const datasources = ref([])
const selectedDsId = ref(null)
const relationData = ref(null)
const loading = ref(false)
const loadError = ref('')

const chartRef = ref(null)
let chartInstance = null

// ==================== 计算属性 ====================

/** 是否有外键关系 */
const hasEdges = computed(() => {
  const edges = relationData.value?.graph?.edges
  return edges && edges.length > 0
})

// ==================== 数据获取 ====================

onMounted(() => {
  fetchDatasources()
})

onUnmounted(() => {
  destroyChart()
})

/** 获取所有数据源列表 */
async function fetchDatasources() {
  try {
    const res = await getDatasourceList(null)
    datasources.value = res.data || []
  } catch {
    datasources.value = []
  }
}

/** 获取关系分析数据 */
async function fetchRelation() {
  if (!selectedDsId.value) {
    relationData.value = null
    return
  }

  loading.value = true
  loadError.value = ''
  try {
    const res = await getSchemaRelation(selectedDsId.value)
    relationData.value = res.data
    // 数据返回后再渲染图表
    await nextTick()
    renderChart()
  } catch (e) {
    loadError.value = e.response?.data?.message || e.message || '加载失败'
    relationData.value = null
  } finally {
    loading.value = false
  }
}

// ==================== ECharts 图表 ====================

/** 初始化/重绘 ECharts 关系图 */
function renderChart() {
  destroyChart()

  const graph = relationData.value?.graph
  if (!graph || !graph.nodes?.length || !graph.edges?.length) {
    return
  }

  const chartDom = chartRef.value
  if (!chartDom) return

  chartInstance = echarts.init(chartDom)

  // 将 edges 反转：API 的 from→to 表示 FK表→引用表
  // 图形上展示为 引用表→FK表，即"被依赖方→依赖方"
  const links = graph.edges.map(e => ({
    source: e.to,       // 被引用表
    target: e.from,     // FK 持有表
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'edge') {
          return `${params.data.source} → ${params.data.target}`
        }
        return params.name
      }
    },
    animation: true,
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        data: graph.nodes.map(name => ({
          name,
          symbolSize: 50,
          label: {
            show: true,
            fontSize: 13,
            fontWeight: 'bold',
          },
          itemStyle: {
            color: '#409EFF',
            borderColor: '#337ECC',
            borderWidth: 2,
            borderRadius: 6,
          },
        })),
        links,
        force: {
          repulsion: 300,
          gravity: 0.15,
          edgeLength: [150, 250],
          layoutAnimation: true,
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [0, 12],
        lineStyle: {
          color: '#909399',
          width: 2,
          curveness: 0.2,
          opacity: 0.8,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 3,
          },
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0,0,0,0.3)',
          },
        },
      },
    ],
  }

  chartInstance.setOption(option)

  // 响应窗口大小变化
  window.addEventListener('resize', handleResize)
}

/** 销毁图表实例 */
function destroyChart() {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

/** 窗口大小变化时自适应 */
function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}



</script>

<style scoped>
.page-header { margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between; }
.page-header h3 { margin: 0; font-size: 20px; }

.config-card { margin-bottom: 20px; max-width: 1100px; }
.result-card { margin-bottom: 20px; max-width: 1100px; }

.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; gap: 8px; }

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

/* ==================== 图表 ==================== */
.chart-box {
  width: 100%;
  height: 500px;
}

.no-relation-box {
  padding: 40px;
}

/* ==================== 生成顺序 ==================== */
.order-list {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-left: 3px solid #409EFF;
  margin-left: 24px;
}

.order-index {
  min-width: 28px;
  text-align: center;
  font-weight: 600;
}

.order-table {
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  min-width: 150px;
}

.order-arrow {
  color: #409EFF;
  font-size: 18px;
  margin-top: 4px;
}

/* ==================== 通用 ==================== */
.col-name { font-family: 'Consolas', 'Menlo', monospace; font-weight: 600; color: #303133; }
</style>
