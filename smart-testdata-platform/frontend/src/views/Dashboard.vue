<template>
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboardStats } from '@/api/project'

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

</script>

<style scoped>
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
