<template>
  <div class="stats-panel">
    <div class="stats-section">
      <h4>🌱 作物统计</h4>
      <div class="stats-grid">
        <div v-for="(count, name) in stats.cropCounts" :key="name" class="stat-item">
          <span class="stat-label">{{ name }}:</span>
          <span class="stat-value">{{ count }}株</span>
        </div>
        <div v-if="!stats.cropCounts || Object.keys(stats.cropCounts).length === 0" class="stat-empty">
          暂无作物
        </div>
      </div>
    </div>

    <div class="stats-section">
      <h4>🔧 工具统计</h4>
      <div class="stats-grid">
        <div v-for="(count, name) in stats.toolCounts" :key="name" class="stat-item">
          <span class="stat-label">{{ name }}:</span>
          <span class="stat-value">{{ count }}个</span>
        </div>
        <div v-if="!stats.toolCounts || Object.keys(stats.toolCounts).length === 0" class="stat-empty">
          暂无工具
        </div>
      </div>
    </div>

    <div class="stats-section economic">
      <h4>💰 经济指标</h4>
      <div class="stats-grid">
        <div class="stat-item">
          <span class="stat-label">总投入:</span>
          <span class="stat-value">{{ (stats.totalCost || 0).toLocaleString() }}G</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">预计产出:</span>
          <span class="stat-value">{{ (stats.totalRevenue || 0).toLocaleString() }}G</span>
        </div>
        <div class="stat-item highlight">
          <span class="stat-label">ROI:</span>
          <span class="stat-value">{{ stats.roi ? (isFinite(stats.roi) ? stats.roi.toFixed(2) : '∞') : '0.00' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">预算剩余:</span>
          <span class="stat-value" :class="{ 'budget-negative': (stats.budgetRemaining || 0) < 0 }">
            {{ (stats.budgetRemaining || 0).toLocaleString() }}G
          </span>
        </div>
        <div class="stat-item" v-if="(stats.budgetRemaining || 0) < 0">
          <span class="stat-label">贷款金额:</span>
          <span class="stat-value loan-amount">
            {{ Math.abs(stats.budgetRemaining || 0).toLocaleString() }}G
          </span>
        </div>
      </div>
    </div>

    <!-- 农场效率指标（新增） -->
    <div class="stats-section efficiency">
      <h4>📊 农场效率</h4>
      <div class="stats-grid">
        <div class="stat-item">
          <span class="stat-label">土地利用率:</span>
          <span class="stat-value highlight-percent">{{ (stats.landUtilization || 0).toFixed(1) }}%</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">总生命回复:</span>
          <span class="stat-value highlight-health">{{ (stats.totalHealthRecovery || 0).toLocaleString() }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">总能量回复:</span>
          <span class="stat-value highlight-energy">{{ (stats.totalEnergyRecovery || 0).toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <!-- 约束检查（PRD v3.1 新增） -->
    <div class="stats-section constraint" v-if="stats.constraintCheck">
      <h4>📋 约束检查</h4>
      <div v-if="stats.constraintCheck.allSatisfied" class="constraint-ok">
        ✅ 所有约束已满足
      </div>
      <div v-else class="constraint-warn">
        <div class="constraint-title">⚠️ 存在未满足的约束:</div>

        <!-- 可展开的问题项（浇水器未覆盖 / 路径不可达） -->
        <el-collapse
          v-if="expandableItems.length > 0"
          v-model="activeConstraint"
          class="constraint-collapse"
          @change="handleCollapseChange"
        >
          <el-collapse-item
            v-for="item in expandableItems"
            :key="item.key"
            :name="item.key"
          >
            <template #title>
              <span class="constraint-expand-title">{{ item.label }}</span>
              <el-tag size="small" type="danger" class="constraint-count-tag">{{ item.cells.length }}</el-tag>
            </template>
            <div class="violation-cells">
              <div
                v-for="(cell, cellIdx) in item.cells"
                :key="cellIdx"
                class="violation-cell-item"
                :class="{ 'is-active': isCellHighlighted(cell) }"
                @click.stop="handleCellClick(cell)"
              >
                <span class="cell-coord">📍 行{{ cell.row + 1 }}, 列{{ cell.col + 1 }}</span>
                <span class="cell-name">{{ cell.name }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <!-- 普通约束消息（不可展开） -->
        <ul v-if="simpleMessages.length > 0" class="constraint-messages">
          <li v-for="(msg, idx) in simpleMessages" :key="idx">{{ msg }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useAppStore } from '../stores/appStore'

const store = useAppStore()

const props = defineProps({
  stats: {
    type: Object,
    required: true,
    default: () => ({
      cropCounts: {},
      toolCounts: {},
      totalCost: 0,
      totalRevenue: 0,
      roi: 0,
      budgetRemaining: 0,
      constraintCheck: null,
      landUtilization: 0,
      totalHealthRecovery: 0,
      totalEnergyRecovery: 0
    })
  }
})

// --- 约束检查展开逻辑 ---
const activeConstraint = ref([])

// 可展开的问题类型（仅浇水器未覆盖 + 路径不可达）
const expandableItems = computed(() => {
  const cc = props.stats.constraintCheck
  if (!cc || cc.allSatisfied) return []
  const items = []
  if (cc.unreachableCrops > 0 && cc.details?.unreachableCrops?.length > 0) {
    items.push({
      key: 'unreachableCrops',
      label: `${cc.unreachableCrops}株作物不可达（无法从地图边缘到达）`,
      cells: cc.details.unreachableCrops
    })
  }
  if (cc.unsprayedCrops > 0 && cc.details?.unsprayedCrops?.length > 0) {
    items.push({
      key: 'unsprayedCrops',
      label: `${cc.unsprayedCrops}株作物未被喷水器覆盖`,
      cells: cc.details.unsprayedCrops
    })
  }
  return items
})

// 不可展开的普通约束消息
const simpleMessages = computed(() => {
  const cc = props.stats.constraintCheck
  if (!cc || cc.allSatisfied) return []
  const msgs = []
  if (cc.unprotectedCrops > 0) msgs.push(`${cc.unprotectedCrops}株作物未被稻草人覆盖`)
  if (cc.sprinklerEmptyCells > 0) msgs.push(`${cc.sprinklerEmptyCells}格喷水器覆盖区域空置`)
  if (cc.sprinklerOverlapCells > 0) msgs.push(`${cc.sprinklerOverlapCells}格喷水器覆盖重叠`)
  if (cc.scarecrowOverlapCells > 0) msgs.push(`${cc.scarecrowOverlapCells}格稻草人覆盖重叠`)
  return msgs
})

function isCellHighlighted(cell) {
  return store.highlightedViolations.some(v => v.row === cell.row && v.col === cell.col)
}

// 展开/收起：高亮/清除该类问题的所有点位
function handleCollapseChange(activeNames) {
  const cells = []
  for (const name of activeNames) {
    const item = expandableItems.value.find(i => i.key === name)
    if (item) cells.push(...item.cells)
  }
  store.highlightedViolations = cells
}

// 点击单个问题点位
function handleCellClick(cell) {
  if (isCellHighlighted(cell) && store.highlightedViolations.length === 1) {
    store.highlightedViolations = []
  } else {
    store.highlightedViolations = [{ row: cell.row, col: cell.col }]
  }
  // 滚动画布到目标格子
  nextTick(() => {
    const target = document.querySelector(`.grid-cell[data-row="${cell.row}"][data-col="${cell.col}"]`)
    target?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' })
  })
}
</script>

<style scoped>
.stats-panel {
  display: flex;
  gap: 16px;
  padding: 12px 24px;
  background-color: #f5f7fa;
  border-top: 1px solid #e4e7ed;
  min-height: 100px;
  flex-wrap: wrap;
}

.stats-section {
  flex: 1;
  min-width: 180px;
  background-color: #fff;
  padding: 12px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.stats-section h4 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.stats-section.economic {
  flex: 1.5;
}

.stats-section.constraint {
  flex: 1.2;
}

.stats-section.efficiency {
  flex: 1.2;
}

.highlight-percent {
  color: #409eff !important;
  font-size: 15px;
  font-weight: 600;
}

.highlight-health {
  color: #f56c6c !important;
  font-weight: 600;
}

.highlight-energy {
  color: #e6a23c !important;
  font-weight: 600;
}

.stats-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stat-item {
  display: flex;
  gap: 4px;
  font-size: 13px;
}

.stat-label {
  color: #909399;
}

.stat-value {
  color: #303133;
  font-weight: 500;
}

.stat-item.highlight .stat-value {
  color: #67c23a;
  font-size: 15px;
}

.stat-empty {
  color: #c0c4cc;
  font-size: 13px;
}

.budget-negative {
  color: #f56c6c !important;
}

.loan-amount {
  color: #e6a23c !important;
  font-weight: 700;
  font-size: 15px;
}

/* 约束检查样式 */
.constraint-ok {
  color: #67c23a;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 4px;
}

.constraint-warn {
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 4px;
}

.constraint-title {
  color: #e6a23c;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.constraint-messages {
  margin: 0;
  padding-left: 20px;
  font-size: 12px;
  color: #e6a23c;
}

.constraint-messages li {
  margin-bottom: 2px;
}

/* 可展开约束项样式 */
.constraint-collapse {
  border: none;
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  --el-collapse-header-height: 28px;
}

.constraint-collapse :deep(.el-collapse-item__header) {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 500;
  border-bottom: 1px dashed #f5dab1;
  padding: 2px 0;
  line-height: 28px;
}

.constraint-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.constraint-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 4px;
}

.constraint-expand-title {
  margin-right: 6px;
}

.constraint-count-tag {
  transform: scale(0.85);
  margin-left: 4px;
}

.violation-cells {
  max-height: 120px;
  overflow-y: auto;
  padding: 2px 0;
}

.violation-cells::-webkit-scrollbar {
  width: 4px;
}
.violation-cells::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}

.violation-cell-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 8px;
  font-size: 11px;
  color: #606266;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
}

.violation-cell-item:hover {
  background: #fdf6ec;
  color: #e6a23c;
}

.violation-cell-item.is-active {
  background: #fef0f0;
  color: #f56c6c;
  font-weight: 600;
}

.cell-coord {
  font-family: 'Courier New', monospace;
  white-space: nowrap;
}

.cell-name {
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
