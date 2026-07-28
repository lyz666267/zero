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
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-value">{{ stats.projectCount }}</div>
              <div class="stat-label">项目数</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-value">{{ stats.taskCount }}</div>
              <div class="stat-label">任务总数</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-value">{{ stats.successTaskCount }}</div>
              <div class="stat-label">成功任务</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 快捷入口 -->
        <el-card class="quick-card">
          <template #header>快捷操作</template>
          <el-space wrap>
            <el-button type="primary" @click="$router.push('/projects')">
              <el-icon><Plus /></el-icon> 新建项目
            </el-button>
          </el-space>
        </el-card>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getDashboardStats } from '@/api/project'

const router = useRouter()
const userStore = useUserStore()
const activeMenu = ref('/dashboard')

const stats = ref({
  projectCount: 0,
  taskCount: 0,
  successTaskCount: 0
})

onMounted(async () => {
  try {
    const res = await getDashboardStats()
    stats.value = res.data
  } catch (e) {
    // 401 handled by interceptor
  }
})

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

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 36px;
  font-weight: bold;
  color: #409EFF;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.quick-card {
  max-width: 600px;
}
</style>
