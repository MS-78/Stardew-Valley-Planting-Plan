# 优化计划 V2 - 详细实施指南

**状态**: ✅ 已完成并验证  
**完成时间**: 2026-06-11  
**验证方式**: Docker 构建成功，API 测试通过

---

## 概述

本指南记录了 OPT-1（增量模式）、OPT-2（H5 约束）、OPT-4（H2 规则修改）的完整实施细节。所有代码修改已完成并通过测试。

---

## 阶段 1：数据层改造

### 1.1 GridCell.java - 新增 source 字段

**文件路径**: `server/src/main/java/com/stardew/planner/dto/GridCell.java`

**修改内容**:
```java
package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网格单元格
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GridCell {
    /** 类型: "empty", "crop", "tool" */
    private String type;
    /** 作物或工具的ID，empty时为null */
    private String itemId;
    /** 显示名称，empty时为null */
    private String name;
    /** 是否可踩踏（影响BFS可达性） */
    private boolean walkable;
    
    /** 来源标记: "user"(用户手动放置) | "auto"(自动规划生成) | null(empty格不需要) */
    private String source;  // ← 新增字段

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

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 1.2 AutoGenerateRequest.java - 新增 existingGrid 字段

**文件路径**: `server/src/main/java/com/stardew/planner/dto/AutoGenerateRequest.java`

**修改内容**:
```java
package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动规划请求
 * POST /api/planning/{id}/auto-generate
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoGenerateRequest {
    /** 用户选择的作物ID列表（至少1种） */
    private List<String> cropIds;

    /** 用户已有的画布布局（增量模式），为空则全新生成 */
    private List<List<GridCell>> existingGrid;  // ← 新增字段
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 1.3 ConstraintCheck.java - 新增 H5 字段

**文件路径**: `server/src/main/java/com/stardew/planner/dto/ConstraintCheck.java`

**修改内容**:
```java
package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 约束检查结果（H1-H5硬性约束）
 * 前端本地实时计算，也由后端在auto-generate时返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintCheck {
    /** 所有约束是否均满足 */
    private boolean allSatisfied;
    /** 未被喷水器覆盖的作物数量（H3） */
    private int unsprayedCrops;
    /** 未被稻草人覆盖的作物数量（H4） */
    private int unprotectedCrops;
    /** 不可达的作物数量（H2） */
    private int unreachableCrops;
    
    /** 喷水器覆盖区域内空置的格子数量（H5） */
    private int sprinklerEmptyCells;  // ← 新增字段
    
    /** 提示信息列表（如 "3株作物未被喷水器覆盖"） */
    private List<String> messages;
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 1.4 PlanningService.java - 传递 existingGrid

**文件路径**: `server/src/main/java/com/stardew/planner/service/PlanningService.java`

**修改内容** (第 88-89 行附近):

**Before**:
```java
public PlanningResult autoGenerate(String planningId, List<String> cropIds) {
    return autoGenerate(planningId, cropIds, null);
}
```

**After**:
```java
public PlanningResult autoGenerate(String planningId, List<String> cropIds,
                                   List<List<com.stardew.planner.dto.GridCell>> existingGrid) {
    // ... 现有代码 ...
    
    // 调用算法时传递 existingGrid
    PlanningAlgorithm algorithm = new PlanningAlgorithm(
        context.width,
        context.height,
        context.season,
        context.budget,
        selectedCrops,
        allSeasonCrops,
        sprinkler,
        scarecrow,
        existingGrid  // ← 新增参数
    );
    
    // ... 现有代码 ...
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

---

## 阶段 2：算法改造

### 2.1 PlanningAlgorithm 构造函数改造

**文件路径**: `server/src/main/java/com/stardew/planner/algorithm/PlanningAlgorithm.java`

#### 2.1.1 新增 gridSource 数组声明

**位置**: 类成员变量区域（第 37 行附近）

```java
// 网格编码：0=空地, 1=可踩踏作物, 2=不可踩踏作物(棚架), 3=喷水器, 4=稻草人
private int[][] grid;
private String[][] gridItemId;
private String[][] gridItemName;

// 来源标记：0=自动/空地, 1=用户放置
private int[][] gridSource;  // ← 新增数组
```

#### 2.1.2 构造函数改造

**Before** (第 41-42 行):
```java
public PlanningAlgorithm(int width, int height, String season, int budget,
                        List<Crop> selectedCrops, List<Crop> allSeasonCrops,
                        Tool sprinkler, Tool scarecrow) {
```

**After** (第 43-68 行):
```java
/**
 * 构造函数 - 全新生成模式
 */
public PlanningAlgorithm(int width, int height, String season, int budget,
                        List<Crop> selectedCrops, List<Crop> allSeasonCrops,
                        Tool sprinkler, Tool scarecrow) {
    this(width, height, season, budget, selectedCrops, allSeasonCrops, 
         sprinkler, scarecrow, null);
}

/**
 * 构造函数 - 支持增量模式
 *
 * @param existingGrid 用户已有的画布布局，为null则全新生成
 */
public PlanningAlgorithm(int width, int height, String season, int budget,
                        List<Crop> selectedCrops, List<Crop> allSeasonCrops,
                        Tool sprinkler, Tool scarecrow,
                        List<List<GridCell>> existingGrid) {
    this.width = width;
    this.height = height;
    this.season = season;
    this.budget = budget;
    this.selectedCrops = selectedCrops;
    this.allSeasonCrops = allSeasonCrops;
    this.sprinkler = sprinkler;
    this.scarecrow = scarecrow;
    this.grid = new int[height][width];
    this.gridItemId = new String[height][width];
    this.gridItemName = new String[height][width];
    this.gridSource = new int[height][width];  // ← 新增初始化

    // 预解析工具覆盖偏移
    this.sprinklerOffsets = CoverageParser.parse(sprinkler.getCoverageOffsets());
    this.scarecrowOffsets = CoverageParser.parse(scarecrow.getCoverageOffsets());

    // 导入用户已有布局（增量模式）
    if (existingGrid != null && !existingGrid.isEmpty()) {
        importExistingGrid(existingGrid);
    }
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.2 importExistingGrid() 方法

**位置**: 第 70-102 行

**完整代码**:
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
                Crop crop = findCropById(cell.getItemId());
                boolean walkable = crop != null ? crop.getIsWalkable() : cell.isWalkable();
                grid[y][x] = walkable ? 1 : 2;
                gridItemId[y][x] = cell.getItemId();
                gridItemName[y][x] = cell.getName();
                gridSource[y][x] = isUser ? 1 : 0;
            } else if ("tool".equals(cell.getType()) || "sprinkler".equals(cell.getType())
                    || "scarecrow".equals(cell.getType())) {
                String name = (cell.getName() != null) ? cell.getName() : "";
                boolean isScarecrow = name.contains("稻草") || name.contains("scarecrow")
                        || "scarecrow".equals(cell.getType());
                grid[y][x] = isScarecrow ? 4 : 3;
                gridItemId[y][x] = isScarecrow ? scarecrow.getId() : sprinkler.getId();
                gridItemName[y][x] = isScarecrow ? scarecrow.getName() : sprinkler.getName();
                gridSource[y][x] = isUser ? 1 : 0;
            }
        }
    }
}
```

**辅助方法** (第 104-112 行):
```java
/**
 * 根据作物ID查找作物对象
 */
private Crop findCropById(String itemId) {
    if (itemId == null) return null;
    return allSeasonCrops.stream()
            .filter(c -> itemId.equals(c.getId()))
            .findFirst().orElse(null);
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.3 Phase A 改造 - 跳过用户放置的格子

**位置**: phaseA_templateLayout() 方法（第 203-229 行）

**Before**:
```java
private void phaseA_templateLayout() {
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int tr = y % 5;
            int tc = x % 5;
            // ... 模板逻辑 ...
        }
    }
}
```

**After** (第 203-229 行):
```java
private void phaseA_templateLayout() {
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // 跳过用户已放置的格子
            if (gridSource[y][x] == 1) continue;  // ← 新增跳过逻辑

            int tr = y % 5;
            int tc = x % 5;

            boolean isCropRow = (tr == 1 || tr == 3);
            boolean isSprinklerCol = (tc == 1 || tc == 3);

            if (isCropRow && isSprinklerCol) {
                grid[y][x] = 3;
                gridItemId[y][x] = sprinkler.getId();
                gridItemName[y][x] = sprinkler.getName();
            } else if (isCropRow) {
                grid[y][x] = 1;
            } else {
                grid[y][x] = 0;
            }
        }
    }
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.4 Phase B 改造 - 排除用户作物格 + 计算已有成本

**位置**: phaseB_cropAllocation() 方法（第 239-306 行）

**关键修改点**:

**修改 1** - 计算用户已放置作物的成本（第 248 行）:
```java
// 计算用户已放置作物的成本
int totalCost = calculateExistingCost();  // ← 新增
```

**新增辅助方法** (第 117-133 行):
```java
/**
 * 计算用户已放置作物的成本
 */
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

**修改 2** - 统计用户已放置的作物（第 252-258 行）:
```java
// 统计用户已放置的作物数量
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        if ((grid[y][x] == 1 || grid[y][x] == 2) && gridSource[y][x] == 1) {
            String cropId = gridItemId[y][x];
            if (cropId != null) {
                cropCounts.merge(cropId, 1, Integer::sum);
            }
        }
    }
}
```

**修改 3** - 收集可用作物格时排除用户放置的（第 261-268 行）:
```java
// 收集所有可用作物格（排除用户已放置的）
List<int[]> cropCells = new ArrayList<>();
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        if (grid[y][x] == 1 && gridSource[y][x] != 1) {  // ← 新增过滤条件
            cropCells.add(new int[]{y, x});
        }
    }
}
```

**修改 4** - H1 检查时跳过用户已放置的作物（第 273-283 行）:
```java
// 第一步：每种至少1株（H1）- 检查用户是否已放置，未放置才分配
for (CropROI cr : ranked) {
    if (cellIdx >= cropCells.size()) break;

    Crop crop = cr.getCrop();
    // 检查用户是否已放置此作物
    int userCount = cropCounts.getOrDefault(crop.getId(), 0);
    if (userCount > 0) continue;  // ← 新增：用户已放置，跳过

    if (totalCost + crop.getSeedPrice() > budget) break;

    int[] pos = cropCells.get(cellIdx++);
    placeCrop(pos[0], pos[1], crop);
    totalCost += crop.getSeedPrice();
    cropCounts.merge(crop.getId(), 1, Integer::sum);
}
```

**修改 5** - placeCrop() 方法设置 gridSource（第 311-315 行）:
```java
private void placeCrop(int y, int x, Crop crop) {
    grid[y][x] = crop.getIsWalkable() ? 1 : 2;
    gridItemId[y][x] = crop.getId();
    gridItemName[y][x] = crop.getName();
    // gridSource 保持为 0（auto），因为这是自动放置的
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.5 Phase C 改造 - 不覆盖用户放置的作物

**位置**: phaseC_sprinklerCoverage() 方法（第 323-340 行）

**修改点** - placeNearestSprinklerOnEmpty() 第三优先逻辑（第 403-427 行）:

**Before**:
```java
// 第三优先：替换一个已被覆盖的作物格为喷水器
for (int[] off : sprinklerOffsets) {
    int sy = cy - off[0];
    int sx = cx - off[1];
    if (sy >= 0 && sy < height && sx >= 0 && sx < width
        && (grid[sy][sx] == 1 || grid[sy][sx] == 2)) {
        // ... 检查覆盖数 ...
        if (coverCount > 1) {
            grid[sy][sx] = 3;
            // ...
        }
    }
}
```

**After** (第 403-427 行):
```java
// 第三优先：替换一个已被覆盖的作物格为喷水器
for (int[] off : sprinklerOffsets) {
    int sy = cy - off[0];
    int sx = cx - off[1];
    if (sy >= 0 && sy < height && sx >= 0 && sx < width
        && (grid[sy][sx] == 1 || grid[sy][sx] == 2)
        && gridSource[sy][sx] != 1) {  // ← 新增：不替换用户放置的作物
        // 检查该作物是否已被多个喷水器覆盖
        int coverCount = 0;
        for (int[] off2 : sprinklerOffsets) {
            int ssy = sy + off2[0];
            int ssx = sx + off2[1];
            if (ssy >= 0 && ssy < height && ssx >= 0 && ssx < width && grid[ssy][ssx] == 3) {
                coverCount++;
            }
        }
        if (coverCount > 1) {
            grid[sy][sx] = 3;
            gridItemId[sy][sx] = sprinkler.getId();
            gridItemName[sy][sx] = sprinkler.getName();
            gridSource[sy][sx] = 0;  // ← 标记为自动放置
            return;
        }
    }
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.6 Phase D 改造 - 不放置在用户格子上

**位置**: phaseD_scarecrowPlacement() 方法（第 440-488 行）

**修改点** - 空地判断时跳过用户格子（第 449 行）:

**Before**:
```java
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        if (grid[y][x] != 0) continue;
        // ... 计算覆盖数 ...
    }
}
```

**After** (第 447-449 行):
```java
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
        if (grid[y][x] != 0) continue;
        if (gridSource[y][x] == 1) continue;  // ← 新增：不放在用户放置的格子上
        
        // ... 计算覆盖数 ...
    }
}
```

**修改点 2** - 放置稻草人时设置 gridSource（第 472-476 行）:
```java
// 放置稻草人
grid[bestY][bestX] = 4;
gridItemId[bestY][bestX] = scarecrow.getId();
gridItemName[bestY][bestX] = scarecrow.getName();
gridSource[bestY][bestX] = 0;  // ← 标记为自动放置
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.7 Phase E 改造 - 不修改用户放置的作物

**位置**: phaseE_connectivityValidation() 方法（第 497-538 行）

**修改点** - 修复时跳过用户放置的作物（第 520-534 行）:

**Before**:
```java
// 修复策略
for (int[] pos : unreachable) {
    if (attempt == 0 && grid[pos[0]][pos[1]] == 2) {
        grid[pos[0]][pos[1]] = 1;
    } else {
        grid[pos[0]][pos[1]] = 0;
        gridItemId[pos[0]][pos[1]] = null;
        gridItemName[pos[0]][pos[1]] = null;
    }
}
```

**After** (第 520-534 行):
```java
// 修复策略（跳过用户放置的格子）
for (int[] pos : unreachable) {
    // 不修改用户放置的格子
    if (gridSource[pos[0]][pos[1]] == 1) continue;  // ← 新增跳过逻辑

    if (attempt == 0 && grid[pos[0]][pos[1]] == 2) {
        grid[pos[0]][pos[1]] = 1;
    } else {
        grid[pos[0]][pos[1]] = 0;
        gridItemId[pos[0]][pos[1]] = null;
        gridItemName[pos[0]][pos[1]] = null;
    }
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.8 Phase F - H5 喷水器区域优化

**位置**: 第 595-638 行

**完整代码**:
```java
// ============ 阶段F：H5喷水器区域优化 ============

/**
 * H5: 喷水器覆盖区域内不得空置
 * 遍历所有喷水器，检查其上下左右4格，如果是空地则填充ROI最高的作物
 */
private void phaseF_sprinklerAreaOptimization(List<Crop> viableCrops) {
    // 选择ROI最高的作物用于填充
    Crop bestCrop = null;
    double bestRoi = -1;
    for (Crop crop : viableCrops) {
        CropRevenue rev = CropCalculator.calculate(crop);
        if (rev != null && rev.getRoi() > bestRoi) {
            bestRoi = rev.getRoi();
            bestCrop = crop;
        }
    }
    if (bestCrop == null) return;

    // 计算当前总成本
    Map<String, Crop> cropMap = allSeasonCrops.stream()
            .collect(Collectors.toMap(Crop::getId, c -> c));
    int currentCost = 0;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (grid[y][x] == 1 || grid[y][x] == 2) {
                Crop crop = cropMap.get(gridItemId[y][x]);
                if (crop != null) {
                    CropRevenue rev = CropCalculator.calculate(crop);
                    if (rev != null) currentCost += rev.getTotalCost();
                }
            }
        }
    }

    // 填充喷水器周围的空地
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (grid[y][x] != 3) continue;  // 只处理喷水器

            for (int[] off : sprinklerOffsets) {
                int cy = y + off[0];
                int cx = x + off[1];
                if (cy >= 0 && cy < height && cx >= 0 && cx < width && grid[cy][cx] == 0) {
                    // 检查预算
                    if (currentCost + bestCrop.getSeedPrice() > budget) continue;

                    // 放置作物
                    grid[cy][cx] = bestCrop.getIsWalkable() ? 1 : 2;
                    gridItemId[cy][cx] = bestCrop.getId();
                    gridItemName[cy][cx] = bestCrop.getName();
                    gridSource[cy][cx] = 0;
                    currentCost += bestCrop.getSeedPrice();
                }
            }
        }
    }
}
```

**在 generate() 方法中调用** (第 178 行):
```java
// === 阶段F：H5喷水器区域优化 ===
phaseF_sprinklerAreaOptimization(viableCrops);
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.9 buildResult() 设置 source 字段

**位置**: buildResult() 方法（第 645-688 行）

**关键修改** - 为每个 GridCell 设置 source（第 654-677 行）:

```java
private PlanningResult buildResult() {
    StatsResponse stats = calculateStats();
    ConstraintCheck check = checkConstraints();
    stats.setConstraintCheck(check);

    List<List<GridCell>> resultGrid = new ArrayList<>();
    for (int y = 0; y < height; y++) {
        List<GridCell> row = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            // 确定source字段
            String source = null;
            if (grid[y][x] != 0) {
                source = gridSource[y][x] == 1 ? "user" : "auto";
            }

            GridCell cell = new GridCell();
            cell.setSource(source);  // ← 设置 source 字段
            if (grid[y][x] == 0) {
                cell.setType("empty");
                cell.setWalkable(true);
            } else if (grid[y][x] == 1 || grid[y][x] == 2) {
                cell.setType("crop");
                cell.setItemId(gridItemId[y][x]);
                cell.setName(gridItemName[y][x]);
                cell.setWalkable(grid[y][x] == 1);
            } else if (grid[y][x] == 3) {
                cell.setType("tool");
                cell.setItemId(sprinkler.getId());
                cell.setName(sprinkler.getName());
                cell.setWalkable(false);
            } else if (grid[y][x] == 4) {
                cell.setType("tool");
                cell.setItemId(scarecrow.getId());
                cell.setName(scarecrow.getName());
                cell.setWalkable(false);
            }
            row.add(cell);
        }
        resultGrid.add(row);
    }

    PlanningResult result = new PlanningResult();
    result.setPlanningId(UUID.randomUUID().toString());
    result.setGrid(resultGrid);
    result.setStats(stats);
    return result;
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

### 2.10 checkConstraints() 集成 H5

**位置**: checkConstraints() 方法（第 750-827 行）

**新增 H5 检查逻辑** (第 789-796 行):

```java
private ConstraintCheck checkConstraints() {
    boolean[][] reachable = bfsFromEdge();
    int unsprayedCrops = 0;
    int unprotectedCrops = 0;
    int unreachableCrops = 0;
    List<String> messages = new ArrayList<>();

    // ... H2, H3, H4 检查代码 ...

    // H5: 喷水器区域不空置
    int sprinklerEmptyCells = checkSprinklerAreaNotEmpty();  // ← 新增

    boolean allSatisfied = (unreachableCrops == 0 && unsprayedCrops == 0
        && unprotectedCrops == 0 && sprinklerEmptyCells == 0);  // ← 新增条件

    if (!allSatisfied) {
        // ... H2, H3, H4 消息 ...
        
        if (sprinklerEmptyCells > 0) {
            messages.add(String.format("%d格喷水器覆盖区域空置", sprinklerEmptyCells));  // ← 新增消息
        }
    }

    ConstraintCheck check = new ConstraintCheck();
    check.setAllSatisfied(allSatisfied);
    check.setUnreachableCrops(unreachableCrops);
    check.setUnsprayedCrops(unsprayedCrops);
    check.setUnprotectedCrops(unprotectedCrops);
    check.setSprinklerEmptyCells(sprinklerEmptyCells);  // ← 新增设置
    check.setMessages(messages);

    return check;
}
```

**新增辅助方法** (第 829-846 行):
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
            if (grid[y][x] != 3) continue;

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

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/server
./mvnw compile
```

---

## 阶段 3：前端修改

### 3.1 constraintChecker.js - 新增 H5 检查

**文件路径**: `front/src/utils/constraintChecker.js`

**新增函数** (插入到 checkAllConstraints() 之前):
```javascript
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
      if (!cell || cell.type !== 'sprinkler') continue

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

**修改 checkAllConstraints() 函数**:
```javascript
export function checkAllConstraints(grid, height, width) {
  const unreachableCrops = checkReachability(grid, height, width)
  const unsprayedCrops = checkSprinklerCoverage(grid, height, width)
  const unprotectedCrops = checkScarecrowCoverage(grid, height, width)
  const sprinklerEmptyCells = checkSprinklerAreaNotEmpty(grid, height, width)  // ← 新增

  const allSatisfied = unreachableCrops === 0 && unsprayedCrops === 0
    && unprotectedCrops === 0 && sprinklerEmptyCells === 0  // ← 新增条件

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
      messages.push(`${sprinklerEmptyCells}格喷水器覆盖区域空置`)  // ← 新增消息
    }
  }

  return {
    allSatisfied,
    unreachableCrops,
    unsprayedCrops,
    unprotectedCrops,
    sprinklerEmptyCells,  // ← 新增返回字段
    messages
  }
}
```

**验证方法**:
```bash
cd /e/小兵之路/Stardew Valley/front
npm run build
```

---

## 阶段 4：集成测试

### 4.1 Docker 构建和部署

```bash
cd /e/小兵之路/Stardew Valley

# 重建后端镜像
docker-compose build backend

# 重建前端镜像
docker-compose build frontend

# 重启所有容器
docker-compose up -d backend frontend
```

### 4.2 验证容器状态

```bash
docker ps --filter name=stardew
```

**预期输出**:
```
NAMES                STATUS                   PORTS
stardew-frontend     Up XX seconds            0.0.0.0:9999->9999/tcp
stardew-backend      Up XX seconds            0.0.0.0:18080->8080/tcp
stardew-mysql        Up XX seconds (healthy)  0.0.0.0:13307->3306/tcp
```

### 4.3 API 连通性测试

```bash
# 测试作物 API
curl -s http://localhost:9999/api/crops | grep -o '"id"' | wc -l
# 预期: 33
```

### 4.4 增量模式测试

```bash
# 创建测试数据
cat > /tmp/test_incremental.json << 'EOF'
{
  "cropIds": ["crop-s01", "crop-s02", "crop-s03"],
  "existingGrid": [
    [
      {"type": "crop", "itemId": "crop-s01", "name": "防风草", "walkable": true, "source": "user"},
      {"type": "empty", "walkable": true, "source": null},
      {"type": "empty", "walkable": true, "source": null}
    ],
    [
      {"type": "empty", "walkable": true, "source": null},
      {"type": "crop", "itemId": "crop-s02", "name": "花椰菜", "walkable": true, "source": "user"},
      {"type": "empty", "walkable": true, "source": null}
    ]
  ]
}
EOF

# 初始化规划
INIT_RESP=$(curl -s -X POST http://localhost:9999/api/planning/init \
  -H "Content-Type: application/json" \
  -d '{"season":"spring","width":10,"height":10,"budget":5000}')
PLANNING_ID=$(echo $INIT_RESP | grep -o '"planningId":"[^"]*"' | cut -d'"' -f4)
echo "Planning ID: $PLANNING_ID"

# 执行增量生成
AUTO_RESP=$(curl -s -X POST "http://localhost:9999/api/planning/$PLANNING_ID/auto-generate" \
  -H "Content-Type: application/json" \
  -d @/tmp/test_incremental.json)

# 验证结果
echo "All Satisfied:" && echo $AUTO_RESP | grep -o '"allSatisfied":[a-z]*' | head -1
echo "H5 Empty Cells:" && echo $AUTO_RESP | grep -o '"sprinklerEmptyCells":[0-9]*' | head -1
echo "User cells preserved:" && echo $AUTO_RESP | grep -o '"source":"user"' | wc -l
```

**预期输出**:
```
Planning ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
All Satisfied: "allSatisfied":false (小地块可能不满足所有约束)
H5 Empty Cells: "sprinklerEmptyCells":0
User cells preserved: 2
```

### 4.5 全新生成模式测试

```bash
# 初始化规划
INIT_RESP=$(curl -s -X POST http://localhost:9999/api/planning/init \
  -H "Content-Type: application/json" \
  -d '{"season":"spring","width":12,"height":12,"budget":5000}')
PLANNING_ID=$(echo $INIT_RESP | grep -o '"planningId":"[^"]*"' | cut -d'"' -f4)
echo "Planning ID: $PLANNING_ID"

# 执行全新生成（不传 existingGrid）
AUTO_RESP=$(curl -s -X POST "http://localhost:9999/api/planning/$PLANNING_ID/auto-generate" \
  -H "Content-Type: application/json" \
  -d '{"cropIds":["crop-s01","crop-s02","crop-s03"]}')

# 验证结果
echo "All Satisfied:" && echo $AUTO_RESP | grep -o '"allSatisfied":[a-z]*' | head -1
echo "H5 Empty Cells:" && echo $AUTO_RESP | grep -o '"sprinklerEmptyCells":[0-9]*' | head -1
echo "Auto cells:" && echo $AUTO_RESP | grep -o '"source":"auto"' | wc -l
```

**预期输出**:
```
Planning ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
All Satisfied: "allSatisfied":false (12x12地块可能不满足所有约束)
H5 Empty Cells: "sprinklerEmptyCells":0
Auto cells: 96 (或接近的数字)
```

---

## 完成状态

✅ **阶段 1**: 数据层改造 - 已完成  
✅ **阶段 2**: 算法改造 - 已完成  
✅ **阶段 3**: 前端修改 - 已完成  
✅ **阶段 4**: 集成测试 - 已完成  

所有修改已实施、编译通过、测试验证。

---

## 故障排查

### 编译失败

如果 `./mvnw compile` 失败:
1. 检查语法错误（特别是括号匹配）
2. 确认所有导入语句正确
3. 查看完整错误信息定位具体行号

### Docker 构建失败

如果 `docker-compose build` 失败:
1. 检查网络连接（需要下载 Maven 依赖）
2. 增加构建超时时间
3. 查看构建日志定位具体错误

### API 测试失败

如果 API 返回错误:
1. 检查容器状态: `docker ps --filter name=stardew`
2. 查看后端日志: `docker logs stardew-backend`
3. 确认数据库连接正常: `docker logs stardew-mysql`

---

**文档结束**
