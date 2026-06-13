<script setup>
import { ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import PlanningView from './views/PlanningView.vue'
import DataView from './views/DataView.vue'

const currentView = ref('planning')
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="app-container">
      <aside class="sidebar">
        <div class="sidebar-header">
          <h2>🌾 星露谷规划</h2>
        </div>
        <el-menu
          :default-active="currentView"
          @select="(key) => currentView = key"
          class="sidebar-menu"
        >
          <el-menu-item index="planning">
            <el-icon><EditPen /></el-icon>
            <span>开始种植规划</span>
          </el-menu-item>
          <el-menu-item index="data">
            <el-icon><Setting /></el-icon>
            <span>植株与工具数据管理</span>
          </el-menu-item>
        </el-menu>
      </aside>
      <main class="main-content">
        <PlanningView v-if="currentView === 'planning'" />
        <DataView v-else />
      </main>
    </div>
  </el-config-provider>
</template>

<style scoped>
.app-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 200px;
  min-width: 200px;
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
}

.main-content {
  flex: 1;
  overflow: auto;
  background: #fff;
}
</style>
