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
          <h3>Schema 结构</h3>
        </div>

        <!-- 选择数据源 -->
        <el-card class="config-card">
          <el-form :inline="true">
            <el-form-item label="数据源">
              <el-select
                v-model="selectedDsId"
                placeholder="请选择数据源"
                style="width: 320px"
                @change="fetchSchema"
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
            <p>正在加载 Schema...</p>
          </div>
        </el-card>

        <!-- 加载错误 -->
        <el-card v-else-if="loadError" class="result-card">
          <el-result icon="error" title="加载失败" :sub-title="loadError">
            <template #extra>
              <el-button @click="fetchSchema">重新加载</el-button>
            </template>
          </el-result>
        </el-card>

        <!-- 空状态 -->
        <el-card v-else-if="!selectedDsId" class="result-card">
          <el-empty description="请选择数据源查看 Schema 结构" />
        </el-card>

        <!-- Schema 展示 -->
        <template v-if="schemaData && !loading">
          <!-- 表列表 -->
          <el-card class="result-card">
            <template #header>
              <div class="card-header">
                <span>数据表</span>
                <el-tag type="info" effect="dark" size="small">{{ schemaData.tables.length }} 张表</el-tag>
              </div>
            </template>

            <el-table
              :data="schemaData.tables"
              border
              stripe
              highlight-current-row
              @row-click="selectTable"
              style="width: 100%"
              empty-text="暂无数据"
            >
              <el-table-column prop="tableName" label="表名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="tableComment" label="表注释" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.tableComment || '—' }}
                </template>
              </el-table-column>
              <el-table-column prop="columns" label="字段数量" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="primary">{{ row.columns?.length ?? 0 }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 字段详情 -->
          <el-card v-if="selectedTable" class="result-card">
            <template #header>
              <div class="card-header">
                <span>
                  <el-icon><Grid /></el-icon>
                  {{ selectedTable.tableName }} 字段
                </span>
                <el-tag size="small" type="primary">{{ selectedTable.columns?.length ?? 0 }} 列</el-tag>
              </div>
            </template>

            <el-table
              :data="selectedTable.columns"
              border
              stripe
              max-height="500"
              style="width: 100%"
              empty-text="暂无字段"
            >
              <el-table-column prop="ordinalPosition" label="#" width="50" align="center" />
              <el-table-column prop="name" label="字段名" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="col-name">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="120" show-overflow-tooltip />
              <el-table-column label="约束" width="100" align="center">
                <template #default="{ row }">
                  <div class="constraint-tags">
                    <el-tag v-if="row.primaryKey" type="danger" size="small" effect="dark">PK</el-tag>
                    <el-tag v-if="row.foreignRefTable" type="warning" size="small" effect="dark">FK</el-tag>
                  </div>
                  <span v-if="!row.primaryKey && !row.foreignRefTable" class="text-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="可空" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.nullable === false" type="danger" size="small" effect="plain">NOT NULL</el-tag>
                  <span v-else class="text-muted">NULL</span>
                </template>
              </el-table-column>
              <el-table-column label="外键引用" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.foreignRefTable" class="fk-ref">
                    {{ row.foreignRefTable }}.{{ row.foreignRefColumn }}
                  </span>
                  <span v-else class="text-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="注释" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.comment || '—' }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getSchemaCache } from '@/api/schema'
import { getDatasourceList } from '@/api/datasource'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const route = useRoute()
const activeMenu = computed(() => route.path)

const datasources = ref([])
const selectedDsId = ref(null)
const schemaData = ref(null)
const selectedTable = ref(null)
const loading = ref(false)
const loadError = ref('')

// ==================== 数据获取 ====================

onMounted(() => {
  fetchDatasources()
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

/** 获取 Schema 缓存 */
async function fetchSchema() {
  if (!selectedDsId.value) {
    schemaData.value = null
    selectedTable.value = null
    return
  }

  loading.value = true
  loadError.value = ''
  selectedTable.value = null
  try {
    const res = await getSchemaCache(selectedDsId.value)
    schemaData.value = res.data
    // 默认不选中任何表，等待用户点击
  } catch (e) {
    loadError.value = e.response?.data?.message || e.message || '加载失败'
    schemaData.value = null
  } finally {
    loading.value = false
  }
}

/** 点击表行 → 展示字段详情 */
function selectTable(row) {
  selectedTable.value = row
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

.config-card { margin-bottom: 20px; max-width: 1100px; }
.result-card { margin-bottom: 20px; max-width: 1100px; }

.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; gap: 8px; }

.loading-box {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px; color: #909399;
}
.loading-box p { margin-top: 12px; }

.col-name { font-family: 'Consolas', 'Menlo', monospace; font-weight: 600; color: #303133; }
.constraint-tags { display: flex; gap: 4px; justify-content: center; }
.fk-ref { font-family: 'Consolas', 'Menlo', monospace; color: #E6A23C; font-size: 13px; }
.text-muted { color: #c0c4cc; }
</style>
