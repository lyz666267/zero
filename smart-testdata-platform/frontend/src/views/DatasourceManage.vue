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
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <!-- 顶部操作栏 -->
        <div class="page-header">
          <div class="header-left-section">
            <h3>数据源管理</h3>
            <el-select v-model="selectedProjectId" placeholder="请选择项目" style="width: 240px; margin-left: 16px"
              @change="fetchList">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </div>
          <el-button type="primary" @click="showAddDialog" :disabled="!selectedProjectId">
            <el-icon><Plus /></el-icon> 添加数据源
          </el-button>
        </div>

        <!-- 数据源列表 -->
        <el-table :data="datasources" v-loading="loading" border stripe empty-text="请先选择一个项目">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" width="160" />
          <el-table-column prop="dbType" label="类型" width="90" />
          <el-table-column label="连接地址" min-width="220">
            <template #default="{ row }">{{ row.host }}:{{ row.port }}/{{ row.dbName }}</template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="handleTest(row)">测试连接</el-button>
              <el-button size="small" type="warning" @click="showEditDialog(row)">编辑</el-button>
              <el-button size="small" type="info" @click="showSchema(row)">查看 Schema</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-main>
    </el-container>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑数据源' : '添加数据源'" width="560px"
      @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例：测试数据库" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" style="width: 100%">
            <el-option label="MySQL" value="MySQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="例：127.0.0.1" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="数据库密码" />
        </el-form-item>
        <el-form-item label="数据库名" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="例：test_db" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="success" :loading="testing" @click="handleTestConnect">测试连接</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Schema 查看对话框 -->
    <el-dialog v-model="schemaVisible" :title="'Schema — ' + schemaData.database" width="800px" top="5vh">
      <div v-loading="schemaLoading">
        <el-alert v-if="schemaError" :title="schemaError" type="error" show-icon closable />
        <el-collapse v-else-if="schemaData.tables">
          <el-collapse-item v-for="table in schemaData.tables" :key="table.tableName"
            :title="`${table.tableName} — ${table.comment || ''}（${table.columns?.length || 0} 列）`">
            <el-table :data="table.columns" border size="small" max-height="400">
              <el-table-column prop="name" label="字段名" width="160" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="length" label="长度" width="70" />
              <el-table-column label="主键" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.primary" type="danger" size="small">PK</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="可空" width="60">
                <template #default="{ row }">
                  <span :style="{ color: row.nullable ? '#67c23a' : '#f56c6c' }">{{ row.nullable ? 'YES' : 'NO' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="defaultValue" label="默认值" width="120" />
              <el-table-column label="外键" width="180">
                <template #default="{ row }">
                  <el-tag v-if="row.foreignRefTable" type="warning" size="small">
                    → {{ row.foreignRefTable }}.{{ row.foreignRefColumn }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="注释" min-width="200" show-overflow-tooltip />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getProjectList } from '@/api/project'
import {
  getDatasourceList, createDatasource, updateDatasource, deleteDatasource,
  testConnection, getSchema
} from '@/api/datasource'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/datasources')

// 项目选择
const projects = ref([])
const selectedProjectId = ref(null)

// 数据源列表
const datasources = ref([])
const loading = ref(false)

// 表单
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const testing = ref(false)
const formRef = ref(null)
const editId = ref(null)
const form = reactive({
  name: '', dbType: 'MySQL', host: '', port: 3306,
  username: '', password: '', databaseName: ''
})
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }]
}

// Schema 对话框
const schemaVisible = ref(false)
const schemaLoading = ref(false)
const schemaError = ref('')
const schemaData = ref({ database: '', tables: [] })

onMounted(async () => {
  try {
    const res = await getProjectList(1, 100)
    projects.value = res.data.records
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      fetchList()
    }
  } catch (e) { /* handled by interceptor */ }
})

async function fetchList() {
  if (!selectedProjectId.value) { datasources.value = []; return }
  loading.value = true
  try {
    const res = await getDatasourceList(selectedProjectId.value)
    datasources.value = res.data || []
  } finally { loading.value = false }
}

// ==================== 新建 / 编辑 ====================

function showAddDialog() {
  isEdit.value = false; editId.value = null
  Object.assign(form, { name: '', dbType: 'MySQL', host: '', port: 3306, username: '', password: '', databaseName: '' })
  dialogVisible.value = true
}

function showEditDialog(row) {
  isEdit.value = true; editId.value = row.id
  Object.assign(form, {
    name: row.name, dbType: row.dbType, host: row.host, port: row.port,
    username: row.username, password: '', databaseName: row.dbName
  })
  // 编辑时密码非必填（留空表示不修改）
  rules.password[0].required = false
  dialogVisible.value = true
}

function resetForm() {
  rules.password[0].required = true
  formRef.value?.resetFields()
}

async function handleTestConnect() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  testing.value = true
  try {
    const res = await testConnection({ ...form, projectId: selectedProjectId.value })
    if (res.data) {
      ElMessage.success('连接成功！')
    } else {
      ElMessage.error('连接失败，请检查配置')
    }
  } finally { testing.value = false }
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = { ...form, projectId: selectedProjectId.value }
    if (isEdit.value) {
      await updateDatasource(editId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createDatasource(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
}

// ==================== 操作 ====================

async function handleTest(row) {
  loading.value = true
  try {
    const res = await testConnection({
      name: row.name, dbType: row.dbType, host: row.host, port: row.port,
      username: row.username, password: '', databaseName: row.dbName,
      projectId: row.projectId
    })
    if (res.data) {
      ElMessage.success(`连接「${row.name}」成功`)
      fetchList()
    } else {
      ElMessage.error(`连接「${row.name}」失败`)
    }
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除数据源「${row.name}」吗？`, '确认删除', { type: 'warning' })
    await deleteDatasource(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) { /* cancel */ }
}

async function showSchema(row) {
  schemaVisible.value = true
  schemaLoading.value = true
  schemaError.value = ''
  schemaData.value = { database: '', tables: [] }
  try {
    const res = await getSchema(row.id)
    schemaData.value = res.data
  } catch (e) {
    schemaError.value = 'Schema 读取失败: ' + (e.response?.data?.message || e.message)
  } finally { schemaLoading.value = false }
}

function statusType(status) {
  return status === 'CONNECTED' ? 'success' : status === 'ERROR' ? 'danger' : 'info'
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
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.header-left-section { display: flex; align-items: center; }
.header-left-section h3 { margin: 0; font-size: 20px; }
</style>
