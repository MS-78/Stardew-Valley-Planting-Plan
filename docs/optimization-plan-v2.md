# 星露谷物语种植规划系统 — 优化计划 V2

**项目**: stardew-planner
**PRD 文档**: docs/PRD.md (v3.2)
**基线版本**: v1.0（第一份开发计划已完成并验收通过）
**技术栈**: Vue.js 3 + Vite (前端) / Java 17 + Spring Boot 3 (后端) / MySQL 8 / Docker + Nginx
**访问地址**: http://localhost:9999

---

## 需求概述

基于 PRD v3.2 新增的3个重大需求，本计划与第一份开发计划独立，作为增量优化方案。

| 编号 | 需求 | PRD章节 | 影响范围 |
|------|------|---------|----------|
| OPT-1 | 自动规划增量模式 + 撤销功能 | 4.1.3 | 前端4文件 + 后端4文件 |
| OPT-2 | 硬性规则 H5：喷水器覆盖区域不得空置 | 8.1 | 前端2文件 + 后端1文件 |
| OPT-3 | 工具影响范围可视化 | 4.1.2 | 前端2文件 |
| OPT-4 | H2 可达性规则修改 | 8.1 | 前端1文件 + 后端1文件 |

---

## H2 可达性规则修改说明

**变更内容**: 用户确认修改 H2 可达性规则的定义。

**旧规则**:
- 可踩踏作物（walkable=true）: 作物格自身必须被 BFS 访问到
- 不可踩踏作物（walkable=false）: 至少有一个四方向相邻格子被 BFS 访问到

**新规则**:
- **所有作物（无论 walkable）**: 至少有一个四方向相邻格子被 BFS 访问到
- 可踩踏作物格仍然可以被 BFS 穿过（isPassable=true）
- 不可踩踏作物格 BFS 不能穿过

**影响范围**:
1. 后端 `PlanningAlgorithm.java` — BFS 可达性验证逻辑
2. 前端 `constraintChecker.js` — checkReachability 函数

**修改时机**: 在阶段2（算法改造）中一并处理，与 H5 约束一起修改。

---

## 技术架构变更总览

### 数据流变更

```
【变更前】
前端空画布 → POST auto-generate {cropIds} → 后端从零生成 → 返回完整网格

【变更后】
前端画布(含用户放置) → POST auto-generate {cropIds, existingGrid} → 后端增量生成 → 返回完整网格(含source标记)
```

### GridCell 数据结构变更

```json
// 变更前
{ "type": "crop", "itemId": "crop-s06", "name": "防风草", "walkable": true }

// 变更后
{ "type": "crop", "itemId": "crop-s06", "name": "防风草", "walkable": true, "source": "user" }
```

- `source` 取值: `"user"` | `"auto"` | null（empty格不需要source）

### 后端内部网格编码扩展

当前 `int[][] grid` 编码:
- 0=空地, 1=可踩踏作物, 2=不可踩踏作物, 3=喷水器, 4=稻草人

新增 `int[][] gridSource` 平行数组:
- 0=空地/未标记, 1=user放置, 2=auto生成

---

## OPT-1: 自动规划增量模式 + 撤销功能

### 1.1 后端修改清单

#### 1.1.1 GridCell.java — 新增 source 字段

**文件**: `server/src/main/java/com/stardew/planner/dto/GridCell.java`

**修改内容**:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GridCell {
    private String type;
    private String itemId;
    private String name;
    private boolean walkable;
    private String source;  // 新增: "user" | "auto" | null

    // 更新工厂方法
    public static GridCell empty() {
        return new GridCell("empty", null, null, true, null);
    }

    public static GridCell crop(String itemId, String name, boolean walkable, String source) {
        return new GridCell("crop", itemId, name, walkable, source);
    }

    public static GridCell tool(String itemId, String name, String source) {
        return new GridCell("tool", itemId, name, false, source);
    }
}
```

#### 1.1.2 AutoGenerateRequest.java — 新增 existingGrid 字段

**文件**: `server/src/main/java/com/stardew/planner/dto/AutoGenerateRequest.java`

**修改内容**:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoGenerateRequest {
    /** 用户选择的作物ID列表（至少1种） */
    private List<String> cropIds;

    /** 用户已有的画布布局（增量模式），为空则全新生成 */
    private List<List<GridCell>> existingGrid;
}
```

#### 1.1.3 PlanningService.java — 传递 existingGrid 到算法

**文件**: `server/src/main/java/com/stardew/planner/service/PlanningService.java`

**修改 autoGenerate() 方法** (约第109行):
```java
// 调用算法（新增 existingGrid 参数）
PlanningAlgorithm algorithm = new PlanningAlgorithm(
    context.width,
    context.height,
    context.season,
    context.budget,
    selectedCrops,
    allSeasonCrops,
    sprinkler,
    scarecrow,
    request.getExistingGrid()  // 新增：传入用户已有布局
);
```

#### 1.1.4 PlanningAlgorithm.java — 核心算法改造（最复杂）

**文件**: `server/src/main/java/com/stardew/planner/algorithm/PlanningAlgorithm.java`

**构造函数改造** — 新增 existingGrid 参数和 gridSource 数组:
```java
private int[][] gridSource;  // 新增: 0=空, 1=user, 2=auto

public PlanningAlgorithm(int width, int height, String season, int budget,
                        List<Crop> selectedCrops, List<Crop> allSeasonCrops,
                        Tool sprinkler, Tool scarecrow,
                        List<List<GridCell>> existingGrid) {
    // ... 现有初始化代码 ...
    this.gridSource = new int[height][width];

    // 如果有 existingGrid，将用户已放置的内容导入到 grid 中
    if (existingGrid != null && !existingGrid.isEmpty()) {
        importExistingGrid(existingGrid);
    }
}
```

**新增 importExistingGrid() 方法**:
```java
/**
 * 将前端传来的 existingGrid 导入到内部 grid 和 gridSource 数组
 * 用户放置的格子标记为 gridSource=1 (user)，算法不会覆盖这些格子
 */
private void importExistingGrid(List<List<GridCell>> existingGrid) {
    for (int y = 0; y < Math.min(height, existingGrid.size()); y++) {
        List<GridCell> row = existingGrid.get(y);
        for (int x = 0; x < Math.min(width, row.size()); x++) {
            GridCell cell = row.get(x);
            if (cell == null || "empty".equals(cell.getType())) continue;

            String source = cell.getSource();
            boolean isUser = "user".equals(source);

            if ("crop".equals(cell.getType())) {
                // 查找作物数据确定 walkable
                Crop crop = findCropById(cell.getItemId());
                boolean walkable = crop != null ? crop.getIsWalkable() : cell.isWalkable();
                grid[y][x] = walkable ? 1 : 2;
                gridItemId[y][x] = cell.getItemId();
                gridItemName[y][x] = cell.getName();
                gridSource[y][x] = isUser ? 1 : 2;
            } else if ("tool".equals(cell.getType()) || "sprinkler".equals(cell.getType())
                    || "scarecrow".equals(cell.getType())) {
                String name = (cell.getName() != null) ? cell.getName() : "";
                boolean isScarecrow = name.contains("稻草") || name.contains("scarecrow")
                    || "scarecrow".equals(cell.getType());
                grid[y][x] = isScarecrow ? 4 : 3;
                gridItemId[y][x] = isScarecrow ? scarecrow.getId() : sprinkler.getId();
                gridItemName[y][x] = isScarecrow ? scarecrow.getName() : sprinkler.getName();
                gridSource[y][x] = isUser ? 1 : 2;
            }
        }
    }
}
```

**新增 findCropById() 辅助方法**:
```java
private Crop findCropById(String itemId) {
    if (itemId == null) return null;
    return allSeasonCrops.stream()
        .filter(c -> itemId.equals(c.getId()))
        .findFirst().orElse(null);
}
```

**phaseA_templateLayout() 改造** — 跳过用户已放置的格子:
```java
private void phaseA_templateLayout() {
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // 如果用户已放置内容，保留不动
            if (gridSource[y][x] == 1) continue;

            int tr = y % 5;
            int tc = x % 5;
            // ... 原有5×5模板逻辑 ...
        }
    }
}
```

**phaseB_cropAllocation() 改造** — 只分配非用户放置的作物格:
```java
// 收集可用作物格时，排除用户已放置的
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        if (grid[y][x] == 1 && gridSource[y][x] != 1) {  // 排除user放置
            cropCells.add(new int[]{y, x});
        }
    }
}

// 计算已有预算消耗（用户已放置的作物也要计入成本）
int existingCost = calculateExistingCost();
int totalCost = existingCost;
```

**新增 calculateExistingCost() 方法**:
```java
private int calculateExistingCost() {
    int cost = 0;
    Map<String, Crop> cropMap = allSeasonCrops.stream()
        .collect(Collectors.toMap(Crop::getId, c -> c));
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if ((grid[y][x] == 1 || grid[y][x] == 2) && gridSource[y][x] == 1) {
                Crop crop = cropMap.get(gridItemId[y][x]);
                if (crop != null) {
                    CropRevenue rev = CropCalculator.calculate(crop);
                    if (rev != null) cost += rev.getTotalCost();
                }
            }
        }
    }
    return cost;
}
```

**phaseC/phaseD/phaseE 改造** — 不修改用户放置的格子:
- phaseC_sprinklerCoverage: 跳过 gridSource==1 的作物（已被用户覆盖）
- phaseD_scarecrowPlacement: 空地判断时保留 gridSource==1 的格子
- phaseE_connectivityValidation: 修复时不删除 gridSource==1 的作物

**buildResult() 改造** — 为每个GridCell设置source字段:
```java
// 在构建GridCell时设置source
String source = null;
if (grid[y][x] != 0) {
    source = gridSource[y][x] == 1 ? "user" : "auto";
}
cell.setSource(source);
```

### 1.2 前端修改清单

#### 1.2.1 PlanningView.vue — 传递现有画布 + 撤销功能

**文件**: `front/src/views/PlanningView.vue`

**新增状态变量**:
```javascript
// 撤销功能：保存自动规划前的画布快照
const preAutoGenGrid = ref(null)
const canUndo = ref(false)
```

**修改 handlePlaceItem()** — 标记 source: "user":
```javascript
function handlePlaceItem({ row, col, item }) {
  // ... 现有逻辑 ...
  newGrid[row][col] = {
    type: cellType,
    itemId: item.id,
    name: item.name,
    walkable: item.data?.isWalkable ?? true,
    data: item.data,
    source: 'user'  // 新增：标记为用户放置
  }
  // ...
}
```

**修改 handleAutoPlanConfirm()** — 传递 existingGrid + 保存快照:
```javascript
async function handleAutoPlanConfirm(cropIds) {
  autoGenLoading.value = true

  // 保存当前画布快照（用于撤销）
  preAutoGenGrid.value = JSON.parse(JSON.stringify(store.grid))
  canUndo.value = true

  try {
    // 将当前画布状态传递给后端（增量模式）
    const res = await autoGenerate(store.planningId, {
      cropIds,
      existingGrid: store.grid  // 新增：传递现有布局
    })
    if (res.data && res.data.grid) {
      store.grid = res.data.grid
      store.stats = res.data.stats
      showAutoPlan.value = false
      cellDetail.value = null
      ElMessage.success('自动规划完成！')
    }
  } catch (e) {
    // 失败时恢复快照
    preAutoGenGrid.value = null
    canUndo.value = false
    ElMessage.error('自动规划失败: ' + (e.response?.data?.message || e.message))
  } finally {
    autoGenLoading.value = false
  }
}
```

**新增 handleUndoAutoGenerate()**:
```javascript
function handleUndoAutoGenerate() {
  if (!preAutoGenGrid.value) return
  store.grid = preAutoGenGrid.value
  preAutoGenGrid.value = null
  canUndo.value = false
  updateStats()
  ElMessage.success('已撤销自动规划')
}
```

**模板中新增撤销按钮**（画布区域顶部，一键生成按钮旁）:
```html
<el-button
  v-if="canUndo"
  type="warning"
  size="small"
  @click="handleUndoAutoGenerate"
>
  ↩ 撤销自动规划
</el-button>
```

#### 1.2.2 planningApi.js — 无需修改

当前 `autoGenerate(planningId, data)` 已经接受任意 data 对象，新增 `existingGrid` 字段后无需修改此文件。

---

## OPT-2: 硬性规则 H5 — 喷水器覆盖区域不得空置

### 2.1 规则定义

**PRD**: 喷水器的十字覆盖范围（上下左右各1格，共4格）内，所有格子必须有作物。不允许喷水器覆盖区域存在空白的"浪费"格子。

### 2.2 后端修改

#### 2.2.1 PlanningAlgorithm.java — 新增 H5 检查 + 修复

**新增 checkSprinklerAreaNotEmpty() 方法**（在 checkConstraints() 中调用）:
```java
/**
 * H5: 检查喷水器覆盖区域内是否有空置格子
 * 喷水器的上下左右4格中，如果是空地（grid==0），则违反H5
 *
 * @return 违反H5的空置格子数量
 */
private int checkSprinklerAreaNotEmpty() {
    int emptyCount = 0;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (grid[y][x] != 3) continue; // 只检查喷水器格

            for (int[] off : sprinklerOffsets) {
                int cy = y + off[0];
                int cx = x + off[1];
                if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                    if (grid[cy][cx] == 0) {
                        emptyCount++;
                    }
                }
            }
        }
    }
    return emptyCount;
}
```

**在 checkConstraints() 中集成 H5**:
```java
private ConstraintCheck checkConstraints() {
    // ... 现有 H2/H3/H4 检查 ...

    // H5: 喷水器区域不空置
    int sprinklerEmptyCells = checkSprinklerAreaNotEmpty();

    boolean allSatisfied = (unreachableCrops == 0 && unsprayedCrops == 0
        && unprotectedCrops == 0 && sprinklerEmptyCells == 0);

    if (sprinklerEmptyCells > 0) {
        messages.add(String.format("%d格喷水器覆盖区域空置", sprinklerEmptyCells));
    }
    // ...
}
```

**新增 phaseF_sprinklerAreaOptimization() — 在 phaseE 之后执行**:
```java
/**
 * 阶段F: 修复喷水器覆盖区域内的空置格子
 * 策略: 在空置格放置ROI最高的作物（如果有预算），或将喷水器移到更高效的位置
 */
private void phaseF_sprinklerAreaOptimization() {
    // 遍历所有喷水器
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (grid[y][x] != 3) continue;

            for (int[] off : sprinklerOffsets) {
                int cy = y + off[0];
                int cx = x + off[1];
                if (cy >= 0 && cy < height && cx >= 0 && cx < width && grid[cy][cx] == 0) {
                    // 空地 → 尝试放置ROI最高的作物
                    placeHighROICropAt(cy, cx);
                }
            }
        }
    }
}
```

**在 generate() 方法中添加 phaseF**:
```java
// === 阶段F：喷水器区域优化（H5）===
phaseF_sprinklerAreaOptimization();
```

#### 2.2.2 ConstraintCheck.java — 新增 H5 字段

**文件**: `server/src/main/java/com/stardew/planner/dto/ConstraintCheck.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintCheck {
    private boolean allSatisfied;
    private int unsprayedCrops;       // H3
    private int unprotectedCrops;     // H4
    private int unreachableCrops;     // H2
    private int sprinklerEmptyCells;  // H5 新增
    private List<String> messages;
}
```

### 2.3 前端修改

#### 2.3.1 constraintChecker.js — 新增 H5 检查

**文件**: `front/src/utils/constraintChecker.js`

**新增 checkSprinklerAreaNotEmpty() 函数**:
```javascript
/**
 * H5 喷水器覆盖区域不空置检查
 * 喷水器十字范围内（上下左右各1格）不应有空白格
 *
 * @returns {number} 空置格子数量
 */
export function checkSprinklerAreaNotEmpty(grid, height, width) {
  if (!grid || grid.length === 0) return 0

  const offsets = [[-1, 0], [1, 0], [0, -1], [0, 1]]
  let emptyCount = 0

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = grid[y][x]
      if (!isSprinkler(cell)) continue

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
```

**修改 checkAllConstraints()** — 集成 H5:
```javascript
export function checkAllConstraints(grid, height, width) {
  const unreachableCrops = checkReachability(grid, height, width)
  const unsprayedCrops = checkSprinklerCoverage(grid, height, width)
  const unprotectedCrops = checkScarecrowCoverage(grid, height, width)
  const sprinklerEmptyCells = checkSprinklerAreaNotEmpty(grid, height, width)  // 新增

  const allSatisfied = unreachableCrops === 0 && unsprayedCrops === 0
    && unprotectedCrops === 0 && sprinklerEmptyCells === 0

  const messages = []
  if (!allSatisfied) {
    if (unreachableCrops > 0) messages.push(`${unreachableCrops}株作物不可达`)
    if (unsprayedCrops > 0) messages.push(`${unsprayedCrops}株作物未被喷水器覆盖`)
    if (unprotectedCrops > 0) messages.push(`${unprotectedCrops}株作物未被稻草人覆盖`)
    if (sprinklerEmptyCells > 0) messages.push(`${sprinklerEmptyCells}格喷水器覆盖区域空置`)  // 新增
  }

  return { allSatisfied, unreachableCrops, unsprayedCrops, unprotectedCrops, sprinklerEmptyCells, messages }
}
```

#### 2.3.2 StatsPanel.vue — 显示 H5 约束信息

**文件**: `front/src/components/StatsPanel.vue`

当前约束检查区域已使用 `constraintCheck.messages` 动态渲染，H5 消息会自动显示。无需额外修改模板。

但需确认 `props.stats` 的 default 对象包含 `sprinklerEmptyCells`:
```javascript
default: () => ({
  cropCounts: {},
  toolCounts: {},
  totalCost: 0,
  totalRevenue: 0,
  roi: 0,
  budgetRemaining: 0,
  constraintCheck: null,
  sprinklerEmptyCells: 0  // 新增
})
```

---

## OPT-3: 工具影响范围可视化

### 3.1 需求定义

用户在画布上放置喷水器或稻草人后，该工具的覆盖范围显示为**半透明有色遮罩**：
- 喷水器: 蓝色半透明遮罩覆盖十字4格
- 稻草人: 黄色半透明遮罩覆盖13×13区域

### 3.2 前端修改

#### 3.2.1 GridCanvas.vue — 新增覆盖层渲染

**文件**: `front/src/components/GridCanvas.vue`

**新增 props**:
```javascript
const props = defineProps({
  grid: { type: Array, required: true },
  width: { type: Number, required: true },
  height: { type: Number, required: true },
  showCoverage: { type: Boolean, default: false }  // 新增：是否显示覆盖范围
})
```

**新增覆盖计算函数**:
```javascript
// 计算每个格子的覆盖状态
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
```

**模板中为每个格子添加覆盖层**:
```html
<div
  v-for="(cell, colIndex) in row"
  :key="`${rowIndex}-${colIndex}`"
  class="grid-cell"
  :class="getCellClass(cell)"
  :style="{ backgroundColor: getCellColor(cell) }"
  :title="cell && cell.name ? cell.name : '空地'"
  @dragover.prevent="handleDragOver($event, rowIndex, colIndex)"
  @dragleave="handleDragLeave($event)"
  @drop="handleDrop($event, rowIndex, colIndex)"
  @click="handleCellClick(rowIndex, colIndex)"
  @contextmenu="handleRightClick($event, rowIndex, colIndex)"
>
  <!-- 覆盖范围遮罩 -->
  <div
    v-if="coverageMap"
    class="coverage-overlay"
    :class="{
      'coverage-sprinkler': coverageMap[rowIndex]?.[colIndex]?.sprinkler,
      'coverage-scarecrow': coverageMap[rowIndex]?.[colIndex]?.scarecrow
    }"
  ></div>
  <span class="cell-content">{{ getCellContent(cell) }}</span>
</div>
```

**新增CSS样式**:
```css
.grid-cell {
  position: relative;  /* 新增：为覆盖层提供定位上下文 */
  /* ... 现有样式 ... */
}

.coverage-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;  /* 不影响拖拽和点击 */
  z-index: 1;
  border-radius: 2px;
}

.coverage-sprinkler {
  background-color: rgba(66, 165, 245, 0.3);  /* 蓝色半透明 */
}

.coverage-scarecrow {
  background-color: rgba(255, 167, 38, 0.2);  /* 黄色半透明 */
}

/* 同时被喷水器和稻草人覆盖 */
.coverage-sprinkler.coverage-scarecrow {
  background: linear-gradient(135deg,
    rgba(66, 165, 245, 0.25) 50%,
    rgba(255, 167, 38, 0.15) 50%);
}
```

#### 3.2.2 PlanningView.vue — 添加覆盖显示开关

**新增状态**:
```javascript
const showCoverage = ref(false)
```

**在画布区域header添加开关**:
```html
<div class="canvas-controls">
  <el-switch
    v-model="showCoverage"
    active-text="显示覆盖范围"
    inactive-text=""
    size="small"
  />
</div>
```

**传递给 GridCanvas**:
```html
<GridCanvas
  :grid="store.grid"
  :width="store.width"
  :height="store.height"
  :show-coverage="showCoverage"
  @place-item="handlePlaceItem"
  @remove-item="handleRemoveItem"
  @cell-click="handleCellClick"
/>
```

---

## OPT-4: H2 可达性规则修改

### 4.1 规则变更说明

**原 H2 规则**: 
- 可踩踏作物（walkable=true）: 作物格自身必须被 BFS 访问到
- 不可踩踏作物（walkable=false）: 至少有一个四方向相邻格子被 BFS 访问到

**新 H2 规则**: 
- **所有作物（无论 walkable）**: 至少有一个四方向相邻格子被 BFS 访问到
- 可踩踏作物格仍然可以被 BFS 穿过（isPassable=true）
- 不可踩踏作物格 BFS 不能穿过

**设计理由**: 简化规则逻辑，统一判断标准。玩家无需走到作物格子上方，只要走到作物的上下左右任一相邻格子即可视为可达。

### 4.2 后端修改

#### 4.2.1 PlanningAlgorithm.java — 修改 BFS 可达性验证

**文件**: `server/src/main/java/com/stardew/planner/algorithm/PlanningAlgorithm.java`

**修改 checkConstraints() 中的 H2 检查逻辑**:

当前代码（约第614-621行）:
```java
// 检查可达性（H2）
boolean reachableFlag = (cellType == 1)
    ? reachable[y][x]
    : hasReachableNeighbor(y, x, reachable);
if (!reachableFlag) {
    unreachableCrops++;
}
```

修改为:
```java
// 检查可达性（H2）— 新规则：所有作物统一检查相邻格
if (!hasReachableNeighbor(y, x, reachable)) {
    unreachableCrops++;
}
```

**说明**: 移除 `cellType == 1` 的条件判断，所有作物统一使用 `hasReachableNeighbor()` 检查。可踩踏作物（cellType == 1）仍然可以被 BFS 穿过（isPassable() 返回 true），因此 BFS 会正常遍历。

### 4.3 前端修改

#### 4.3.1 constraintChecker.js — 修改 checkReachability 函数

**文件**: `front/src/utils/constraintChecker.js`

**当前逻辑**（约第48-71行）:
```javascript
// 统计不可达的作物
let unreachableCount = 0
for (let y = 0; y < height; y++) {
  for (let x = 0; x < width; x++) {
    const cell = grid[y][x]
    if (cell && cell.type === 'crop') {
      if (cell.walkable) {
        // 可踩踏作物：自身必须可达
        if (!visited[y][x]) unreachableCount++
      } else {
        // 不可踩踏(棚架)作物：相邻格子必须可达
        let hasReachableNeighbor = false
        for (const [dy, dx] of dirs) {
          const ny = y + dy
          const nx = x + dx
          if (ny >= 0 && ny < height && nx >= 0 && nx < width && visited[ny][nx]) {
            hasReachableNeighbor = true
            break
          }
        }
        if (!hasReachableNeighbor) unreachableCount++
      }
    }
  }
}
```

**修改为**:
```javascript
// 统计不可达的作物（新规则：所有作物统一检查相邻格）
let unreachableCount = 0
for (let y = 0; y < height; y++) {
  for (let x = 0; x < width; x++) {
    const cell = grid[y][x]
    if (cell && cell.type === 'crop') {
      // 新规则：所有作物统一检查相邻格是否可达
      let hasReachableNeighbor = false
      for (const [dy, dx] of dirs) {
        const ny = y + dy
        const nx = x + dx
        if (ny >= 0 && ny < height && nx >= 0 && nx < width && visited[ny][nx]) {
          hasReachableNeighbor = true
          break
        }
      }
      if (!hasReachableNeighbor) unreachableCount++
    }
  }
}
```

**说明**: 移除 `cell.walkable` 的条件判断，所有作物统一使用相邻格检查逻辑。可踩踏作物仍然可以被 BFS 穿过（isPassable() 返回 true），因此 BFS 会正常遍历。

### 4.4 修改时机

**在阶段2（算法改造）中一并处理**:
- 阶段2步骤2.8（checkConstraints() 集成 H5）时，同时修改 H2 检查逻辑
- 阶段4（H5前端约束检查）步骤4.2（修改 checkAllConstraints()）时，同时修改 H2 检查逻辑

**验证方法**:
- 手动放置一个不可踩踏作物（walkable=false），BFS 无法穿过该作物
- 确认作物有相邻格被 BFS 访问到 → 显示可达
- 确认作物无相邻格被 BFS 访问到 → 显示不可达
- 手动放置一个可踩踏作物（walkable=true），BFS 可以穿过该作物
- 确认作物有相邻格被 BFS 访问到 → 显示可达

---

## 开发阶段与步骤

### 阶段 1: 数据层改造（OPT-1 后端基础）

**目标**: 扩展 GridCell 和请求数据结构，为增量模式做准备。

| 步骤 | 文件 | 修改内容 | 验证 |
|------|------|----------|------|
| 1.1 | `dto/GridCell.java` | 新增 source 字段 + 更新工厂方法 | mvn compile |
| 1.2 | `dto/AutoGenerateRequest.java` | 新增 existingGrid 字段 | mvn compile |
| 1.3 | `dto/ConstraintCheck.java` | 新增 sprinklerEmptyCells 字段 | mvn compile |
| 1.4 | `service/PlanningService.java` | 传递 existingGrid 到算法 | mvn compile |

### 阶段 2: 算法改造（OPT-1 + OPT-2 + OPT-4 后端核心）

**目标**: PlanningAlgorithm 支持增量模式、H5 约束和 H2 规则修改。

| 步骤 | 修改内容 | 验证 |
|------|----------|------|
| 2.1 | 构造函数新增 existingGrid + gridSource 数组 | mvn compile |
| 2.2 | 新增 importExistingGrid() 方法 | 单元测试: 导入后 gridSource 正确 |
| 2.3 | phaseA 跳过 gridSource==1 的格子 | 测试: 用户放置的格子不被模板覆盖 |
| 2.4 | phaseB 排除用户作物格 + 计算已有成本 | 测试: 预算扣减正确 |
| 2.5 | phaseC/D/E 不修改用户格子 | 测试: 用户格子在约束修复中保留 |
| 2.6 | 新增 phaseF H5 喷水器区域优化 | 测试: 喷水器周围无空置地 |
| 2.7 | buildResult() 设置 source 字段 | 测试: 返回的GridCell含正确source |
| 2.8 | checkConstraints() 集成 H5 + 修改 H2 规则 | 测试: allSatisfied 包含 H5，H2 使用新规则 |

### 阶段 3: 前端增量模式 + 撤销（OPT-1 前端）

**目标**: 前端传递 existingGrid、保存快照、实现撤销。

| 步骤 | 文件 | 修改内容 | 验证 |
|------|------|----------|------|
| 3.1 | PlanningView.vue | handlePlaceItem 添加 source:"user" | 拖拽放置后格子含 source |
| 3.2 | PlanningView.vue | handleAutoPlanConfirm 传递 existingGrid | API请求含 existingGrid |
| 3.3 | PlanningView.vue | 保存 preAutoGenGrid 快照 | 自动规划前快照正确 |
| 3.4 | PlanningView.vue | handleUndoAutoGenerate 函数 | 撤销后画布恢复 |
| 3.5 | PlanningView.vue | 撤销按钮UI | 按钮可见可点击 |

### 阶段 4: H5 + H2 前端约束检查（OPT-2 + OPT-4 前端）

**目标**: 前端本地实时检查喷水器区域是否空置，并修改 H2 可达性规则。

| 步骤 | 文件 | 修改内容 | 验证 |
|------|------|----------|------|
| 4.1 | constraintChecker.js | 新增 checkSprinklerAreaNotEmpty() | 手动放置喷水器+空地 → 显示空置数 |
| 4.2 | constraintChecker.js | 修改 checkAllConstraints() 集成 H5 + 修改 H2 规则 | 约束检查面板显示 H5 信息，H2 使用新规则 |
| 4.3 | StatsPanel.vue | 确认 default props 含 sprinklerEmptyCells | 无报错 |

### 阶段 5: 工具覆盖可视化（OPT-3 前端）

**目标**: 画布显示工具覆盖范围的半透明遮罩。

| 步骤 | 文件 | 修改内容 | 验证 |
|------|------|----------|------|
| 5.1 | GridCanvas.vue | 新增 showCoverage prop | 编译通过 |
| 5.2 | GridCanvas.vue | 新增 coverageMap 计算 | 放置喷水器后周围格子标记正确 |
| 5.3 | GridCanvas.vue | 模板添加覆盖层 div | 蓝色/黄色遮罩可见 |
| 5.4 | GridCanvas.vue | CSS 覆盖层样式 | 半透明效果正确 |
| 5.5 | PlanningView.vue | 新增 showCoverage 开关 | 切换开关遮罩显示/隐藏 |

### 阶段 6: 集成测试与验收

| 测试场景 | 预期结果 |
|----------|----------|
| 全新生成（空画布） | 与v1.0行为一致，所有格子source="auto" |
| 增量生成（用户预放3株作物） | 用户作物保留(source="user")，空白区域被填充(source="auto") |
| 撤销自动规划 | 画布恢复到自动规划前的状态，用户作物保留 |
| 撤销后重新生成 | 可以再次一键生成 |
| H5约束 — 喷水器旁有空地 | 约束面板显示"N格喷水器覆盖区域空置" |
| H5约束 — 喷水器旁全部有作物 | 约束面板不显示H5警告 |
| H2新规则 — 可踩踏作物有相邻可达格 | 显示可达（无论作物格自身是否被BFS访问） |
| H2新规则 — 不可踩踏作物有相邻可达格 | 显示可达 |
| H2新规则 — 任意作物无相邻可达格 | 显示不可达 |
| 覆盖可视化开关 | 切换后喷水器周围显示蓝色遮罩，稻草人13×13显示黄色遮罩 |
| 100×100增量生成性能 | <5秒 |

---

## 依赖关系

```
阶段1 (数据层) → 阶段2 (算法改造，含H2修改)
阶段2 → 阶段3 (前端增量模式)
阶段2 → 阶段4 (H5+H2前端检查) [可与阶段3并行]
阶段3 → 阶段5 (覆盖可视化) [可与阶段4并行]
阶段3 + 阶段4 + 阶段5 → 阶段6 (集成测试)
```

## 并行建议

- 阶段3 和 阶段4 可并行（增量模式 vs H5+H2检查）
- 阶段4 和 阶段5 可并行（H5+H2检查 vs 覆盖可视化）
- 阶段1+2 是后端串行依赖链，必须先完成

## 风险点

1. **增量模式预算计算**: 用户预放置的作物成本必须正确计入总预算，否则可能导致预算超支
2. **H5修复冲突**: phaseF填充作物可能与phaseE连通性修复冲突，需要确保执行顺序
3. **覆盖层性能**: 100×100地块的coverageMap计算 + 10000个覆盖层DOM可能卡顿，建议>60×60时自动关闭覆盖显示
4. **撤销状态管理**: 仅支持1次撤销，连续两次自动规划时，第一次的快照会被覆盖

---

**文档结束**
