/**
 * 前端本地约束检查器
 * PRD 4.1.4: 约束检查计算位置 — 前端本地实时计算（无需调用后端API）
 * 每次手动拖拽/删除/一键生成后立即重新计算
 */

/**
 * H2 可达性检查 — BFS从地图边缘四方向遍历
 * 可通行格：empty(无内容) 和 walkable=true 的作物/工具
 * 不可通行格：walkable=false 的作物(棚架)、工具(喷水器/稻草人阻挡行走)
 *
 * 新规则（v3.2）：所有作物统一判断"至少有一个相邻格子可达"
 * - 可踩踏作物（walkable=true）：BFS 可以穿过，可达性检查看相邻格
 * - 不可踩踏作物（walkable=false）：BFS 不能穿过，可达性检查看相邻格
 *
 * @param {Array<Array>} grid - 二维网格
 * @param {number} height - 地块高度
 * @param {number} width - 地块宽度
 * @returns {{ count: number, cells: Array<{row: number, col: number, name: string}> }} 不可达作物数量及坐标列表
 */
export function checkReachability(grid, height, width) {
  if (!grid || grid.length === 0) return { count: 0, cells: [] }

  const visited = Array.from({ length: height }, () => Array(width).fill(false))
  const queue = []

  // 从地图边缘所有可通行格子开始BFS
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if ((y === 0 || y === height - 1 || x === 0 || x === width - 1) && isPassable(grid[y][x])) {
        queue.push([y, x])
        visited[y][x] = true
      }
    }
  }

  // 四方向BFS（用索引指针代替 shift()，避免 O(n²)）
  const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]]
  let head = 0
  while (head < queue.length) {
    const [cy, cx] = queue[head++]
    for (const [dy, dx] of dirs) {
      const ny = cy + dy
      const nx = cx + dx
      if (ny >= 0 && ny < height && nx >= 0 && nx < width && !visited[ny][nx] && isPassable(grid[ny][nx])) {
        visited[ny][nx] = true
        queue.push([ny, nx])
      }
    }
  }

  // 统计不可达的作物 — 所有作物统一检查"至少有一个相邻格子可达"
  const unreachableCells = []
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (cell && cell.type === 'crop') {
        // 检查是否至少有一个相邻格子可达
        let hasReachableNeighbor = false
        for (const [dy, dx] of dirs) {
          const ny = y + dy
          const nx = x + dx
          if (ny >= 0 && ny < height && nx >= 0 && nx < width && visited[ny][nx]) {
            hasReachableNeighbor = true
            break
          }
        }
        if (!hasReachableNeighbor) {
          unreachableCells.push({ row: y, col: x, name: cell.name || '未知作物' })
        }
      }
    }
  }

  return { count: unreachableCells.length, cells: unreachableCells }
}

/**
 * 判断格子是否可通行（BFS可通过）
 * 可通行：empty 或 walkable=true 的作物
 */
function isPassable(cell) {
  if (!cell || cell.type === 'empty') return true
  if (cell.type === 'crop' && cell.walkable) return true
  return false
}

/**
 * H3 喷水器覆盖检查
 * 喷水器覆盖范围：上下左右各1格（十字形，共4格，不含自身）
 *
 * @param {Array<Array>} grid - 二维网格
 * @param {number} height - 地块高度
 * @param {number} width - 地块宽度
 * @returns {{ count: number, cells: Array<{row: number, col: number, name: string}> }} 未被喷水器覆盖的作物数量及坐标列表
 */
export function checkSprinklerCoverage(grid, height, width) {
  if (!grid || grid.length === 0) return { count: 0, cells: [] }

  // 喷水器偏移：上下左右各1格
  const offsets = [[-1, 0], [1, 0], [0, -1], [0, 1]]

  // 找到所有喷水器位置
  const sprinklers = []
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (cell && (cell.type === 'tool' || cell.type === 'sprinkler') && isSprinkler(cell)) {
        sprinklers.push([y, x])
      }
    }
  }

  // 构建喷水器覆盖集合
  const coveredSet = new Set()
  for (const [sy, sx] of sprinklers) {
    for (const [dy, dx] of offsets) {
      const cy = sy + dy
      const cx = sx + dx
      if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
        coveredSet.add(`${cy},${cx}`)
      }
    }
  }

  // 统计未被覆盖的作物
  const uncoveredCells = []
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (cell && cell.type === 'crop') {
        if (!coveredSet.has(`${y},${x}`)) {
          uncoveredCells.push({ row: y, col: x, name: cell.name || '未知作物' })
        }
      }
    }
  }

  return { count: uncoveredCells.length, cells: uncoveredCells }
}

/**
 * H4 稻草人覆盖检查
 * 稻草人覆盖范围：以自身为中心的13×13区域（range=6）
 *
 * @param {Array<Array>} grid - 二维网格
 * @param {number} height - 地块高度
 * @param {number} width - 地块宽度
 * @returns {number} 未被稻草人覆盖的作物数量
 */
export function checkScarecrowCoverage(grid, height, width) {
  if (!grid || grid.length === 0) return 0

  const RANGE = 6

  // 找到所有稻草人位置
  const scarecrows = []
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (cell && (cell.type === 'tool' || cell.type === 'scarecrow') && isScarecrow(cell)) {
        scarecrows.push([y, x])
      }
    }
  }

  // 构建稻草人覆盖集合
  const protectedSet = new Set()
  for (const [sy, sx] of scarecrows) {
    for (let dy = -RANGE; dy <= RANGE; dy++) {
      for (let dx = -RANGE; dx <= RANGE; dx++) {
        const cy = sy + dy
        const cx = sx + dx
        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
          protectedSet.add(`${cy},${cx}`)
        }
      }
    }
  }

  // 统计未被保护的作物
  let unprotectedCount = 0
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (cell && cell.type === 'crop') {
        if (!protectedSet.has(`${y},${x}`)) {
          unprotectedCount++
        }
      }
    }
  }

  return unprotectedCount
}

/**
 * H5 喷水器覆盖区域不空置检查
 * 喷水器的上下左右4格中不应有空白格
 *
 * @param {Array<Array>} grid - 二维网格
 * @param {number} height - 地块高度
 * @param {number} width - 地块宽度
 * @returns {number} 空置格子数量
 */
export function checkSprinklerAreaNotEmpty(grid, height, width) {
  if (!grid || grid.length === 0) return 0

  const offsets = [[-1, 0], [1, 0], [0, -1], [0, 1]]
  let emptyCount = 0

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (!cell || !isSprinkler(cell)) continue

      for (const [dy, dx] of offsets) {
        const ny = y + dy
        const nx = x + dx
        if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
          const neighbor = grid[ny][nx]
          if (!neighbor || neighbor.type === 'empty') {
            emptyCount++
          }
        }
      }
    }
  }

  return emptyCount
}

/**
 * 检查同类工具覆盖是否重叠。
 * 喷水器之间、稻草人之间分别统计；喷水器与稻草人覆盖同一作物是允许的。
 */
export function checkToolCoverageOverlap(grid, height, width) {
  if (!grid || grid.length === 0) {
    return { sprinklerOverlapCells: 0, scarecrowOverlapCells: 0 }
  }

  const sprinklerMap = Array.from({ length: height }, () => Array(width).fill(0))
  const scarecrowMap = Array.from({ length: height }, () => Array(width).fill(0))
  const sprinklerOffsets = [[-1, 0], [1, 0], [0, -1], [0, 1]]

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (!cell || cell.type === 'empty') continue

      if (isSprinkler(cell)) {
        for (const [dy, dx] of sprinklerOffsets) {
          const cy = y + dy
          const cx = x + dx
          if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
            sprinklerMap[cy][cx]++
          }
        }
      }

      if (isScarecrow(cell)) {
        for (let dy = -6; dy <= 6; dy++) {
          for (let dx = -6; dx <= 6; dx++) {
            const cy = y + dy
            const cx = x + dx
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
              scarecrowMap[cy][cx]++
            }
          }
        }
      }
    }
  }

  let sprinklerOverlapCells = 0
  let scarecrowOverlapCells = 0
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (sprinklerMap[y][x] > 1) sprinklerOverlapCells++
      if (scarecrowMap[y][x] > 1) scarecrowOverlapCells++
    }
  }

  return { sprinklerOverlapCells, scarecrowOverlapCells }
}

/**
 * 综合约束检查 — 一次调用返回所有约束结果
 *
 * @param {Array<Array>} grid - 二维网格
 * @param {number} height - 地块高度
 * @param {number} width - 地块宽度
 * @returns {Object} constraintCheck 对象
 */
export function checkAllConstraints(grid, height, width) {
  const reachResult = checkReachability(grid, height, width)
  const sprinklerResult = checkSprinklerCoverage(grid, height, width)
  const unreachableCrops = reachResult.count
  const unsprayedCrops = sprinklerResult.count
  const unprotectedCrops = checkScarecrowCoverage(grid, height, width)
  const sprinklerEmptyCells = checkSprinklerAreaNotEmpty(grid, height, width)
  const { sprinklerOverlapCells, scarecrowOverlapCells } = checkToolCoverageOverlap(grid, height, width)

  const allSatisfied = unreachableCrops === 0 && unsprayedCrops === 0
    && unprotectedCrops === 0 && sprinklerEmptyCells === 0
    && sprinklerOverlapCells === 0 && scarecrowOverlapCells === 0

  const messages = []
  if (!allSatisfied) {
    if (unreachableCrops > 0) {
      messages.push(`${unreachableCrops}株作物不可达（无法从地图边缘到达）`)
    }
    if (unsprayedCrops > 0) {
      messages.push(`${unsprayedCrops}株作物未被喷水器覆盖`)
    }
    if (unprotectedCrops > 0) {
      messages.push(`${unprotectedCrops}株作物未被稻草人覆盖`)
    }
    if (sprinklerEmptyCells > 0) {
      messages.push(`${sprinklerEmptyCells}格喷水器覆盖区域空置`)
    }
    if (sprinklerOverlapCells > 0) {
      messages.push(`${sprinklerOverlapCells}格喷水器覆盖重叠`)
    }
    if (scarecrowOverlapCells > 0) {
      messages.push(`${scarecrowOverlapCells}格稻草人覆盖重叠`)
    }
  }

  return {
    allSatisfied,
    unreachableCrops,
    unsprayedCrops,
    unprotectedCrops,
    sprinklerEmptyCells,
    sprinklerOverlapCells,
    scarecrowOverlapCells,
    messages,
    details: {
      unreachableCrops: reachResult.cells,
      unsprayedCrops: sprinklerResult.cells
    }
  }
}

/**
 * 判断工具格是否为喷水器
 */
function isSprinkler(cell) {
  if (cell.type === 'sprinkler') return true
  if (cell.type === 'scarecrow') return false
  const name = (cell.name || '').toLowerCase()
  const itemId = (cell.itemId || '').toLowerCase()
  return name.includes('喷水') || name.includes('sprinkler') || itemId.includes('sprinkler')
}

/**
 * 判断工具格是否为稻草人
 */
function isScarecrow(cell) {
  if (cell.type === 'scarecrow') return true
  if (cell.type === 'sprinkler') return false
  const name = (cell.name || '').toLowerCase()
  const itemId = (cell.itemId || '').toLowerCase()
  return name.includes('稻草') || name.includes('scarecrow') || itemId.includes('scarecrow')
}

/**
 * 检查网格中是否有任何已放置的内容
 * 用于决定是否显示约束检查区域
 */
export function hasPlacedItems(grid) {
  if (!grid || grid.length === 0) return false
  for (const row of grid) {
    for (const cell of row) {
      if (cell && cell.type !== 'empty') return true
    }
  }
  return false
}
