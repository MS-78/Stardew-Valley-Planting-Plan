<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useAppStore } from '../stores/appStore'
import { calculateCropRevenue } from '../utils/cropCalculator'

const store = useAppStore()

const props = defineProps({
  grid: { type: Array, required: true },
  width: { type: Number, required: true },
  height: { type: Number, required: true },
  showCoverage: { type: Boolean, default: false }
})

const emit = defineEmits([
  'place-item', 'remove-item', 'cell-click', 'move-item',
  'expand-grid', 'delete-row', 'delete-column'
])

// --- 拖拽移动状态 ---
const didMove = ref(false)
const dragSourceCell = ref(null)
const hoverCell = ref(null)

// --- 作物图片 fallback ---
const failedCellImages = reactive(new Set())

// --- 悬停弹窗 ---
const tooltip = ref(null)        // { visible, data, x, y }
const tooltipTimer = ref(null)   // 延时定时器
const TOOLTIP_DELAY = 1000       // 悬停 1 秒后显示

// --- 行列高亮与右键菜单 ---
const highlightRow = ref(-1)     // 当前高亮的行 (-1=无)
const highlightCol = ref(-1)     // 当前高亮的列 (-1=无)
const contextMenu = ref(null)    // { x, y, type: 'row'|'col', index }

// --- 工具类型判断 ---
function isSprinklerCell(cell) {
  if (cell.type === 'sprinkler') return true
  if (cell.type === 'tool') {
    const name = (cell.name || '').toLowerCase()
    return name.includes('喷水') || name.includes('sprinkler')
  }
  return false
}

function isScarecrowCell(cell) {
  if (cell.type === 'scarecrow') return true
  if (cell.type === 'tool') {
    const name = (cell.name || '').toLowerCase()
    return name.includes('稻草') || name.includes('scarecrow')
  }
  return false
}

// --- 计算工具覆盖范围映射（已放置的） ---
const coverageMap = computed(() => {
  if (!props.showCoverage) return null

  const map = Array.from({ length: props.height }, () =>
    Array.from({ length: props.width }, () => ({ sprinkler: false, scarecrow: false }))
  )

  const sprinklerOffsets = [[-1, 0], [1, 0], [0, -1], [0, 1]]

  for (let y = 0; y < props.height; y++) {
    for (let x = 0; x < props.width; x++) {
      const cell = props.grid[y]?.[x]
      if (!cell) continue

      if (isSprinklerCell(cell)) {
        for (const [dy, dx] of sprinklerOffsets) {
          const ny = y + dy, nx = x + dx
          if (ny >= 0 && ny < props.height && nx >= 0 && nx < props.width) {
            map[ny][nx].sprinkler = true
          }
        }
      }

      if (isScarecrowCell(cell)) {
        for (let dy = -6; dy <= 6; dy++) {
          for (let dx = -6; dx <= 6; dx++) {
            const ny = y + dy, nx = x + dx
            if (ny >= 0 && ny < props.height && nx >= 0 && nx < props.width) {
              map[ny][nx].scarecrow = true
            }
          }
        }
      }
    }
  }

  return map
})

// --- 拖拽预览覆盖范围 ---
const previewCoverage = computed(() => {
  if (!props.showCoverage) return null
  if (!store.dragState) return null
  if (!hoverCell.value) return null

  const { row, col } = hoverCell.value
  let cellToCheck = null

  if (store.dragState.source === 'sidebar') {
    cellToCheck = { type: store.dragState.item.type, name: store.dragState.item.name }
  } else if (store.dragState.source === 'grid') {
    cellToCheck = store.dragState.cell
  }

  if (!cellToCheck) return null

  const isSprinkler = isSprinklerCell(cellToCheck)
  const isScarecrow = isScarecrowCell(cellToCheck)
  if (!isSprinkler && !isScarecrow) return null

  const cells = new Set()

  if (isSprinkler) {
    for (const [dy, dx] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
      const ny = row + dy, nx = col + dx
      if (ny >= 0 && ny < props.height && nx >= 0 && nx < props.width) cells.add(`${ny},${nx}`)
    }
  }
  if (isScarecrow) {
    for (let dy = -6; dy <= 6; dy++)
      for (let dx = -6; dx <= 6; dx++) {
        const ny = row + dy, nx = col + dx
        if (ny >= 0 && ny < props.height && nx >= 0 && nx < props.width) cells.add(`${ny},${nx}`)
      }
  }

  return { cells, type: isSprinkler ? 'sprinkler' : 'scarecrow' }
})

// --- 违规高亮映射（从 StatsPanel 约束展开联动） ---
const violationHighlightSet = computed(() => {
  if (!store.highlightedViolations || store.highlightedViolations.length === 0) return new Set()
  return new Set(store.highlightedViolations.map(v => `${v.row},${v.col}`))
})

// --- CSS Grid 样式 ---
const LABEL_SIZE = '28px'

const wrapperStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `${LABEL_SIZE} auto`,
  gridTemplateRows: `${LABEL_SIZE} auto`,
  width: 'fit-content'
}))

const colHeadersStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `repeat(${props.width}, 40px) 28px`,
  gap: '1px',
  marginLeft: '1px'
}))

const rowHeadersStyle = computed(() => ({
  display: 'grid',
  gridTemplateRows: `repeat(${props.height}, 40px) 28px`,
  gap: '1px',
  marginTop: '1px'
}))

const gridStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `repeat(${props.width}, 40px)`,
  gridTemplateRows: `repeat(${props.height}, 40px)`,
  gap: '1px',
  backgroundColor: '#c0c0c0',
  border: '2px solid #666',
  width: 'fit-content',
  boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
  contain: 'layout style'
}))

// --- 格子渲染辅助 ---
function getCellClass(cell) {
  if (!cell || cell.type === 'empty') return 'cell-empty'
  if (cell.type === 'crop') return cell.walkable ? 'cell-crop' : 'cell-crop-trellis'
  if (cell.type === 'tool' || cell.type === 'sprinkler' || cell.type === 'scarecrow') return 'cell-tool'
  return 'cell-empty'
}

function getCellColor(cell) {
  if (!cell || cell.type === 'empty') return '#8B5E3C'
  if (cell.type === 'crop') return cell.walkable ? '#7cb342' : '#8b6914'
  if (cell.type === 'scarecrow') return '#ffa726'
  if (cell.type === 'sprinkler' || cell.type === 'tool') {
    const name = (cell.name || '').toLowerCase()
    return (name.includes('稻草') || name.includes('scarecrow')) ? '#ffa726' : '#42a5f5'
  }
  return '#8B5E3C'
}

function isUserPlaced(cell) { return cell && cell.source === 'user' }
function isCellOccupied(cell) { return cell && cell.type !== 'empty' }

function getCellContent(cell) {
  if (!cell || cell.type === 'empty') return ''
  if (cell.type === 'crop') return cell.name ? cell.name.charAt(0) : '🌱'
  if (cell.type === 'scarecrow') return '🎃'
  if (cell.type === 'sprinkler' || cell.type === 'tool') {
    const name = (cell.name || '').toLowerCase()
    return (name.includes('稻草') || name.includes('scarecrow')) ? '🎃' : '💧'
  }
  return ''
}

// --- 行列高亮 ---
function getCellHighlightClass(rowIndex, colIndex) {
  if (highlightRow.value === rowIndex && highlightCol.value === colIndex) return 'cell-highlight-both'
  if (highlightRow.value === rowIndex) return 'cell-highlight-row'
  if (highlightCol.value === colIndex) return 'cell-highlight-col'
  return ''
}

// --- 右键菜单 ---
const contextMenuItems = computed(() => {
  if (!contextMenu.value) return []
  const { type, index } = contextMenu.value
  if (type === 'row') {
    return [
      { label: `⬆ 在上方插入行`, action: () => emit('expand-grid', { direction: 'row', position: index }) },
      { label: `⬇ 在下方插入行`, action: () => emit('expand-grid', { direction: 'row', position: index + 1 }) },
      { divider: true },
      { label: `🗑 删除第 ${index + 1} 行`, action: () => emit('delete-row', { index }), danger: true }
    ]
  } else {
    return [
      { label: `⬅ 在左侧插入列`, action: () => emit('expand-grid', { direction: 'column', position: index }) },
      { label: `➡ 在右侧插入列`, action: () => emit('expand-grid', { direction: 'column', position: index + 1 }) },
      { divider: true },
      { label: `🗑 删除第 ${index + 1} 列`, action: () => emit('delete-column', { index }), danger: true }
    ]
  }
})

function handleHeaderContextMenu(event, type, index) {
  event.preventDefault()
  // 获取相对于 grid-canvas 的位置
  const rect = event.currentTarget.closest('.grid-canvas').getBoundingClientRect()
  contextMenu.value = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
    type,
    index
  }
}

function handleMenuAction(action) {
  action()
  contextMenu.value = null
}

function closeContextMenu() {
  contextMenu.value = null
}

// --- 拖拽事件 ---
let lastHoverUpdate = 0
function handleDragOver(event, row, col) {
  event.preventDefault()
  // 根据拖拽来源设置 dropEffect：画布内移动用 'move'，侧栏拖入用 'copy'
  event.dataTransfer.dropEffect = store.dragState?.source === 'grid' ? 'move' : 'copy'
  event.currentTarget.style.opacity = '0.6'
  // 节流：最多每 50ms 更新一次 hover 状态，避免拖拽时频繁触发 previewCoverage 计算
  const now = performance.now()
  if (now - lastHoverUpdate > 50) {
    lastHoverUpdate = now
    hoverCell.value = { row, col }
  }
}

function handleDragLeave(event) {
  event.currentTarget.style.opacity = '1'
  const related = event.relatedTarget
  if (!event.currentTarget.contains(related)) hoverCell.value = null
}

function handleDrop(event, row, col) {
  event.preventDefault()
  event.currentTarget.style.opacity = '1'
  hoverCell.value = null

  // 优先检查画布内移动（通过共享 store.dragState 判断，比自定义 MIME 类型更可靠）
  if (store.dragState?.source === 'grid') {
    const { fromRow, fromCol } = store.dragState
    if (fromRow === row && fromCol === col) return
    didMove.value = true
    emit('move-item', { fromRow, fromCol, toRow: row, toCol: col })
    return
  }

  // 回退：从侧栏拖入放置
  try {
    const itemData = event.dataTransfer.getData('application/json') || event.dataTransfer.getData('text/plain')
    if (itemData) {
      const item = JSON.parse(itemData)
      emit('place-item', { row, col, item })
    }
  } catch (e) { console.error('解析拖拽数据失败:', e) }
}

function handleCellDragStart(event, row, col) {
  const cell = props.grid[row]?.[col]
  if (!cell || cell.type === 'empty') { event.preventDefault(); return }

  dragSourceCell.value = { row, col }
  didMove.value = false

  event.dataTransfer.setData('application/json', JSON.stringify({
    id: cell.itemId, name: cell.name, type: cell.type, data: cell.data
  }))
  event.dataTransfer.effectAllowed = 'move'

  store.dragState = { source: 'grid', cell, fromRow: row, fromCol: col }
  event.currentTarget.style.opacity = '0.3'
}

function handleCellDragEnd(event) {
  event.currentTarget.style.opacity = '1'
  dragSourceCell.value = null
  store.dragState = null
  hoverCell.value = null
}

function handleGridDragEnd() {
  hoverCell.value = null
  store.dragState = null
  dragSourceCell.value = null
}

function handleCellClick(row, col) {
  if (didMove.value) { didMove.value = false; return }
  emit('cell-click', { row, col })
}

function handleRightClick(event, row, col) {
  event.preventDefault()
  const cell = props.grid[row] && props.grid[row][col]
  if (cell && cell.type !== 'empty') emit('remove-item', { row, col })
}

// --- 悬停弹窗逻辑 ---
function handleCellMouseEnter(event, row, col) {
  const cell = props.grid[row]?.[col]
  if (!cell || cell.type === 'empty') return

  // 清除之前的定时器
  if (tooltipTimer.value) {
    clearTimeout(tooltipTimer.value)
  }

  tooltipTimer.value = setTimeout(() => {
    const rect = event.currentTarget.getBoundingClientRect()

    let tooltipData = null

    if (cell.type === 'crop') {
      const cropData = cell.data || store.crops.find(c => c.name === cell.name)
      if (cropData && cropData.growthDays) {
        const revenue = calculateCropRevenue(cropData)
        tooltipData = {
          type: 'crop',
          name: cell.name,
          image: `/crop-images/${cell.name}.png`,
          seedPrice: cropData.seedPrice,
          baseSellPrice: cropData.baseSellPrice,
          growthDays: cropData.growthDays,
          canRegrow: cropData.canRegrow,
          isWalkable: cropData.isWalkable ?? cell.walkable,
          harvestCount: revenue?.harvestCount || 0,
          roi: revenue?.roi || 0,
          baseEnergy: cropData.baseEnergy || 0,
          baseHealth: cropData.baseHealth || 0,
          totalEnergy: (cropData.baseEnergy || 0) * (revenue?.harvestCount || 0),
          totalHealth: (cropData.baseHealth || 0) * (revenue?.harvestCount || 0)
        }
      }
    } else if (cell.type === 'tool' || cell.type === 'sprinkler' || cell.type === 'scarecrow') {
      tooltipData = {
        type: 'tool',
        name: cell.name || (cell.type === 'scarecrow' ? '稻草人' : '喷水器'),
        toolType: cell.type === 'scarecrow' ? '稻草人' : '喷水器',
        coverage: cell.type === 'scarecrow' ? '13×13 区域' : '十字4格'
      }
    }

    if (tooltipData) {
      // 定位：在格子右侧显示，如果超出屏幕则左移
      let x = rect.right + 10
      let y = rect.top
      if (x + 280 > window.innerWidth) {
        x = rect.left - 290
      }
      if (y + 300 > window.innerHeight) {
        y = window.innerHeight - 310
      }

      tooltip.value = { visible: true, data: tooltipData, x, y }
    }
  }, TOOLTIP_DELAY)
}

function handleCellMouseLeave() {
  if (tooltipTimer.value) {
    clearTimeout(tooltipTimer.value)
    tooltipTimer.value = null
  }
  tooltip.value = null
}

// --- 生命周期 ---
onMounted(() => {
  document.addEventListener('click', closeContextMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeContextMenu)
  if (tooltipTimer.value) {
    clearTimeout(tooltipTimer.value)
  }
})
</script>

<template>
  <div class="grid-canvas">
    <div class="grid-body">
      <!-- 网格主体（带行列序号） -->
      <div class="grid-wrapper" :style="wrapperStyle">
        <!-- 左上角空白 -->
        <div class="grid-corner"></div>

        <!-- 列号 -->
        <div class="grid-col-headers" :style="colHeadersStyle">
          <span
            v-for="c in width" :key="'col-' + c"
            class="grid-label col-label"
            :class="{ 'header-active': highlightCol === c - 1 }"
            @mouseenter="highlightCol = c - 1"
            @mouseleave="highlightCol = -1"
            @contextmenu="handleHeaderContextMenu($event, 'col', c - 1)"
          >{{ c }}</span>
          <!-- 添加列按钮 -->
          <span
            class="grid-label header-add"
            title="添加列"
            @click="emit('expand-grid', { direction: 'column', position: width })"
          >+</span>
        </div>

        <!-- 行号 -->
        <div class="grid-row-headers" :style="rowHeadersStyle">
          <span
            v-for="r in height" :key="'row-' + r"
            class="grid-label row-label"
            :class="{ 'header-active': highlightRow === r - 1 }"
            @mouseenter="highlightRow = r - 1"
            @mouseleave="highlightRow = -1"
            @contextmenu="handleHeaderContextMenu($event, 'row', r - 1)"
          >{{ r }}</span>
          <!-- 添加行按钮 -->
          <span
            class="grid-label header-add"
            title="添加行"
            @click="emit('expand-grid', { direction: 'row', position: height })"
          >+</span>
        </div>

        <!-- 实际画布 -->
        <div class="grid-container" :style="gridStyle" @dragend="handleGridDragEnd">
          <div
            v-for="(row, rowIndex) in grid"
            :key="rowIndex"
            class="grid-row"
            style="display: contents;"
          >
            <div
              v-for="(cell, colIndex) in row"
              :key="`${rowIndex}-${colIndex}`"
              class="grid-cell"
              :class="[
                getCellClass(cell),
                { 'user-placed': isUserPlaced(cell) },
                { 'cell-violation': violationHighlightSet.has(`${rowIndex},${colIndex}`) },
                getCellHighlightClass(rowIndex, colIndex)
              ]"
              :data-row="rowIndex"
              :data-col="colIndex"
              :style="{ backgroundColor: getCellColor(cell) }"
              :draggable="isCellOccupied(cell) ? 'true' : undefined"
              :title="cell && cell.type === 'crop' ? '' : (cell && cell.name ? (isUserPlaced(cell) ? '[手动] ' : '') + cell.name : '空地')"
              @dragstart="handleCellDragStart($event, rowIndex, colIndex)"
              @dragend="handleCellDragEnd"
              @dragover.prevent="handleDragOver($event, rowIndex, colIndex)"
              @dragleave="handleDragLeave($event)"
              @drop="handleDrop($event, rowIndex, colIndex)"
              @click="handleCellClick(rowIndex, colIndex)"
              @contextmenu="handleRightClick($event, rowIndex, colIndex)"
              @mouseenter="handleCellMouseEnter($event, rowIndex, colIndex)"
              @mouseleave="handleCellMouseLeave"
            >
              <div
                v-if="coverageMap"
                class="coverage-overlay"
                :class="{
                  'coverage-sprinkler': coverageMap[rowIndex]?.[colIndex]?.sprinkler,
                  'coverage-scarecrow': coverageMap[rowIndex]?.[colIndex]?.scarecrow
                }"
              ></div>
              <div
                v-if="previewCoverage && previewCoverage.cells.has(`${rowIndex},${colIndex}`)"
                class="coverage-preview"
                :class="{
                  'preview-sprinkler': previewCoverage.type === 'sprinkler',
                  'preview-scarecrow': previewCoverage.type === 'scarecrow'
                }"
              ></div>
              <!-- 作物：显示真实图片（fallback 首字）；工具：显示 emoji -->
              <template v-if="cell && cell.type === 'crop'">
                <img
                  v-if="!failedCellImages.has(cell.name)"
                  :src="`/crop-images/${cell.name}.png`"
                  :alt="cell.name"
                  class="cell-content-img"
                  @error="failedCellImages.add(cell.name)"
                />
                <span v-else class="cell-content">{{ cell.name?.charAt(0) || '🌱' }}</span>
              </template>
              <span v-else class="cell-content">{{ getCellContent(cell) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenu"
      class="header-context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <template v-for="(item, idx) in contextMenuItems" :key="idx">
        <div v-if="item.divider" class="menu-divider"></div>
        <div
          v-else
          class="menu-item"
          :class="{ 'menu-item-danger': item.danger }"
          @click="handleMenuAction(item.action)"
        >{{ item.label }}</div>
      </template>
    </div>

    <!-- 悬停详情弹窗（Teleport 到 body 避免被容器裁剪） -->
    <Teleport to="body">
      <div
        v-if="tooltip?.visible"
        class="cell-tooltip"
        :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
      >
        <template v-if="tooltip.data.type === 'crop'">
          <div class="tooltip-header">
            <img
              :src="tooltip.data.image"
              :alt="tooltip.data.name"
              class="tooltip-image"
              @error="$event.target.style.display = 'none'"
            />
            <div class="tooltip-title">{{ tooltip.data.name }}</div>
          </div>
          <div class="tooltip-body">
            <div class="tooltip-row">
              <span class="tooltip-label">种子价格</span>
              <span class="tooltip-value">{{ tooltip.data.seedPrice }}G</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">基础售价</span>
              <span class="tooltip-value">{{ tooltip.data.baseSellPrice }}G</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">生长天数</span>
              <span class="tooltip-value">{{ tooltip.data.growthDays }}天</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">收获类型</span>
              <span class="tooltip-value">{{ tooltip.data.canRegrow ? '可重复收获' : '一次性收获' }}</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">地形类型</span>
              <span class="tooltip-value">{{ tooltip.data.isWalkable ? '可踩踏' : '棚架' }}</span>
            </div>
            <div class="tooltip-divider"></div>
            <div class="tooltip-row">
              <span class="tooltip-label">收获次数</span>
              <span class="tooltip-value tooltip-highlight">{{ tooltip.data.harvestCount }}次</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">ROI</span>
              <span class="tooltip-value tooltip-highlight">
                {{ isFinite(tooltip.data.roi) ? tooltip.data.roi.toFixed(2) : '∞' }}
              </span>
            </div>
            <div class="tooltip-divider"></div>
            <div class="tooltip-row">
              <span class="tooltip-label">单次生命回复</span>
              <span class="tooltip-value">{{ tooltip.data.baseHealth }}</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">单次能量回复</span>
              <span class="tooltip-value">{{ tooltip.data.baseEnergy }}</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">总生命回复</span>
              <span class="tooltip-value tooltip-health">{{ tooltip.data.totalHealth }}</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">总能量回复</span>
              <span class="tooltip-value tooltip-energy">{{ tooltip.data.totalEnergy }}</span>
            </div>
          </div>
        </template>
        <template v-else-if="tooltip.data.type === 'tool'">
          <div class="tooltip-header">
            <span class="tooltip-icon">🔧</span>
            <div class="tooltip-title">{{ tooltip.data.name }}</div>
          </div>
          <div class="tooltip-body">
            <div class="tooltip-row">
              <span class="tooltip-label">类型</span>
              <span class="tooltip-value">{{ tooltip.data.toolType }}</span>
            </div>
            <div class="tooltip-row">
              <span class="tooltip-label">覆盖范围</span>
              <span class="tooltip-value">{{ tooltip.data.coverage }}</span>
            </div>
          </div>
        </template>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.grid-canvas {
  overflow: auto;
  max-width: 100%;
  max-height: calc(100vh - 220px);
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  position: relative;
}

/* 自定义滚动条 */
.grid-canvas::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.grid-canvas::-webkit-scrollbar-track {
  background: #f0f0f0;
  border-radius: 4px;
}
.grid-canvas::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 4px;
}
.grid-canvas::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
.grid-canvas::-webkit-scrollbar-corner {
  background: #f0f0f0;
}

.grid-body {
  display: flex;
  align-items: flex-start;
}

/* --- 行列序号标签（类Excel） --- */
.grid-corner {
  background: #f5f5f5;
  border-bottom: 1px solid #dcdfe6;
  border-right: 1px solid #dcdfe6;
}

.grid-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #606266;
  user-select: none;
  cursor: default;
  background: #f5f5f5;
  transition: background 0.15s, color 0.15s;
}

.col-label {
  border-bottom: 1px solid #dcdfe6;
  border-right: 1px solid #ebeef5;
}

.row-label {
  border-right: 1px solid #dcdfe6;
  border-bottom: 1px solid #ebeef5;
}

.col-label:hover, .row-label:hover {
  background: #e8eaed;
  color: #303133;
}

.header-active {
  background: #d0d7de !important;
  color: #1a73e8 !important;
  font-weight: 600;
}

/* 添加行列按钮 */
.header-add {
  color: #c0c4cc;
  font-size: 16px;
  font-weight: 300;
  cursor: pointer;
  border: 1px dashed #dcdfe6;
  background: transparent;
  transition: all 0.15s;
}
.header-add:hover {
  color: #409eff;
  border-color: #409eff;
  background: #ecf5ff;
}

/* --- 行列高亮 --- */
.cell-highlight-row {
  box-shadow: inset 0 0 0 1px rgba(26, 115, 232, 0.25) !important;
  filter: brightness(1.08);
}
.cell-highlight-col {
  box-shadow: inset 0 0 0 1px rgba(26, 115, 232, 0.25) !important;
  filter: brightness(1.08);
}
.cell-highlight-both {
  box-shadow: inset 0 0 0 2px rgba(26, 115, 232, 0.35) !important;
  filter: brightness(1.12);
}

/* --- 右键菜单 --- */
.header-context-menu {
  position: absolute;
  z-index: 200;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.12);
  padding: 4px 0;
  min-width: 160px;
  font-size: 13px;
}
.menu-item {
  padding: 6px 16px;
  cursor: pointer;
  color: #303133;
  transition: background 0.1s;
  white-space: nowrap;
}
.menu-item:hover {
  background: #f5f7fa;
}
.menu-item-danger {
  color: #f56c6c;
}
.menu-item-danger:hover {
  background: #fef0f0;
  color: #f56c6c;
}
.menu-divider {
  height: 1px;
  background: #ebeef5;
  margin: 4px 0;
}

/* --- 格子样式 --- */
.grid-cell {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
  contain: layout paint;
  will-change: background-color;
  position: relative;
}

.grid-cell:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 4px rgba(0,0,0,0.3);
  z-index: 1;
}

.cell-content {
  font-size: 16px;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
  pointer-events: none;
}

/* 用户手动放置的格子 */
.user-placed {
  box-shadow: inset 0 0 0 2px #ffd700;
  z-index: 2;
}
.user-placed::after {
  content: '📌';
  position: absolute;
  top: -2px;
  right: -2px;
  font-size: 8px;
  line-height: 1;
  z-index: 3;
  pointer-events: none;
}

/* --- 工具覆盖范围遮罩 --- */
.coverage-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  z-index: 1;
  border-radius: 2px;
}
.coverage-sprinkler { background-color: rgba(66, 165, 245, 0.3); }
.coverage-scarecrow { background-color: rgba(255, 167, 38, 0.2); }
.coverage-sprinkler.coverage-scarecrow {
  background: linear-gradient(135deg, rgba(66,165,245,0.25) 50%, rgba(255,167,38,0.15) 50%);
}

/* --- 拖拽预览覆盖范围 --- */
.coverage-preview {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  z-index: 2;
  border-radius: 2px;
  border: 2px dashed;
}
.preview-sprinkler {
  background-color: rgba(66, 165, 245, 0.15);
  border-color: rgba(66, 165, 245, 0.6);
}
.preview-scarecrow {
  background-color: rgba(255, 167, 38, 0.1);
  border-color: rgba(255, 167, 38, 0.5);
}

/* --- 作物图片 --- */
.cell-content-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
  image-rendering: pixelated;
  pointer-events: none;
}

/* --- 约束违规高亮 --- */
.cell-violation {
  box-shadow: inset 0 0 0 2px #f56c6c, 0 0 8px rgba(245, 108, 108, 0.5) !important;
  animation: violation-pulse 1.2s ease-in-out infinite;
  z-index: 3;
}

@keyframes violation-pulse {
  0%, 100% { box-shadow: inset 0 0 0 2px #f56c6c, 0 0 8px rgba(245, 108, 108, 0.5) !important; }
  50% { box-shadow: inset 0 0 0 3px #f56c6c, 0 0 14px rgba(245, 108, 108, 0.7) !important; }
}

/* --- 悬停详情弹窗 --- */
.cell-tooltip {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 14px;
  min-width: 230px;
  max-width: 280px;
  font-size: 13px;
  pointer-events: none;
  animation: tooltip-fade-in 0.15s ease-out;
}

@keyframes tooltip-fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.tooltip-image {
  width: 36px;
  height: 36px;
  object-fit: contain;
  image-rendering: pixelated;
}

.tooltip-icon {
  font-size: 24px;
}

.tooltip-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.tooltip-body {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.tooltip-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tooltip-label {
  color: #909399;
  font-size: 12px;
}

.tooltip-value {
  color: #303133;
  font-weight: 500;
  font-size: 13px;
}

.tooltip-divider {
  height: 1px;
  background: #ebeef5;
  margin: 4px 0;
}

.tooltip-highlight {
  color: #67c23a;
  font-size: 14px;
  font-weight: 600;
}

.tooltip-health {
  color: #f56c6c;
  font-weight: 600;
}

.tooltip-energy {
  color: #e6a23c;
  font-weight: 600;
}
</style>
