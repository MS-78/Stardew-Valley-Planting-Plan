<template>
  <el-dialog
    :model-value="visible"
    title="自动规划 - 选择作物"
    width="600px"
    @update:model-value="$emit('update:visible', $event)"
    @closed="$emit('closed')"
  >
    <div class="crop-selection">
      <div class="mode-selector">
        <span class="mode-label">规划模式：</span>
        <el-radio-group v-model="algorithmMode">
          <el-radio-button value="max_roi">极限投入产出比</el-radio-button>
          <el-radio-button value="weighted_balanced">加权均衡</el-radio-button>
          <el-radio-button value="fully_balanced">完全均衡</el-radio-button>
        </el-radio-group>
      </div>

      <div class="selection-header">
        <el-checkbox
          v-model="selectAll"
          :indeterminate="isIndeterminate"
          @change="handleSelectAll"
        >
          全选
        </el-checkbox>
        <span class="selection-count">
          已选 {{ selectedCrops.length }} / {{ crops.length }} 种作物
        </span>
      </div>

      <el-table
        :data="crops"
        @selection-change="handleSelectionChange"
        ref="tableRef"
        max-height="400"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="作物名称" width="120" />
        <el-table-column prop="seedPrice" label="种子价格" width="100">
          <template #default="{ row }">
            {{ row.seedPrice }}G
          </template>
        </el-table-column>
        <el-table-column prop="baseSellPrice" label="售价" width="80">
          <template #default="{ row }">
            {{ row.baseSellPrice }}G
          </template>
        </el-table-column>
        <el-table-column prop="growthDays" label="生长天数" width="90" />
        <el-table-column label="ROI" width="80">
          <template #default="{ row }">
            <span class="roi-value">
              {{ calculateROI(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="特性" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.canRegrow" type="success" size="small">可重复</el-tag>
            <el-tag v-if="!row.isWalkable" type="warning" size="small">棚架</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button
        type="primary"
        @click="handleConfirm"
        :loading="loading"
        :disabled="selectedCrops.length === 0"
      >
        开始自动规划
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  crops: {
    type: Array,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'confirm', 'closed'])

const tableRef = ref(null)
const selectedCrops = ref([])
const algorithmMode = ref('max_roi')

const selectAll = computed({
  get: () => selectedCrops.value.length === props.crops.length,
  set: (val) => {
    if (val) {
      selectedCrops.value = [...props.crops]
    } else {
      selectedCrops.value = []
    }
  }
})

const isIndeterminate = computed(() => {
  return selectedCrops.value.length > 0 && selectedCrops.value.length < props.crops.length
})

const handleSelectAll = (val) => {
  if (val) {
    tableRef.value?.toggleAllSelection()
  } else {
    tableRef.value?.clearSelection()
  }
}

const handleSelectionChange = (selection) => {
  selectedCrops.value = selection
}

// 前端计算ROI（展示用，后端CropCalculator为准）
const calculateROI = (crop) => {
  if (!crop.canRegrow) {
    const harvests = Math.floor(27 / crop.growthDays)
    const revenue = crop.baseSellPrice * harvests
    const cost = crop.seedPrice * harvests
    return cost > 0 ? (revenue / cost).toFixed(2) : '∞'
  } else {
    const harvests = Math.floor((28 - crop.growthDays - 1) / (crop.regrowInterval || 1)) + 1
    const revenue = crop.baseSellPrice * harvests
    const cost = crop.seedPrice
    return cost > 0 ? (revenue / cost).toFixed(2) : '∞'
  }
}

const handleConfirm = () => {
  if (selectedCrops.value.length === 0) {
    ElMessage.warning('请至少选择一种作物')
    return
  }
  emit('confirm', { cropIds: selectedCrops.value.map(c => c.id), mode: algorithmMode.value })
}

// 弹窗打开时默认全选
watch(() => props.visible, (newVal) => {
  if (newVal) {
    // 默认全选 + 默认模式
    selectedCrops.value = [...props.crops]
    algorithmMode.value = 'max_roi'
    // 等表格渲染后触发全选状态同步
    nextTick(() => {
      if (tableRef.value) {
        // 先清除再全选，确保状态一致
        tableRef.value.clearSelection()
        nextTick(() => {
          props.crops.forEach(row => {
            tableRef.value?.toggleRowSelection(row, true)
          })
        })
      }
    })
  } else {
    selectedCrops.value = []
  }
})
</script>

<style scoped>
.crop-selection {
  padding: 0 8px;
}

.mode-selector {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.mode-label {
  font-size: 14px;
  color: #606266;
  margin-right: 12px;
  white-space: nowrap;
}

.selection-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.selection-count {
  font-size: 13px;
  color: #909399;
}

.roi-value {
  color: #67c23a;
  font-weight: 500;
}
</style>
