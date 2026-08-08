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
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
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
          <el-menu-item index="/schema/view">
            <el-icon><Files /></el-icon>
            <span>Schema 结构</span>
          </el-menu-item>
          <el-menu-item index="/schema/relation">
            <el-icon><Share /></el-icon>
            <span>数据库关系图</span>
          </el-menu-item>
          <el-menu-item index="/testdata">
            <el-icon><MagicStick /></el-icon>
            <span>测试数据生成</span>
          </el-menu-item>
          <el-menu-item index="/testdata/task">
            <el-icon><List /></el-icon>
            <span>创建生成任务</span>
          </el-menu-item>
          <el-menu-item index="/testdata/plan">
            <el-icon><MagicStick /></el-icon>
            <span>AI 生成计划</span>
          </el-menu-item>
          <el-menu-item index="/task-monitor">
            <el-icon><Monitor /></el-icon>
            <span>任务监控</span>
          </el-menu-item>
          <el-menu-item index="/agent-trace">
            <el-icon><Connection /></el-icon>
            <span>Agent 执行轨迹</span>
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
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 根据当前路由自动高亮菜单项
const activeMenu = computed(() => route.path)

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
</style>
