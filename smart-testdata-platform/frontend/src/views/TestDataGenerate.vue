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
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <div class="page-header">
          <h3>测试数据生成</h3>
        </div>

        <!-- 步骤1：选择数据源 + 输入需求 -->
        <el-card class="config-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">1</span> 配置生成参数
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
              <el-select v-model="selectedDsId" placeholder="请先选择项目" style="width: 300px"
                :disabled="!selectedProjectId" @change="fetchSchema">
                <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.dbName})`" :value="ds.id" />
              </el-select>
              <el-button style="margin-left: 12px" :loading="schemaLoading" :disabled="!selectedDsId"
                @click="fetchSchema">
                加载 Schema
              </el-button>
            </el-form-item>

            <!-- Schema 加载状态 -->
            <el-form-item v-if="schemaLoaded" label="Schema 状态">
              <el-tag type="success">
                已加载：{{ schemaData.database }} — {{ schemaData.tables?.length || 0 }} 张表
              </el-tag>
            </el-form-item>

            <!-- 选择目标表 -->
            <el-form-item v-if="schemaData.tables?.length" label="目标表">
              <el-select v-model="form.targetTable" placeholder="全部表" clearable style="width: 300px"
                @change="onTableChange">
                <el-option label="全部表" value="" />
                <el-option v-for="t in schemaData.tables" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
              </el-select>
              <span class="form-hint">不选则对所有表生成计划</span>
            </el-form-item>

            <!-- 用户需求 -->
            <el-form-item label="生成需求" prop="requirement">
              <el-input v-model="form.requirement" type="textarea" :rows="2"
                placeholder="例如：生成1000条用户测试数据，年龄18-60，邮箱真实格式" />
            </el-form-item>

            <!-- 快捷示例 -->
            <el-form-item label="快捷需求">
              <el-space wrap>
                <el-tag v-for="q in quickRequirements" :key="q" class="quick-tag"
                  :type="form.requirement === q ? 'primary' : 'info'" @click="form.requirement = q">
                  {{ q }}
                </el-tag>
              </el-space>
            </el-form-item>

            <!-- 生成按钮 -->
            <el-form-item>
              <el-button type="primary" size="large" :loading="generating" :disabled="!canGenerate"
                @click="handleGenerate">
                <el-icon><MagicStick /></el-icon>
                生成测试数据计划
              </el-button>
              <el-tag v-if="result && result.mock" type="warning" style="margin-left: 12px">
                Mock 模式（未配置 LLM API Key）
              </el-tag>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 步骤2：生成结果 -->
        <el-card v-if="result" class="result-card">
          <template #header>
            <div class="card-header">
              <span class="step-badge">2</span> 生成计划 — {{ result.plan?.taskName || '结果' }}
            </div>
          </template>

          <!-- 按表展示 -->
          <el-tabs v-if="result.plan?.tables?.length" type="border-card">
            <el-tab-pane v-for="(table, ti) in result.plan.tables" :key="ti"
              :label="`${table.table} (${table.count} 条)`">
              <el-table :data="table.fields" border stripe size="small" empty-text="该表无字段">
                <el-table-column type="index" label="#" width="50" />
                <el-table-column prop="name" label="字段名" width="180" />
                <el-table-column prop="generator" label="生成器" width="200">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.generator }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="范围/参数" min-width="300">
                  <template #default="{ row }">
                    <span v-if="row.range">
                      范围: {{ row.range.min }} ~ {{ row.range.max }}
                    </span>
                    <span v-if="row.params">
                      {{ JSON.stringify(row.params) }}
                    </span>
                    <span v-if="!row.range && !row.params" style="color: #909399">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>

          <!-- 无 tables 字段时使用旧格式 -->
          <el-table v-else-if="result.plan?.fields?.length" :data="result.plan.fields" border stripe size="small">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="字段名" width="180" />
            <el-table-column prop="generator" label="生成器" width="200">
              <template #default="{ row }">
                <el-tag size="small">{{ row.generator }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="范围/参数" min-width="300">
              <template #default="{ row }">
                <span v-if="row.range">范围: {{ row.range.min }} ~ {{ row.range.max }}</span>
                <span v-if="row.params">{{ JSON.stringify(row.params) }}</span>
                <span v-if="!row.range && !row.params" style="color: #909399">—</span>
              </template>
            </el-table-column>
          </el-table>

          <!-- JSON 原始输出 -->
          <el-collapse style="margin-top: 20px">
            <el-collapse-item title="查看原始 JSON" name="json">
              <div class="json-block">
                <pre>{{ JSON.stringify(result, null, 2) }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
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
import { getDatasourceList, getSchema } from '@/api/datasource'
import { generatePlan } from '@/api/testdata'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/testdata')

// 项目选择
const projects = ref([])
const selectedProjectId = ref(null)

// 数据源选择
const datasources = ref([])
const selectedDsId = ref(null)

// Schema
const schemaLoading = ref(false)
const schemaLoaded = ref(false)
const schemaData = ref({ database: '', tables: [] })

// 表单
const form = reactive({
  requirement: '',
  targetTable: ''
})

// 快捷需求示例
const quickRequirements = [
  '生成100条用户数据',
  '生成1000条订单数据',
  '生成500条商品数据',
  '生成100条包含关联关系的数据'
]

// 生成结果
const generating = ref(false)
const result = ref(null)

// 能否生成
const canGenerate = computed(() => {
  return schemaLoaded.value && form.requirement.trim()
})

onMounted(async () => {
  try {
    const res = await getProjectList(1, 100)
    projects.value = res.data.records
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      fetchDatasources()
    }
  } catch (e) { /* handled */ }
})

/** 加载数据源列表 */
async function fetchDatasources() {
  if (!selectedProjectId.value) return
  try {
    const res = await getDatasourceList(selectedProjectId.value)
    datasources.value = res.data || []
  } catch (e) { /* handled */ }
}

/** 加载 Schema */
async function fetchSchema() {
  if (!selectedDsId.value) return
  schemaLoading.value = true
  schemaLoaded.value = false
  try {
    const res = await getSchema(selectedDsId.value)
    schemaData.value = res.data
    schemaLoaded.value = true
    ElMessage.success(`Schema 加载成功: ${res.data.tables?.length || 0} 张表`)
  } catch (e) {
    ElMessage.error('Schema 加载失败')
  } finally {
    schemaLoading.value = false
  }
}

/** 当选择特定表时，自动更新需求 */
function onTableChange(val) {
  if (val) {
    if (!form.requirement || form.requirement === quickRequirements[0]) {
      form.requirement = `生成100条${val}数据`
    }
  }
}

/** 生成计划 */
async function handleGenerate() {
  if (!canGenerate.value) return
  generating.value = true
  result.value = null
  try {
    // 构建请求 Schema（如果选择了特定表则过滤）
    let schema = schemaData.value
    if (form.targetTable) {
      schema = {
        ...schemaData.value,
        tables: schemaData.value.tables.filter(t => t.tableName === form.targetTable)
      }
    }

    const res = await generatePlan({
      schema: schema,
      requirement: form.requirement
    })
    result.value = res.data

    const tableCount = res.data.plan?.tables?.length || 1
    const mockLabel = res.data.mock ? ' [Mock模式]' : ''
    ElMessage.success(`计划生成成功：${tableCount} 张表${mockLabel}`)
  } catch (e) {
    // handled by interceptor
  } finally {
    generating.value = false
  }
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

.form-hint { margin-left: 12px; color: #909399; font-size: 13px; }

.quick-tag { cursor: pointer; }
.quick-tag:hover { opacity: 0.8; }

.json-block {
  background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 6px;
  max-height: 500px; overflow: auto; font-size: 13px; line-height: 1.6;
}
.json-block pre { margin: 0; white-space: pre-wrap; word-break: break-all; }
</style>
