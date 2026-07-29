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
        <div class="page-header">
          <h3>项目管理</h3>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon> 新建项目
          </el-button>
        </div>

        <el-table :data="projects" v-loading="loading" border stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="项目名称" min-width="200" />
          <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="{ row }">
              {{ row.createdAt?.replace('T', ' ').substring(0, 19) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next"
            @current-change="fetchProjects" />
        </div>

        <!-- 新建/编辑对话框 -->
        <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑项目' : '新建项目'" width="500px">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="项目名称" />
            </el-form-item>
            <el-form-item label="描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="项目描述（可选）" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">
              {{ isEdit ? '更新' : '创建' }}
            </el-button>
          </template>
        </el-dialog>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getProjectList, createProject, updateProject, deleteProject } from '@/api/project'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/projects')

const projects = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

// 表单
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = ref({ name: '', description: '' })
const editId = ref(null)
const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

onMounted(() => {
  fetchProjects()
})

async function fetchProjects() {
  loading.value = true
  try {
    const res = await getProjectList(page.value, size.value)
    projects.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  isEdit.value = false
  editId.value = null
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

function showEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = { name: row.name, description: row.description || '' }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateProject(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createProject(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchProjects()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」吗？此操作不可恢复。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    fetchProjects()
  } catch (e) {
    // cancel or error
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout-header {
  background: #304156;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left h2 {
  color: #fff;
  font-size: 18px;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  color: #bfcbd9;
}

.layout-aside {
  background: #304156;
}

.layout-main {
  background: #f0f2f5;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h3 {
  margin: 0;
  font-size: 20px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
