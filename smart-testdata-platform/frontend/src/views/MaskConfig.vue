<template>
        <div class="page-header">
          <h3>隐私脱敏配置</h3>
          <span class="page-desc">查看脱敏规则、实时测试脱敏效果</span>
        </div>

        <!-- 脱敏规则卡片 -->
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Collection /></el-icon> 脱敏策略（共 {{ rules.length }} 种）</span>
              <el-tag type="info" effect="plain">基于字段类型自动匹配</el-tag>
            </div>
          </template>

          <el-row v-if="rules.length > 0" :gutter="20">
            <el-col v-for="rule in rules" :key="rule.sensitiveType" :span="8">
              <el-card class="rule-card" shadow="hover">
                <!-- 类型标签 -->
                <div class="rule-type-header">
                  <el-tag :type="typeColorMap[rule.sensitiveType] || 'info'" size="small" effect="dark">
                    {{ rule.typeLabel }}
                  </el-tag>
                  <span class="strategy-code">{{ rule.strategy }}</span>
                </div>

                <!-- 策略描述 -->
                <p class="rule-desc">{{ rule.description }}</p>

                <!-- 示例对比 -->
                <div class="example-compare">
                  <div class="example-item before">
                    <span class="example-label">原始</span>
                    <span class="example-value">{{ rule.exampleInput }}</span>
                  </div>
                  <div class="example-arrow">
                    <el-icon><ArrowRight /></el-icon>
                  </div>
                  <div class="example-item after">
                    <span class="example-label">脱敏</span>
                    <span class="example-value masked">{{ rule.exampleOutput }}</span>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 加载中 -->
          <div v-if="rulesLoading" class="loading-area">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>加载脱敏规则中...</p>
          </div>

          <!-- 空状态 -->
          <el-empty v-if="!rulesLoading && rules.length === 0" description="暂无脱敏规则" />
        </el-card>

        <!-- 实时测试区 -->
        <el-card class="section-card">
          <template #header>
            <span><el-icon><EditPen /></el-icon> 实时脱敏测试</span>
          </template>

          <el-form :model="testForm" label-width="100px" class="test-form">
            <el-form-item label="脱敏策略">
              <el-select v-model="testForm.strategy" placeholder="选择脱敏策略" @change="handleTestMask" style="width: 260px">
                <el-option
                  v-for="rule in rules"
                  :key="rule.strategy"
                  :label="`${rule.typeLabel} — ${rule.strategy}`"
                  :value="rule.strategy"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="原始值">
              <el-input
                v-model="testForm.value"
                placeholder="输入要脱敏的值，如：13812345678"
                clearable
                @input="handleTestMask"
                style="width: 400px"
              >
                <template #prefix>
                  <el-icon><Edit /></el-icon>
                </template>
              </el-input>
              <el-button
                v-if="rules.length > 0"
                style="margin-left: 12px"
                @click="handleQuickFill"
                type="primary" plain size="small"
              >
                <el-icon><RefreshLeft /></el-icon>
                填充示例
              </el-button>
            </el-form-item>

            <el-form-item v-if="testResult !== null" label="脱敏结果">
              <div class="test-result-area">
                <div class="result-before">
                  <span class="result-label">脱敏前</span>
                  <el-tag type="info" size="large">{{ testForm.value }}</el-tag>
                </div>
                <el-icon :size="24" class="result-arrow"><ArrowRight /></el-icon>
                <div class="result-after">
                  <span class="result-label">脱敏后</span>
                  <el-tag type="success" size="large" effect="dark">{{ testResult }}</el-tag>
                </div>
              </div>
            </el-form-item>

            <el-form-item v-if="testError" label="">
              <el-alert type="error" :title="testError" show-icon :closable="false" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 脱敏流程说明 -->
        <el-card class="section-card">
          <template #header>
            <span><el-icon><InfoFilled /></el-icon> 三层融合检测流程</span>
          </template>

          <div class="pipeline">
            <div class="pipeline-step">
              <div class="pipeline-icon step-regex">
                <el-icon :size="28"><Search /></el-icon>
              </div>
              <div class="pipeline-content">
                <h4>第 1 层：正则检测</h4>
                <p>对数据值进行正则匹配（手机号/邮箱/身份证/银行卡格式），置信度最高（0.85-0.98），优先级最高</p>
                <el-tag size="small" type="danger" effect="plain">最高优先级</el-tag>
              </div>
            </div>
            <div class="pipeline-connector">
              <el-icon :size="20"><ArrowDown /></el-icon>
            </div>
            <div class="pipeline-step">
              <div class="pipeline-icon step-keyword">
                <el-icon :size="28"><Key /></el-icon>
              </div>
              <div class="pipeline-content">
                <h4>第 2 层：关键词检测</h4>
                <p>根据字段名关键词匹配（phone/email/name/id_card 等），置信度 0.80-0.95</p>
                <el-tag size="small" type="warning" effect="plain">中等优先级</el-tag>
              </div>
            </div>
            <div class="pipeline-connector">
              <el-icon :size="20"><ArrowDown /></el-icon>
            </div>
            <div class="pipeline-step">
              <div class="pipeline-icon step-llm">
                <el-icon :size="28"><Cpu /></el-icon>
              </div>
              <div class="pipeline-content">
                <h4>第 3 层：LLM 语义检测</h4>
                <p>AI 大模型分析字段语义上下文（字段名+注释+样本值），识别非常规命名敏感字段</p>
                <el-tag size="small" type="info" effect="plain">补充检测</el-tag>
              </div>
            </div>
          </div>
        </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getMaskRules, testMask } from '@/api/privacy'
import {
  DataAnalysis, FolderOpened, Coin, MagicStick, List, Monitor, Connection,
  Lock, Collection, EditPen, InfoFilled, Search, Key, Cpu,
  ArrowRight, ArrowDown, Edit, RefreshLeft, Loading
} from '@element-plus/icons-vue'


const rules = ref([])
const rulesLoading = ref(false)

const testForm = reactive({
  strategy: '',
  value: ''
})
const testResult = ref(null)
const testError = ref('')

// 定时器防抖
let testTimer = null

// 类型→颜色映射
const typeColorMap = {
  PHONE: 'danger',
  EMAIL: 'warning',
  ID_CARD: 'danger',
  NAME: '',
  ADDRESS: 'success',
  BANK_CARD: 'danger'
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchRules()
})

/** 获取所有脱敏规则 */
async function fetchRules() {
  rulesLoading.value = true
  try {
    const res = await getMaskRules()
    if (res.code === 200) {
      rules.value = res.data || []
    }
  } catch (e) {
    console.error('获取脱敏规则失败:', e)
  } finally {
    rulesLoading.value = false
  }
}

// ==================== 测试脱敏 ====================

/** 执行脱敏测试（带防抖） */
function handleTestMask() {
  // 清除之前的错误
  testError.value = ''
  testResult.value = null

  if (!testForm.strategy || !testForm.value) return

  // 防抖 300ms
  if (testTimer) clearTimeout(testTimer)
  testTimer = setTimeout(async () => {
    try {
      const res = await testMask({
        strategy: testForm.strategy,
        value: testForm.value
      })
      if (res.code === 200 && res.data) {
        testResult.value = res.data.maskedValue
      }
    } catch (e) {
      testError.value = '脱敏测试失败: ' + (e.response?.data?.message || e.message)
    }
  }, 300)
}

/** 快速填充当前选中策略的示例值 */
function handleQuickFill() {
  if (!testForm.strategy) return
  const rule = rules.value.find(r => r.strategy === testForm.strategy)
  if (rule && rule.exampleInput) {
    testForm.value = rule.exampleInput
    handleTestMask()
  }
}

</script>

<style scoped>

.page-header { margin-bottom: 24px; }
.page-header h3 { margin: 0 0 4px 0; font-size: 20px; }
.page-desc { color: #909399; font-size: 14px; }

.section-card { margin-bottom: 24px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }

/* ====== 规则卡片 ====== */
.rule-card {
  margin-bottom: 20px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s;
  height: 100%;
}
.rule-card:hover {
  border-color: #409EFF;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.rule-type-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 12px;
}
.strategy-code {
  font-family: monospace; font-size: 12px; color: #909399; background: #f5f7fa;
  padding: 2px 8px; border-radius: 4px;
}

.rule-desc {
  color: #606266; font-size: 13px; line-height: 1.7; margin: 0 0 16px 0;
}

/* 示例对比 */
.example-compare {
  display: flex; align-items: center; gap: 8px;
  background: #fafafa; border: 1px dashed #e4e7ed;
  border-radius: 8px; padding: 12px;
}
.example-item {
  flex: 1; display: flex; flex-direction: column; gap: 4px; min-width: 0;
}
.example-label {
  font-size: 11px; color: #909399; text-transform: uppercase;
}
.example-value {
  font-family: monospace; font-size: 13px; color: #303133;
  word-break: break-all;
}
.example-value.masked {
  color: #409EFF; font-weight: 600;
}
.example-arrow {
  flex-shrink: 0; color: #C0C4CC;
}

.loading-area {
  text-align: center; padding: 40px; color: #909399;
}

/* ====== 测试表单 ====== */
.test-form {
  max-width: 700px;
}

/* 测试结果展示 */
.test-result-area {
  display: flex; align-items: center; gap: 16px;
  background: #f0f9eb; border: 1px solid #e1f3d8;
  border-radius: 8px; padding: 16px 24px;
  width: 100%;
}
.result-before, .result-after {
  display: flex; flex-direction: column; gap: 6px; align-items: center;
}
.result-label {
  font-size: 12px; color: #909399;
}
.result-arrow {
  color: #67C23A; flex-shrink: 0;
}

/* ====== 三层检测流程 ====== */
.pipeline {
  padding: 8px 0;
}
.pipeline-step {
  display: flex; align-items: flex-start; gap: 20px;
  background: #fff; border: 1px solid #e4e7ed;
  border-radius: 10px; padding: 20px 24px;
  transition: all 0.3s;
}
.pipeline-step:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}
.pipeline-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; color: #fff;
}
.step-regex { background: linear-gradient(135deg, #F56C6C, #E6A23C); }
.step-keyword { background: linear-gradient(135deg, #E6A23C, #409EFF); }
.step-llm { background: linear-gradient(135deg, #409EFF, #67C23A); }

.pipeline-content { flex: 1; }
.pipeline-content h4 { margin: 0 0 6px 0; font-size: 15px; color: #303133; }
.pipeline-content p { margin: 0 0 8px 0; font-size: 13px; color: #606266; line-height: 1.7; }

.pipeline-connector {
  display: flex; justify-content: center; padding: 8px 0; color: #C0C4CC;
}
</style>
