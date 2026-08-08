<template>
  <div class="page-header">
          <h3>Schema 结构</h3>
          <el-button
            type="primary"
            :disabled="!schemaData || aiAnalyzing"
            :loading="aiAnalyzing"
            @click="handleAiAnalyze"
          >
            <el-icon><MagicStick /></el-icon>
            AI 分析 Schema
          </el-button>
        </div>

        <!-- 选择数据源 -->
        <el-card class="config-card">
          <el-form :inline="true">
            <el-form-item label="项目">
              <el-select
                v-model="selectedProjectId"
                placeholder="请选择项目"
                style="width: 220px"
                @change="handleProjectChange"
              >
                <el-option
                  v-for="p in projects"
                  :key="p.id"
                  :label="p.name"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源">
              <el-select
                v-model="selectedDsId"
                :disabled="!selectedProjectId"
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

        <!-- AI 分析结果弹窗 -->
        <el-dialog
          v-model="aiDialogVisible"
          title="AI Schema 语义分析结果"
          width="800px"
          destroy-on-close
        >
          <div v-if="aiResult" class="ai-result">
            <!-- 概览 -->
            <el-descriptions v-if="aiResult.summary" :column="1" border class="ai-summary">
              <el-descriptions-item label="数据库">
                {{ aiResult.database || aiResult.summary?.database || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="分析概述">
                {{ aiResult.summary?.overview || aiResult.summary?.description || aiResult.description || '—' }}
              </el-descriptions-item>
            </el-descriptions>

            <!-- 表级分析 -->
            <div v-if="aiResult.tables?.length" class="ai-tables">
              <h4 style="margin: 16px 0 8px">表级语义分析</h4>
              <el-collapse>
                <el-collapse-item
                  v-for="(t, idx) in aiResult.tables"
                  :key="idx"
                  :name="idx"
                >
                  <template #title>
                    <div style="display: flex; align-items: center; gap: 8px;">
                      <span style="font-weight: 600; font-family: monospace;">{{ t.tableName || t.table }}</span>
                      <el-tag v-if="t.businessRole || t.role" size="small" type="success">
                        {{ t.businessRole || t.role }}
                      </el-tag>
                      <span style="color: #909399; font-size: 13px;">
                        {{ t.comment || t.description || '' }}
                      </span>
                    </div>
                  </template>
                  <el-table
                    v-if="t.columns?.length"
                    :data="t.columns"
                    size="small"
                    border
                    stripe
                  >
                    <el-table-column prop="name" label="字段" width="180" />
                    <el-table-column label="语义角色" min-width="200">
                      <template #default="{ row }">
                        <el-tag
                          v-if="row.semanticLabel || row.semanticRole || row.role"
                          size="small"
                          :type="getSemanticTagType(row.semanticLabel || row.semanticRole || row.role)"
                        >
                          {{ row.semanticLabel || row.semanticRole || row.role }}
                        </el-tag>
                        <span v-else style="color: #909399">—</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="敏感级别" width="100" align="center">
                      <template #default="{ row }">
                        <el-tag
                          v-if="getSensitivity(row)"
                          size="small"
                          :type="getSensitivityTagType(getSensitivity(row))"
                        >
                          {{ getSensitivity(row) }}
                        </el-tag>
                        <span v-else style="color: #909399">—</span>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-empty v-else description="该表无字段分析" :image-size="40" />
                </el-collapse-item>
              </el-collapse>
            </div>

            <!-- Mock 模式提示 -->
            <el-alert
              v-if="aiResult.mock"
              title="当前为 Mock 模式，分析结果由预置规则生成（非 AI 实时分析）"
              type="warning"
              :closable="false"
              show-icon
              style="margin-top: 16px"
            />
          </div>
        </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'


import { getSchemaCache, syncSchemaCache, analyzeSchema } from '@/api/schema'
import { getDatasourceList } from '@/api/datasource'
import { getProjectList } from '@/api/project'
import { ElMessage } from 'element-plus'



const projects = ref([])
const selectedProjectId = ref(null)
const datasources = ref([])
const selectedDsId = ref(null)
const schemaData = ref(null)
const selectedTable = ref(null)
const loading = ref(false)
const loadError = ref('')

// AI 分析相关
const aiAnalyzing = ref(false)
const aiDialogVisible = ref(false)
const aiResult = ref(null)

// ==================== 数据获取 ====================

onMounted(() => {
  fetchProjects()
})

/** 获取项目列表，默认选择第一个项目 */
async function fetchProjects() {
  try {
    const res = await getProjectList(1, 100)
    projects.value = res.data.records || []
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      fetchDatasources()
    }
  } catch (e) {
    console.error(e)
  }
}

/** 切换项目时清空已选数据源 */
async function handleProjectChange() {
  selectedDsId.value = null
  schemaData.value = null
  selectedTable.value = null
  await fetchDatasources()
}

/** 获取所有数据源列表 */
async function fetchDatasources() {
  if (!selectedProjectId.value) {
    datasources.value = []
    schemaData.value = null
    selectedTable.value = null
    return
  }

  try {
    const res = await getDatasourceList(selectedProjectId.value)
    datasources.value = res.data || []
  } catch (e) {
    console.error(e)
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
    let res
    try {
      res = await getSchemaCache(selectedDsId.value)
      schemaData.value = res.data
    } catch (e) {
      const cacheMissing = (e.response?.data?.message || '').includes('Schema 缓存不存在')
      if (!cacheMissing) {
        throw e
      }
      await syncSchemaCache(selectedDsId.value)
      res = await getSchemaCache(selectedDsId.value)
      schemaData.value = res.data
    }
    // 默认不选中任何表，等待用户点击
  } catch (e) {
    loadError.value = e.response?.data?.message || e.message || '加载失败'
    schemaData.value = null
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

/** 点击表行 → 展示字段详情 */
function selectTable(row) {
  selectedTable.value = row
}

// ==================== AI 分析 ====================

/** 调用 AI 语义分析 Schema */
async function handleAiAnalyze() {
  if (!schemaData.value?.tables?.length) {
    ElMessage.warning('请先加载 Schema')
    return
  }

  aiAnalyzing.value = true
  aiResult.value = null
  try {
    const res = await analyzeSchema({
      database: schemaData.value.database || '',
      dbType: 'MySQL',
      tables: schemaData.value.tables.map(t => ({
        tableName: t.tableName,
        comment: t.tableComment || '',
        columns: (t.columns || []).map(c => ({
          name: c.name,
          type: c.type,
          comment: c.comment || '',
          primaryKey: c.primaryKey || false,
          foreignRefTable: c.foreignRefTable || null,
          foreignRefColumn: c.foreignRefColumn || null
        }))
      }))
    })

    const data = res.data
    if (data?.success && data?.result) {
      aiResult.value = data.result
      aiDialogVisible.value = true
      const mockLabel = data.mock ? ' [Mock模式]' : ''
      ElMessage.success(`AI 分析完成${mockLabel}`)
    } else {
      ElMessage.error(data?.message || 'AI 分析返回为空')
    }
  } catch (e) {
    console.error('AI 分析失败:', e)
    ElMessage.error(e.response?.data?.message || e.message || 'AI 分析失败，请检查 AI 服务是否可用')
  } finally {
    aiAnalyzing.value = false
  }
}

/** 语义标签 → Element 类型 */
function getSemanticTagType(role) {
  if (!role) return 'info'
  const r = role.toLowerCase()
  // AI 返回的英文标签映射
  const labelMap = {
    'person_name': 'primary', 'email': 'danger', 'phone': 'danger',
    'id_card': 'danger', 'address': 'warning', 'bank_card': 'danger',
    'amount': 'primary', 'date_time': 'info', 'boolean_flag': 'info',
    'enum_value': 'success', 'identifier': 'warning', 'text_content': 'info',
    'url_path': 'info', 'unknown': 'info'
  }
  if (labelMap[r]) return labelMap[r]
  // 中文标签映射
  if (r.includes('敏感') || r.includes('隐私') || r.includes('身份证') || r.includes('手机') || r.includes('email')) return 'danger'
  if (r.includes('主键') || r.includes('外键') || r.includes('标识')) return 'warning'
  if (r.includes('业务') || r.includes('金额') || r.includes('价格')) return 'primary'
  return 'info'
}

/** 从 AI 响应中提取敏感级别 */
function getSensitivity(row) {
  // 新格式：sensitiveDetection.sensitiveType
  if (row.sensitiveDetection?.sensitive && row.sensitiveDetection?.sensitiveType) {
    const type = row.sensitiveDetection.sensitiveType
    if (type === 'NONE') return ''
    const highTypes = ['PHONE', 'EMAIL', 'ID_CARD', 'BANK_CARD']
    if (highTypes.includes(type)) return 'HIGH'
    return 'MEDIUM'
  }
  // 旧格式兼容
  if (row.sensitivity) return row.sensitivity
  return ''
}

/** 敏感级别 → Tag 类型 */
function getSensitivityTagType(level) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
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

.col-name { font-family: 'Consolas', 'Menlo', monospace; font-weight: 600; color: #303133; }
.constraint-tags { display: flex; gap: 4px; justify-content: center; }
.fk-ref { font-family: 'Consolas', 'Menlo', monospace; color: #E6A23C; font-size: 13px; }
.text-muted { color: #c0c4cc; }
</style>
