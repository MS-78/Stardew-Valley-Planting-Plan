<script setup>
import { ref, computed, onMounted, reactive, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '../stores/appStore'
import { getCrops } from '../api/cropApi'
import { getTools } from '../api/toolApi'
import { getCategories } from '../api/categoryApi'
import { initPlanning, autoGenerate, getStats } from '../api/planningApi'
import GridCanvas from '../components/GridCanvas.vue'
import SidePanel from '../components/SidePanel.vue'
import StatsPanel from '../components/StatsPanel.vue'
import ConfigDialog from '../components/ConfigDialog.vue'
import AutoPlanDialog from '../components/AutoPlanDialog.vue'
import { checkAllConstraints, hasPlacedItems } from '../utils/constraintChecker'
import { calculateCropRevenue, calculateROI } from '../utils/cropCalculator'

const store = useAppStore()

const showConfig = ref(false)
const showAutoPlan = ref(false)
const loading = ref(false)
const planningActive = ref(false)
const cellDetail = ref(null)
const autoGenLoading = ref(false)

// 撤销功能：保存自动规划前的画布快照
const preAutoGenGrid = ref(null)
const canUndo = ref(false)

// 工具覆盖范围可视化开关
const showCoverage = ref(false)

const crops = ref([])
const tools = ref([])
const categories = ref([])

// UX-3: computed — 画布是否为空（用于显示操作引导）
const canvasEmpty = computed(() => !hasPlacedItems(store.grid))

// UX-4: computed — 预算是否超支
const budgetExceeded = computed(() => store.stats.budgetRemaining < 0)

// Load data on mount
onMounted(async () => {
  try {
    const [cropRes, toolRes, catRes] = await Promise.all([
      getCrops(),
      getTools(),
      getCategories()
    ])
    crops.value = cropRes.data || []
    tools.value = toolRes.data || []
    categories.value = catRes.data || []
    store.crops = crops.value
    store.tools = tools.value
    store.categories = categories.value
  } catch (e) {
    console.error('加载初始数据失败:', e)
  }
})

// Current season crops for auto-plan dialog
const seasonCrops = computed(() => {
  return crops.value.filter(c => c.seasons && c.seasons.includes(store.season))
})

// Filtered categories: only current season crop categories + tool categories
const filteredCategories = computed(() => {
  return categories.value.filter(c => {
    if (c.type === 'tool') return true
    if (c.type === 'crop') return c.season === store.season
    return false
  })
})

// Crop lookup map by name (for revenue calculation from drag-dropped cells)
const cropMapByName = computed(() => {
  const map = {}
  for (const c of crops.value) {
    map[c.name] = c
  }
  return map
})

// Crop lookup map by ID
const cropMapById = computed(() => {
  const map = {}
  for (const c of crops.value) {
    map[c.id] = c
  }
  return map
})

function handleStartPlanning() {
  if (planningActive.value && hasPlacedItems(store.grid)) {
    ElMessageBox.confirm(
      '当前规划已有内容，重新规划将清空画布，是否继续？',
      '确认重新规划',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      store.resetPlanning()
      planningActive.value = false
      showConfig.value = true
    }).catch(() => {
      // User cancelled
    })
  } else {
    if (planningActive.value) {
      store.resetPlanning()
      planningActive.value = false
    }
    showConfig.value = true
  }
}

async function handleConfigConfirm(config) {
  loading.value = true
  try {
    store.season = config.season
    store.width = config.width
    store.height = config.height
    store.budget = config.budget

    const res = await initPlanning(config)
    // Backend ApiResponse: { code, message, data } where data = {planningId, season, width, height, budget}
    store.planningId = res.data?.planningId || res.planningId || res.data

    // Initialize empty grid
    store.grid = Array.from({ length: config.height }, () =>
      Array.from({ length: config.width }, () => ({ type: 'empty' }))
    )

    planningActive.value = true
    showConfig.value = false
    ElMessage.success('规划已创建，开始拖拽放置或点击「一键生成」')
  } catch (e) {
    ElMessage.error('创建规划失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

function handlePlaceItem({ row, col, item }) {
  if (!planningActive.value) return
  if (row < 0 || row >= store.height || col < 0 || col >= store.width) return

  // 确定工具type：拖拽工具时统一用 sprinkler/scarecrow
  let cellType = item.type
  if (cellType === 'tool') {
    const name = (item.name || '').toLowerCase()
    if (name.includes('稻草') || name.includes('scarecrow') || item.id?.includes('scarecrow')) {
      cellType = 'scarecrow'
    } else {
      cellType = 'sprinkler'
    }
  }

  const newGrid = store.grid.map(r => [...r])
  newGrid[row][col] = {
    type: cellType,
    itemId: item.id,
    name: item.name,
    walkable: item.data?.isWalkable ?? true,
    data: item.data,
    source: 'user'  // 标记为用户放置
  }
  store.grid = newGrid
  updateStats()
}

function handleRemoveItem({ row, col }) {
  if (!planningActive.value) return
  const cell = store.grid[row][col]
  const cellName = cell?.name || ''
  const newGrid = store.grid.map(r => [...r])
  newGrid[row][col] = { type: 'empty' }
  store.grid = newGrid
  updateStats()
  if (cellName) {
    ElMessage({ message: `已移除 ${cellName}`, type: 'info', duration: 1500, offset: 80 })
  }
}

function handleExpandGrid({ direction, position }) {
  if (!planningActive.value) return

  const newGrid = store.grid.map(r => [...r])

  if (direction === 'row') {
    const newRow = Array.from({ length: store.width }, () => ({ type: 'empty' }))
    newGrid.splice(position, 0, newRow)
    store.height = newGrid.length
  } else if (direction === 'column') {
    for (const row of newGrid) {
      row.splice(position, 0, { type: 'empty' })
    }
    store.width = newGrid[0].length
  }

  store.grid = newGrid
  updateStats()
}

function handleDeleteRow({ index }) {
  if (!planningActive.value) return
  if (store.height <= 1) {
    ElMessage.warning('至少保留1行')
    return
  }

  // 检查该行是否有内容
  const rowHasContent = store.grid[index].some(c => c && c.type !== 'empty')
  const doDelete = () => {
    const newGrid = store.grid.filter((_, i) => i !== index)
    store.grid = newGrid
    store.height = newGrid.length
    updateStats()
    ElMessage({ message: `已删除第 ${index + 1} 行`, type: 'info', duration: 1500, offset: 80 })
  }

  if (rowHasContent) {
    ElMessageBox.confirm(
      `第 ${index + 1} 行包含已放置的组件，删除后内容将丢失，是否继续？`,
      '确认删除行',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    ).then(doDelete).catch(() => {})
  } else {
    doDelete()
  }
}

function handleDeleteColumn({ index }) {
  if (!planningActive.value) return
  if (store.width <= 1) {
    ElMessage.warning('至少保留1列')
    return
  }

  // 检查该列是否有内容
  const colHasContent = store.grid.some(row => row[index] && row[index].type !== 'empty')
  const doDelete = () => {
    const newGrid = store.grid.map(r => {
      const newRow = [...r]
      newRow.splice(index, 1)
      return newRow
    })
    store.grid = newGrid
    store.width = newGrid[0].length
    updateStats()
    ElMessage({ message: `已删除第 ${index + 1} 列`, type: 'info', duration: 1500, offset: 80 })
  }

  if (colHasContent) {
    ElMessageBox.confirm(
      `第 ${index + 1} 列包含已放置的组件，删除后内容将丢失，是否继续？`,
      '确认删除列',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    ).then(doDelete).catch(() => {})
  } else {
    doDelete()
  }
}

function handleMoveItem({ fromRow, fromCol, toRow, toCol }) {
  if (!planningActive.value) return
  if (toRow < 0 || toRow >= store.height || toCol < 0 || toCol >= store.width) return

  const newGrid = store.grid.map(r => [...r])
  const sourceCell = newGrid[fromRow][fromCol]

  if (!sourceCell || sourceCell.type === 'empty') return

  // 移动：源格子内容复制到目标，源格子清空
  newGrid[toRow][toCol] = { ...sourceCell }
  newGrid[fromRow][fromCol] = { type: 'empty' }

  store.grid = newGrid
  updateStats()
  ElMessage({
    message: `已移动 ${sourceCell.name || '组件'}`,
    type: 'info',
    duration: 1200,
    offset: 80
  })
}

function handleCellClick(row, col) {
  const cell = store.grid[row][col]
  if (!cell || cell.type === 'empty') {
    cellDetail.value = null
    return
  }

  if (cell.type === 'crop') {
    const cropData = cell.data || cropMapById.value[cell.itemId] || cropMapByName.value[cell.name]
    if (cropData) {
      const revenue = calculateCropRevenue(cropData)
      cellDetail.value = {
        type: 'crop',
        name: cell.name,
        seedPrice: cropData.seedPrice,
        baseSellPrice: cropData.baseSellPrice,
        growthDays: cropData.growthDays,
        canRegrow: cropData.canRegrow,
        isWalkable: cropData.isWalkable ?? cell.walkable,
        harvests: revenue?.harvestCount || 0,
        totalRevenue: revenue?.totalRevenue || 0,
        totalCost: revenue?.totalCost || 0,
        roi: revenue?.roi || 0
      }
    } else {
      cellDetail.value = { type: 'crop', name: cell.name || '未知作物' }
    }
  } else if (cell.type === 'tool' || cell.type === 'sprinkler' || cell.type === 'scarecrow') {
    const toolName = cell.name || (cell.type === 'scarecrow' ? '稻草人' : '喷水器')
    cellDetail.value = {
      type: 'tool',
      name: toolName,
      toolType: cell.type === 'scarecrow' ? '稻草人' : '喷水器',
      coverage: cell.type === 'scarecrow' ? '13×13 区域' : '十字4格'
    }
  }
}

function updateStats() {
  const cropCounts = {}
  const toolCounts = {}
  let totalCost = 0
  let totalRevenue = 0
  let cropCellCount = 0
  let totalHealthRecovery = 0
  let totalEnergyRecovery = 0

  for (const row of store.grid) {
    for (const cell of row) {
      if (cell.type === 'crop') {
        const name = cell.name || 'unknown'
        cropCounts[name] = (cropCounts[name] || 0) + 1
        cropCellCount++
        // 计算收益：优先使用cell中携带的作物数据，否则从cropMap查找
        const cropData = cell.data || cropMapById.value[cell.itemId] || cropMapByName.value[cell.name]
        if (cropData && cropData.growthDays && cropData.baseSellPrice != null) {
          const revenue = calculateCropRevenue(cropData)
          if (revenue) {
            totalCost += revenue.totalCost
            totalRevenue += revenue.totalRevenue
            // 累加生命回复和能量回复（单次值 × 收获次数）
            const harvestCount = revenue.harvestCount || 0
            totalHealthRecovery += (cropData.baseHealth || 0) * harvestCount
            totalEnergyRecovery += (cropData.baseEnergy || 0) * harvestCount
          }
        }
      } else if (cell.type === 'tool' || cell.type === 'sprinkler' || cell.type === 'scarecrow') {
        const name = cell.name || (cell.type === 'sprinkler' ? '喷水器' : '稻草人')
        toolCounts[name] = (toolCounts[name] || 0) + 1
      }
    }
  }

  const roi = calculateROI(totalRevenue, totalCost)

  // 计算土地利用率（仅统计作物，不统计工具）
  const totalCells = store.width * store.height
  const landUtilization = totalCells > 0 ? (cropCellCount / totalCells) * 100 : 0

  // 前端本地约束检查（防抖 300ms，避免频繁触发 5x O(n²) 检查）
  store.stats = {
    cropCounts,
    toolCounts,
    totalCost,
    totalRevenue,
    roi,
    budgetRemaining: store.budget - totalCost,
    constraintCheck: store.stats.constraintCheck || null,
    landUtilization,
    totalHealthRecovery,
    totalEnergyRecovery
  }

  scheduleConstraintCheck()
}

// 防抖约束检查
let constraintTimer = null
function scheduleConstraintCheck() {
  if (constraintTimer) clearTimeout(constraintTimer)
  constraintTimer = setTimeout(() => {
    if (hasPlacedItems(store.grid)) {
      store.stats.constraintCheck = checkAllConstraints(store.grid, store.height, store.width)
    } else {
      store.stats.constraintCheck = null
    }
    constraintTimer = null
  }, 300)
}

onBeforeUnmount(() => {
  if (constraintTimer) clearTimeout(constraintTimer)
})

function handleAutoGenerate() {
  if (!planningActive.value) {
    ElMessage.warning('请先创建规划')
    return
  }
  showAutoPlan.value = true
}

async function handleAutoPlanConfirm({ cropIds, mode }) {
  autoGenLoading.value = true

  // 保存当前画布快照（用于撤销）
  preAutoGenGrid.value = JSON.parse(JSON.stringify(store.grid))
  canUndo.value = true

  const payload = {
    cropIds,
    mode,
    existingGrid: store.grid
  }

  try {
    let res
    try {
      res = await autoGenerate(store.planningId, payload)
    } catch (e) {
      // 规划上下文丢失（后端重启），自动重新初始化后重试
      const errMsg = e.response?.data?.message || e.message || ''
      if (errMsg.includes('not found') || errMsg.includes('Planning context')) {
        console.warn('[AutoGen] 规划上下文丢失，重新初始化...')
        const initRes = await initPlanning({
          season: store.season,
          width: store.width,
          height: store.height,
          budget: store.budget
        })
        store.planningId = initRes.data?.planningId || initRes.planningId || initRes.data
        res = await autoGenerate(store.planningId, payload)
      } else {
        throw e
      }
    }

    const resultGrid = res.data?.grid || res.grid
    if (resultGrid) {
      store.grid = resultGrid
      // 用前端重新计算统计（含土地利用率、生命/能量回复等后端不包含的字段）
      updateStats()
      showAutoPlan.value = false
      cellDetail.value = null
      ElMessage.success('自动规划完成!')
    } else {
      ElMessage.error('自动规划返回数据异常')
      preAutoGenGrid.value = null
      canUndo.value = false
    }
  } catch (e) {
    ElMessage.error('自动规划失败: ' + (e.response?.data?.message || e.message))
    preAutoGenGrid.value = null
    canUndo.value = false
  } finally {
    autoGenLoading.value = false
  }
}

function handleUndoAutoGenerate() {
  if (!preAutoGenGrid.value) return
  store.grid = preAutoGenGrid.value
  preAutoGenGrid.value = null
  canUndo.value = false
  updateStats()
  ElMessage.success('已撤销自动规划')
}
</script>

<template>
  <div class="planning-view">
    <div class="planning-header">
      <h3>🌾 种植规划
        <el-tag v-if="planningActive" type="success" size="small">
          {{ { spring: '春季', summer: '夏季', fall: '秋季' }[store.season] }}
          {{ store.width }}×{{ store.height }}
        </el-tag>
      </h3>
      <div class="header-actions">
        <el-button
          v-if="canUndo"
          type="warning"
          size="small"
          @click="handleUndoAutoGenerate"
        >
          ↩ 撤销自动规划
        </el-button>
        <el-button type="success" @click="handleAutoGenerate" :disabled="!planningActive">
          ⚡ 一键生成
        </el-button>
        <el-button type="primary" @click="handleStartPlanning">
          {{ planningActive ? '重新规划' : '开始新规划' }}
        </el-button>
      </div>
    </div>

    <div class="planning-body" v-if="planningActive">
      <SidePanel
        :categories="filteredCategories"
        :crops="crops.filter(c => c.seasons && c.seasons.includes(store.season))"
        :tools="tools"
      />
      <div class="canvas-area">
        <!-- UX-8: 颜色图例 -->
        <div class="canvas-legend">
          <span><i class="legend-dot" style="background:#7cb342"></i>可踩踏作物</span>
          <span><i class="legend-dot" style="background:#8b6914"></i>棚架作物</span>
          <span><i class="legend-dot" style="background:#42a5f5"></i>喷水器</span>
          <span><i class="legend-dot" style="background:#ffa726"></i>稻草人</span>
          <span><i class="legend-dot" style="background:#8B5E3C"></i>空地</span>
          <span class="coverage-toggle">
            <el-switch
              v-model="showCoverage"
              active-text="显示覆盖范围"
              size="small"
            />
          </span>
        </div>

        <!-- UX-6: 格子点击详情条 -->
        <div v-if="cellDetail" class="cell-detail-bar" @click="cellDetail = null">
          <span class="detail-close">✕</span>
          <span class="detail-label">{{ cellDetail.type === 'crop' ? '🌱' : '🔧' }} {{ cellDetail.name }}</span>
          <template v-if="cellDetail.type === 'crop' && cellDetail.seedPrice != null">
            <span class="detail-sep">|</span>
            <span>种子: {{ cellDetail.seedPrice }}G</span>
            <span class="detail-sep">|</span>
            <span>售价: {{ cellDetail.baseSellPrice }}G</span>
            <span class="detail-sep">|</span>
            <span>生长: {{ cellDetail.growthDays }}天</span>
            <span class="detail-sep">|</span>
            <span>{{ cellDetail.canRegrow ? '可重复' : '一次性' }}</span>
            <span class="detail-sep">|</span>
            <span>{{ cellDetail.isWalkable ? '可踩踏' : '棚架' }}</span>
            <span class="detail-sep">|</span>
            <span>收获{{ cellDetail.harvests }}次 ROI: {{ isFinite(cellDetail.roi) ? cellDetail.roi.toFixed(2) : '∞' }}</span>
          </template>
          <template v-else-if="cellDetail.type === 'tool'">
            <span class="detail-sep">|</span>
            <span>{{ cellDetail.toolType }}</span>
            <span class="detail-sep">|</span>
            <span>覆盖: {{ cellDetail.coverage }}</span>
          </template>
        </div>

        <!-- UX-3: 操作引导（仅空画布时显示） -->
        <div v-if="canvasEmpty" class="canvas-guide">
          💡 从左侧拖拽作物/工具到画布 | 右键点击移除 | 点击「一键生成」自动规划
        </div>

        <!-- UX-5: 自动规划加载遮罩 -->
        <div v-if="autoGenLoading" class="canvas-loading-overlay">
          <div class="loading-content">
            <el-icon class="is-loading" :size="36"><Loading /></el-icon>
            <p>正在规划中...</p>
          </div>
        </div>

        <GridCanvas
          :grid="store.grid"
          :width="store.width"
          :height="store.height"
          :show-coverage="showCoverage"
          @place-item="handlePlaceItem"
          @remove-item="handleRemoveItem"
          @cell-click="handleCellClick"
          @move-item="handleMoveItem"
          @expand-grid="handleExpandGrid"
          @delete-row="handleDeleteRow"
          @delete-column="handleDeleteColumn"
        />
      </div>
    </div>
    <div class="planning-placeholder" v-else>
      <el-empty description="点击「开始新规划」配置地块参数，进入种植规划界面" />
    </div>

    <!-- UX-4: 预算超支警告横幅 -->
    <div v-if="planningActive && budgetExceeded" class="budget-exceeded-banner">
      ⚠️ 预算超支！当前投入已超过预算 {{ store.budget.toLocaleString() }}G，请减少种植或增加预算
    </div>

    <StatsPanel :stats="store.stats" v-if="planningActive" />

    <ConfigDialog
      v-model:visible="showConfig"
      :loading="loading"
      @confirm="handleConfigConfirm"
    />

    <AutoPlanDialog
      v-model:visible="showAutoPlan"
      :crops="seasonCrops"
      :loading="loading"
      @confirm="handleAutoPlanConfirm"
    />
  </div>
</template>

<style scoped>
.planning-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.planning-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}

.planning-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.planning-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-area {
  flex: 1;
  overflow: auto;
  background-color: #fafafa;
  position: relative;
  display: flex;
  flex-direction: column;
}

.planning-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* UX-8: 颜色图例 */
.canvas-legend {
  display: flex;
  gap: 12px;
  padding: 6px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
  color: #606266;
  flex-wrap: wrap;
}
.canvas-legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  border: 1px solid rgba(0,0,0,0.1);
}

.coverage-toggle {
  margin-left: auto;
}

/* UX-6: 格子详情条 */
.cell-detail-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: #ecf5ff;
  border-bottom: 1px solid #b3d8ff;
  font-size: 12px;
  color: #303133;
  cursor: pointer;
  flex-wrap: wrap;
}
.cell-detail-bar:hover {
  background: #d9ecff;
}
.detail-close {
  color: #909399;
  font-size: 14px;
  margin-right: 4px;
}
.detail-label {
  font-weight: 600;
}
.detail-sep {
  color: #c0c4cc;
}

/* UX-3: 操作引导 */
.canvas-guide {
  padding: 8px 16px;
  background: #fdf6ec;
  border-bottom: 1px solid #faecd8;
  font-size: 13px;
  color: #e6a23c;
  text-align: center;
}

/* UX-5: 加载遮罩 */
.canvas-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.loading-content {
  text-align: center;
  color: #409eff;
}
.loading-content p {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 500;
}

/* UX-4: 预算超支横幅 */
.budget-exceeded-banner {
  padding: 6px 16px;
  background: #fef0f0;
  border-bottom: 1px solid #fde2e2;
  color: #f56c6c;
  font-size: 13px;
  font-weight: 500;
  text-align: center;
}
</style>
