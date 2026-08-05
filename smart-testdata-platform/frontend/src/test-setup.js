import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// Register Element Plus globally for all tests
config.global.plugins = [ElementPlus]

// Register all Element Plus icons as global stubs
const iconStubs = {}
for (const [key] of Object.entries(ElementPlusIconsVue)) {
  iconStubs[key] = { template: '<span class="el-icon-stub"></span>' }
}
config.global.stubs = {
  ...iconStubs,
  RouterView: { template: '<div class="router-view-stub"><slot /></div>' }
}
