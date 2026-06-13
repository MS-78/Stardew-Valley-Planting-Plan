<template>
  <div class="side-panel">
    <div class="panel-header">
      <h3>作物与工具</h3>
    </div>
    <el-collapse v-model="activeNames">
      <el-collapse-item
        v-for="category in categories"
        :key="category.id"
        :title="category.name"
        :name="category.id"
      >
        <div class="item-list">
          <div
            v-for="item in getItemsByCategory(category)"
            :key="item.id"
            class="draggable-item"
            draggable="true"
            @dragstart="handleDragStart($event, item, category.type)"
            @dragend="handleDragEnd"
          >
            <!-- 作物使用真实图片，工具使用 emoji -->
            <template v-if="category.type === 'crop'">
              <img
                v-if="!failedImages.has(item.name)"
                :src="`/crop-images/${item.name}.png`"
                :alt="item.name"
                class="item-icon-img"
                @error="failedImages.add(item.name)"
              />
              <span v-else class="item-icon">🌱</span>
            </template>
            <span v-else class="item-icon">🔧</span>
            <span class="item-name">{{ item.name }}</span>
            <span class="item-price" v-if="category.type === 'crop'">{{ item.seedPrice }}G</span>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAppStore } from '../stores/appStore'

const store = useAppStore()

// 记录加载失败的作物图片（降级显示 emoji）
const failedImages = reactive(new Set())

const props = defineProps({
  categories: {
    type: Array,
    required: true
  },
  crops: {
    type: Array,
    required: true
  },
  tools: {
    type: Array,
    required: true
  }
})

const activeNames = ref(props.categories.map(c => c.id))

const getItemsByCategory = (category) => {
  if (category.type === 'crop') {
    return props.crops.filter(c => c.seasons && c.seasons.includes(category.season))
  } else if (category.type === 'tool') {
    return props.tools
  }
  return []
}

const handleDragStart = (event, item, type) => {
  const dragData = {
    id: item.id,
    name: item.name,
    type: type,
    data: item
  }
  event.dataTransfer.setData('application/json', JSON.stringify(dragData))
  event.dataTransfer.effectAllowed = 'copy'
  // 设置共享拖拽状态，供 GridCanvas 预览作用范围
  store.dragState = { source: 'sidebar', item: dragData }
}

const handleDragEnd = () => {
  store.dragState = null
}
</script>

<style scoped>
.side-panel {
  width: 250px;
  height: 100%;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  background-color: #fafafa;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.item-list {
  padding: 8px;
}

.draggable-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 4px;
  background-color: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: grab;
  transition: all 0.2s ease;
}

.draggable-item:hover {
  background-color: #ecf5ff;
  border-color: #409eff;
  transform: translateX(2px);
}

.draggable-item:active {
  cursor: grabbing;
}

.item-icon {
  font-size: 18px;
  margin-right: 8px;
}

.item-icon-img {
  width: 24px;
  height: 24px;
  margin-right: 8px;
  object-fit: contain;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.item-name {
  flex: 1;
  font-size: 14px;
  color: #606266;
}

.item-price {
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
}
</style>
