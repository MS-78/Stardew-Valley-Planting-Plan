package com.stardew.planner.algorithm;

import com.stardew.planner.dto.ConstraintCheck;
import com.stardew.planner.dto.CropRevenue;
import com.stardew.planner.dto.GridCell;
import com.stardew.planner.dto.PlanningResult;
import com.stardew.planner.dto.StatsResponse;
import com.stardew.planner.model.Crop;
import com.stardew.planner.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自动规划算法 - 5×5 平铺覆盖最优实现
 *
 * 核心设计原则：
 * 1. 以用户底图为基础，尊重所有用户已放置的工具和作物
 * 2. 喷水器基于 5×5 最小重复单元平铺，数学保证零重叠、零遗漏（密度 1/5）
 * 3. 稻草人覆盖不重叠，最小化稻草人数量
 * 4. 喷水器覆盖范围内不允许有空地
 *
 * Phase 0: 分析现有布局（清除 auto 格子，保留用户物品）
 * Phase 1: 5×5 平铺喷水器放置（选择最优偏移，边缘截断允许）
 * Phase 2: 稻草人分区放置（排除喷水器位置，覆盖作物）
 * Phase 3: 作物分配（在喷水器覆盖区域内按策略分配：max_roi/weighted_balanced/fully_balanced）
 * Phase 4: BFS连通性验证+修复（含级联清理）
 * Phase 5: 最终约束验证（含重叠审计）
 */
public class PlanningAlgorithm {
    private static final Logger log = LoggerFactory.getLogger(PlanningAlgorithm.class);

    private final int width;
    private final int height;
    private final String season;
    private final int budget;
    private final List<Crop> selectedCrops;
    private final List<Crop> allSeasonCrops;
    private final Tool sprinkler;
    private final Tool scarecrow;
    private final String mode;  // "max_roi", "weighted_balanced", "fully_balanced"

    // 网格编码：0=空地, 1=可踩踏作物, 2=不可踩踏作物(棚架), 3=喷水器, 4=稻草人
    private int[][] grid;
    private String[][] gridItemId;
    private String[][] gridItemName;
    private int[][] gridSource;  // 0=auto, 1=user

    // 覆盖地图：记录每个格子被几个喷水器/稻草人覆盖
    private int[][] sprinklerCoverage;
    private int[][] scarecrowCoverage;

    // 缓存工具覆盖偏移
    private List<int[]> sprinklerOffsets;
    private List<int[]> scarecrowOffsets;

    public PlanningAlgorithm(int width, int height, String season, int budget,
                            List<Crop> selectedCrops, List<Crop> allSeasonCrops,
                            Tool sprinkler, Tool scarecrow,
                            List<List<GridCell>> existingGrid,
                            String mode) {
        this.width = width;
        this.height = height;
        this.season = season;
        this.budget = budget;
        this.selectedCrops = selectedCrops;
        this.allSeasonCrops = allSeasonCrops;
        this.sprinkler = sprinkler;
        this.scarecrow = scarecrow;
        this.mode = mode != null ? mode : "max_roi";
        this.grid = new int[height][width];
        this.gridItemId = new String[height][width];
        this.gridItemName = new String[height][width];
        this.gridSource = new int[height][width];
        this.sprinklerCoverage = new int[height][width];
        this.scarecrowCoverage = new int[height][width];

        // 预解析工具覆盖偏移
        this.sprinklerOffsets = CoverageParser.parse(sprinkler.getCoverageOffsets());
        this.scarecrowOffsets = CoverageParser.parse(scarecrow.getCoverageOffsets());

        // 导入用户已有布局（增量模式）
        if (existingGrid != null && !existingGrid.isEmpty()) {
            importExistingGrid(existingGrid);
        }
    }

    /**
     * 导入用户已有布局到 grid 和 gridSource
     */
    private void importExistingGrid(List<List<GridCell>> existingGrid) {
        for (int y = 0; y < Math.min(height, existingGrid.size()); y++) {
            List<GridCell> row = existingGrid.get(y);
            for (int x = 0; x < Math.min(width, row.size()); x++) {
                GridCell cell = row.get(x);
                if (cell == null || "empty".equals(cell.getType())) continue;

                int source = "user".equals(cell.getSource()) ? 1 : 0;

                if ("crop".equals(cell.getType())) {
                    Crop crop = findCropById(cell.getItemId());
                    boolean walkable = crop != null ? crop.getIsWalkable() : cell.isWalkable();
                    grid[y][x] = walkable ? 1 : 2;
                    gridItemId[y][x] = cell.getItemId();
                    gridItemName[y][x] = cell.getName();
                    gridSource[y][x] = source;
                } else if ("tool".equals(cell.getType()) || "sprinkler".equals(cell.getType())
                        || "scarecrow".equals(cell.getType())) {
                    String name = (cell.getName() != null) ? cell.getName() : "";
                    boolean isScarecrow = name.contains("稻草") || name.contains("scarecrow")
                            || "scarecrow".equals(cell.getType());
                    grid[y][x] = isScarecrow ? 4 : 3;
                    gridItemId[y][x] = isScarecrow ? scarecrow.getId() : sprinkler.getId();
                    gridItemName[y][x] = isScarecrow ? scarecrow.getName() : sprinkler.getName();
                    gridSource[y][x] = source;
                }
            }
        }
    }

    private Crop findCropById(String itemId) {
        if (itemId == null) return null;
        return allSeasonCrops.stream()
                .filter(c -> itemId.equals(c.getId()))
                .findFirst().orElse(null);
    }

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

    /**
     * 执行五阶段规划算法（喷水器优先模式）。
     * 关键顺序：先确定 5×5 喷水器布局，再放稻草人，最后在覆盖区域内分配作物。
     */
    public PlanningResult generate() {
        if (width < 6 || height < 6) {
            throw new IllegalArgumentException("GRID_TOO_SMALL: 地块尺寸不能小于6×6");
        }

        List<Crop> viableCrops = selectedCrops.stream()
            .filter(c -> c.getGrowthDays() < 28)
            .collect(Collectors.toList());

        if (viableCrops.isEmpty()) {
            throw new IllegalArgumentException("NO_VIABLE_CROPS: 没有可在28天内成熟的作物");
        }

        log.info("算法模式: {}, 画布: {}x{}, 选中作物: {}", mode, width, height, viableCrops.size());

        // === Phase 0: 分析现有布局（清除 auto，保留用户） ===
        phase0_analyzeLayout();

        // === Phase 1: 5×5 平铺放置喷水器（取代贪心） ===
        phase1_sprinklerTiling();

        // === Phase 2: 稻草人放置（排除喷水器位置） ===
        rebuildCoverageMaps();
        phase2_scarecrowPlacement();

        // === Phase 3: 作物分配（填满喷水器覆盖区域） ===
        rebuildCoverageMaps();
        phase3_cropAllocation(viableCrops);

        // === Phase 4: BFS连通性验证+修复+级联清理 ===
        phase4_connectivityValidation();
        rebuildCoverageMaps();
        cleanupOrphanedSprinklers();
        cleanupOrphanedScarecrows();
        rebuildCoverageMaps();

        // === Phase 5: 最终约束验证 ===
        phase5_finalVerification();

        return buildResult();
    }

    // ============ Phase 0: 分析现有布局 ============

    /**
     * 分析底图：
     * 1. 清除所有 auto 格子（从用户底图+空白画布开始）
     * 2. 计算用户喷水器/稻草人的覆盖地图
     */
    private void phase0_analyzeLayout() {
        // 步骤1: 清除所有自动放置的格子，只保留用户放置的
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (gridSource[y][x] == 0 && grid[y][x] != 0) {
                    grid[y][x] = 0;
                    gridItemId[y][x] = null;
                    gridItemName[y][x] = null;
                }
            }
        }
        rebuildCoverageMaps();
    }

    /**
     * 根据当前网格重新计算喷水器/稻草人覆盖图。
     */
    private void rebuildCoverageMaps() {
        this.sprinklerCoverage = new int[height][width];
        this.scarecrowCoverage = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 3) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            sprinklerCoverage[cy][cx]++;
                        }
                    }
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 4) {
                    for (int[] off : scarecrowOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            scarecrowCoverage[cy][cx]++;
                        }
                    }
                }
            }
        }
    }

    // ============ Phase 1: 5×5 平铺喷水器放置 ============

    /**
     * 基于 5×5 最小重复单元的喷水器平铺放置。
     *
     * 数学原理：对于 cross/range=1 喷水器，在 (r,c) 处放置喷水器当且仅当
     * (r + 2c) mod 5 == offset，可实现零重叠、零遗漏的理论最优覆盖。
     * 5×5 单元含 5 个喷水器，覆盖 25 格，密度 1/5。
     *
     * 流程：
     * 1. 遍历 5 个偏移（0-4），选择与用户物品冲突最小、覆盖率最高的偏移
     * 2. 按选中偏移在画布上平铺喷水器，跳过不合法位置
     * 3. 边缘截断允许（超出边界的覆盖格被忽略）
     */
    private void phase1_sprinklerTiling() {
        // 仅适用于标准十字形 range=1 喷水器
        if (!isStandardCrossSprinkler()) {
            log.warn("喷水器非标准 cross/range=1，回退到贪心放置");
            phase1_sprinklerPlacementGreedy();
            return;
        }

        int bestOffset = selectBestOffset();
        log.info("5×5 平铺: 选中偏移={}, 画布={}x{}", bestOffset, width, height);

        int placed = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((y + 2 * x) % 5 != bestOffset) continue;
                if (canPlacePatternSprinkler(y, x)) {
                    placeSprinklerAt(y, x);
                    placed++;
                }
            }
        }
        log.info("5×5 平铺完成: 放置了{}个自动喷水器", placed);
    }

    /**
     * 判断当前喷水器是否为标准十字形 range=1
     */
    private boolean isStandardCrossSprinkler() {
        if (sprinklerOffsets.size() != 4) return false;
        for (int[] off : sprinklerOffsets) {
            int absSum = Math.abs(off[0]) + Math.abs(off[1]);
            if (absSum != 1) return false;
        }
        return true;
    }

    /**
     * 遍历 5 个偏移，选择得分最高的偏移
     */
    private int selectBestOffset() {
        int bestOffset = 0;
        int bestScore = Integer.MIN_VALUE;

        for (int offset = 0; offset < 5; offset++) {
            int score = evaluateOffset(offset);
            if (score > bestScore) {
                bestScore = score;
                bestOffset = offset;
            }
        }
        return bestOffset;
    }

    /**
     * 评估单个偏移的得分
     * score = validPositions × 100 + userCropsCovered × 10 + userSprinklersAligned × 50
     */
    private int evaluateOffset(int offset) {
        int validPositions = 0;
        int userCropsCovered = 0;
        int userSprinklersAligned = 0;

        // 临时覆盖地图（不修改全局状态）
        int[][] tempCoverage = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 3 && gridSource[y][x] == 1) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            tempCoverage[cy][cx]++;
                        }
                    }
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((y + 2 * x) % 5 != offset) continue;

                if (grid[y][x] == 3 && gridSource[y][x] == 1) {
                    userSprinklersAligned++;
                    continue;
                }

                if (!canPlacePatternSprinklerWithCoverage(y, x, tempCoverage)) continue;

                validPositions++;
                for (int[] off : sprinklerOffsets) {
                    int cy = y + off[0], cx = x + off[1];
                    if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                        tempCoverage[cy][cx]++;
                        if ((grid[cy][cx] == 1 || grid[cy][cx] == 2) && gridSource[cy][cx] == 1) {
                            userCropsCovered++;
                        }
                    }
                }
            }
        }

        return validPositions * 100 + userCropsCovered * 10 + userSprinklersAligned * 50;
    }

    /**
     * 使用临时覆盖地图检查图案位置是否可放置（用于评估阶段）
     */
    private boolean canPlacePatternSprinklerWithCoverage(int y, int x, int[][] tempCoverage) {
        if (grid[y][x] != 0 || gridSource[y][x] == 1) return false;
        if (isScarecrowCenter(y, x)) return false;
        for (int[] off : sprinklerOffsets) {
            int cy = y + off[0], cx = x + off[1];
            if (cy < 0 || cy >= height || cx < 0 || cx >= width) continue;
            if (tempCoverage[cy][cx] > 0) return false;
            if (grid[cy][cx] == 3 || grid[cy][cx] == 4) return false;
        }
        return true;
    }

    /**
     * 检查在 5×5 图案位置 (y,x) 是否可以放置喷水器
     * 边缘截断允许：超出边界的覆盖格被忽略
     */
    private boolean canPlacePatternSprinkler(int y, int x) {
        if (grid[y][x] != 0 || gridSource[y][x] == 1) return false;
        if (isScarecrowCenter(y, x)) return false;  // 跳过稻草人预留中心
        for (int[] off : sprinklerOffsets) {
            int cy = y + off[0], cx = x + off[1];
            if (cy < 0 || cy >= height || cx < 0 || cx >= width) continue;
            if (sprinklerCoverage[cy][cx] > 0) return false;
            if (grid[cy][cx] == 3 || grid[cy][cx] == 4) return false;
        }
        return true;
    }

    /**
     * 判断 (y,x) 是否为稻草人分区中心（预留位置）
     * 使用缓存的 Set 避免重复创建 List 和线性查找
     */
    private Set<Integer> cachedRowCenters;
    private Set<Integer> cachedColCenters;

    private boolean isScarecrowCenter(int y, int x) {
        if (cachedRowCenters == null) {
            cachedRowCenters = new HashSet<>(scarecrowCenters(height));
            cachedColCenters = new HashSet<>(scarecrowCenters(width));
        }
        return cachedRowCenters.contains(y) && cachedColCenters.contains(x);
    }

    /**
     * 在 (y,x) 放置喷水器并更新覆盖地图
     */
    private void placeSprinklerAt(int y, int x) {
        grid[y][x] = 3;
        gridItemId[y][x] = sprinkler.getId();
        gridItemName[y][x] = sprinkler.getName();
        gridSource[y][x] = 0;
        for (int[] off : sprinklerOffsets) {
            int cy = y + off[0], cx = x + off[1];
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                sprinklerCoverage[cy][cx]++;
            }
        }
    }

    /**
     * 回退：贪心放置喷水器（非标准喷水器时使用）
     */
    private void phase1_sprinklerPlacementGreedy() {
        while (true) {
            int bestY = -1, bestX = -1, bestScore = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (grid[y][x] != 0 || gridSource[y][x] == 1) continue;
                    int score = evaluateSprinklerPositionGreedy(y, x);
                    if (score > bestScore) {
                        bestScore = score;
                        bestY = y;
                        bestX = x;
                    }
                }
            }
            if (bestScore == 0) break;
            placeSprinklerAt(bestY, bestX);
        }
    }

    private int evaluateSprinklerPositionGreedy(int y, int x) {
        int score = 0;
        for (int[] off : sprinklerOffsets) {
            int cy = y + off[0], cx = x + off[1];
            if (cy < 0 || cy >= height || cx < 0 || cx >= width) return -1;
            if (sprinklerCoverage[cy][cx] > 0) return -1;
            if (grid[cy][cx] == 3 || grid[cy][cx] == 4) return -1;
            if (grid[cy][cx] == 0) continue;
            if (grid[cy][cx] == 1 || grid[cy][cx] == 2) score++;
        }
        return score;
    }

    // ============ Phase 2: 稻草人最优放置 ============

    /**
     * 分区放置稻草人。
     * 在喷水器优先流程中，此时作物尚未放置，因此评估基于喷水器覆盖区域
     * （sprinklerCoverage > 0 的格子将在 Phase 3 种上作物）。
     *
     * 约束：
     * - 稻草人之间覆盖不重叠
     * - 稻草人不能放在喷水器/已有物品位置
     */
    private void phase2_scarecrowPlacement() {
        for (int centerY : scarecrowCenters(height)) {
            for (int centerX : scarecrowCenters(width)) {
                if (!tileHasUnprotectedCoverage(centerY, centerX)) continue;

                int[] pos = findScarecrowPositionForTile(centerY, centerX);
                if (pos == null) continue;

                grid[pos[0]][pos[1]] = 4;
                gridItemId[pos[0]][pos[1]] = scarecrow.getId();
                gridItemName[pos[0]][pos[1]] = scarecrow.getName();
                gridSource[pos[0]][pos[1]] = 0;
                for (int[] off : scarecrowOffsets) {
                    int cy = pos[0] + off[0], cx = pos[1] + off[1];
                    if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                        scarecrowCoverage[cy][cx]++;
                    }
                }
            }
        }
    }

    private List<Integer> scarecrowCenters(int size) {
        List<Integer> centers = new ArrayList<>();
        if (size <= 13) {
            centers.add(size / 2);
            return centers;
        }
        for (int center = 0; center < size; center += 13) {
            centers.add(center);
        }
        return centers;
    }

    /**
     * 检查分区内是否有未被稻草人保护的喷水器覆盖区域
     * （喷水器覆盖 = 将来会种作物的区域）
     */
    private boolean tileHasUnprotectedCoverage(int centerY, int centerX) {
        for (int[] off : scarecrowOffsets) {
            int cy = centerY + off[0], cx = centerX + off[1];
            if (cy >= 0 && cy < height && cx >= 0 && cx < width
                    && sprinklerCoverage[cy][cx] > 0
                    && scarecrowCoverage[cy][cx] == 0) {
                return true;
            }
        }
        return false;
    }

    private int[] findScarecrowPositionForTile(int centerY, int centerX) {
        if (canPlaceScarecrowAt(centerY, centerX)) {
            return new int[]{centerY, centerX};
        }
        for (int radius = 1; radius <= 6; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dy) != radius && Math.abs(dx) != radius) continue;
                    int y = centerY + dy, x = centerX + dx;
                    if (canPlaceScarecrowAt(y, x)) {
                        return new int[]{y, x};
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlaceScarecrowAt(int y, int x) {
        if (y < 0 || y >= height || x < 0 || x >= width) return false;
        if (gridSource[y][x] == 1) return false;
        if (grid[y][x] != 0) return false;  // 只允许空地（排除喷水器、稻草人、作物）
        return evaluateScarecrowPosition(y, x) > 0;
    }

    /**
     * 评估稻草人位置得分
     * 在喷水器优先流程中，基于喷水器覆盖区域内未受保护的格子数评分
     */
    private int evaluateScarecrowPosition(int y, int x) {
        int score = 0;

        for (int[] off : scarecrowOffsets) {
            int cy = y + off[0], cx = x + off[1];

            if (cy < 0 || cy >= height || cx < 0 || cx >= width) continue;

            // 已被其他稻草人覆盖 → 重叠，无效
            if (scarecrowCoverage[cy][cx] > 0) return -1;

            // 统计可保护的喷水器覆盖区域（将来会种作物）
            if (sprinklerCoverage[cy][cx] > 0) {
                score++;
            }
        }

        return score;
    }

    // ============ Phase 3: 作物分配（基于 5×5 喷水器覆盖区域） ============

    /**
     * 作物分配（喷水器优先模式）：
     * 1. 保留用户底图中的作物
     * 2. 收集所有喷水器覆盖区域内的可种植空地和
     * 3. 先补齐用户选择作物每种至少一株，再按策略（max_roi/weighted_balanced/fully_balanced）填满
     * 4. 受预算约束：超出预算时停止分配
     */
    private void phase3_cropAllocation(List<Crop> viableCrops) {
        List<CropROI> ranked = viableCrops.stream()
            .map(c -> new CropROI(c, CropCalculator.calculate(c)))
            .filter(cr -> cr.getRevenue() != null)
            .sorted(
                Comparator.comparingDouble((CropROI cr) -> cr.getRevenue().getRoi()).reversed()
                    .thenComparing((CropROI cr) -> cr.getRevenue().getTotalRevenue() - cr.getRevenue().getTotalCost(), Comparator.reverseOrder())
                    .thenComparing(cr -> cr.getCrop().getSeedPrice())
            )
            .collect(Collectors.toList());

        int totalCost = calculateExistingCost();
        Map<String, Integer> cropCounts = new HashMap<>();

        // 统计用户已放置的作物
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((grid[y][x] == 1 || grid[y][x] == 2) && gridSource[y][x] == 1) {
                    cropCounts.merge(gridItemId[y][x], 1, Integer::sum);
                }
            }
        }

        Set<String> missingCropIds = ranked.stream()
            .map(cr -> cr.getCrop().getId())
            .filter(id -> cropCounts.getOrDefault(id, 0) == 0)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // 收集所有可种植的喷水器覆盖格
        List<int[]> plantableCells = collectPlantableCells();
        log.info("作物分配: 可种植覆盖格={}, 预算剩余={}", plantableCells.size(), budget - totalCost);

        // 逐格分配作物
        int planted = 0;
        for (int[] pos : plantableCells) {
            Crop crop = chooseNextCropWithinBudget(ranked, missingCropIds, cropCounts, budget - totalCost);
            if (crop == null) break;  // 没有任何作物可种（所有作物都超预算或无正收益）

            int cost = cropMonthCost(crop);
            totalCost += cost;
            placeCrop(pos[0], pos[1], crop);
            gridSource[pos[0]][pos[1]] = 0;
            planted++;
            cropCounts.merge(crop.getId(), 1, Integer::sum);
            missingCropIds.remove(crop.getId());
        }
        log.info("作物分配完成: 种植了{}株作物, 总投入={}", planted, totalCost);
    }

    /**
     * 收集所有可种植的喷水器覆盖格：
     * - 被喷水器覆盖（sprinklerCoverage > 0）
     * - 当前是空地（grid == 0）且非用户放置（gridSource == 0）
     * - 不是喷水器/稻草人位置
     */
    private List<int[]> collectPlantableCells() {
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] != 0 || gridSource[y][x] == 1) continue;
                if (sprinklerCoverage[y][x] == 0) continue;         // 不在喷水器覆盖范围内
                cells.add(new int[]{y, x});
            }
        }
        return cells;
    }

    private Crop chooseNextCrop(List<CropROI> ranked, Set<String> missingCropIds,
                                Map<String, Integer> cropCounts) {
        switch (mode) {
            case "weighted_balanced":
                return chooseNextCropWeightedBalanced(ranked, cropCounts);
            case "fully_balanced":
                return chooseNextCropFullyBalanced(ranked, cropCounts);
            default: // "max_roi"
                return chooseNextCropMaxRoi(ranked, missingCropIds);
        }
    }

    /**
     * 预算感知的作物选择：先按策略选择首选作物，若超预算则尝试更便宜的替代作物。
     * 修复了 max_roi 模式下最高 ROI 作物超预算导致所有格子被跳过的 Bug。
     */
    private Crop chooseNextCropWithinBudget(List<CropROI> ranked, Set<String> missingCropIds,
                                            Map<String, Integer> cropCounts, int remainingBudget) {
        // 先尝试策略首选作物
        Crop preferred = chooseNextCrop(ranked, missingCropIds, cropCounts);
        if (preferred == null) return null;

        int preferredCost = cropMonthCost(preferred);
        if (preferredCost <= remainingBudget) return preferred;

        // 首选作物超预算 → 尝试更便宜的替代作物（按成本升序）
        List<CropROI> affordable = ranked.stream()
                .filter(cr -> cropMonthCost(cr.getCrop()) <= remainingBudget)
                .filter(cr -> cr.getRevenue().getTotalRevenue() > cr.getRevenue().getTotalCost())
                .sorted(Comparator.comparingInt(cr -> cropMonthCost(cr.getCrop())))
                .collect(Collectors.toList());

        if (affordable.isEmpty()) return null;  // 没有任何作物能种

        // 在可负担作物中，优先选缺失作物
        for (CropROI cr : affordable) {
            if (missingCropIds.contains(cr.getCrop().getId())) {
                return cr.getCrop();
            }
        }
        // 否则选最便宜的正收益作物
        return affordable.get(0).getCrop();
    }

    /**
     * 极限投入产出比模式：missing crops 优先，然后最高 ROI
     */
    private Crop chooseNextCropMaxRoi(List<CropROI> ranked, Set<String> missingCropIds) {
        if (!missingCropIds.isEmpty()) {
            for (CropROI cr : ranked) {
                if (missingCropIds.contains(cr.getCrop().getId())) {
                    return cr.getCrop();
                }
            }
        }
        for (CropROI cr : ranked) {
            CropRevenue revenue = cr.getRevenue();
            if (revenue.getTotalRevenue() > revenue.getTotalCost()) {
                return cr.getCrop();
            }
        }
        return null;
    }

    /**
     * 加权均衡模式：按 ROI 占比分配，选择 deficit 最大的作物
     * deficit = targetRatio - actualRatio
     * targetRatio = crop.ROI / sum(ROI)
     */
    private Crop chooseNextCropWeightedBalanced(List<CropROI> ranked, Map<String, Integer> cropCounts) {
        double totalRoi = ranked.stream()
            .mapToDouble(cr -> cr.getRevenue().getRoi())
            .sum();
        if (totalRoi <= 0) return chooseNextCropMaxRoi(ranked, new LinkedHashSet<>());

        int totalPlaced = ranked.stream()
            .mapToInt(cr -> cropCounts.getOrDefault(cr.getCrop().getId(), 0))
            .sum();

        Crop bestCrop = null;
        double bestDeficit = Double.NEGATIVE_INFINITY;

        for (CropROI cr : ranked) {
            double targetRatio = cr.getRevenue().getRoi() / totalRoi;
            int count = cropCounts.getOrDefault(cr.getCrop().getId(), 0);
            double actualRatio = totalPlaced > 0 ? (double) count / totalPlaced : 0;
            double deficit = targetRatio - actualRatio;

            if (deficit > bestDeficit) {
                bestDeficit = deficit;
                bestCrop = cr.getCrop();
            }
        }
        return bestCrop;
    }

    /**
     * 完全均衡模式：各作物数量均等，选择当前数量最少的作物（相同则按 ROI 排序）
     */
    private Crop chooseNextCropFullyBalanced(List<CropROI> ranked, Map<String, Integer> cropCounts) {
        int minCount = Integer.MAX_VALUE;
        for (CropROI cr : ranked) {
            int count = cropCounts.getOrDefault(cr.getCrop().getId(), 0);
            if (count < minCount) {
                minCount = count;
            }
        }
        // ranked 已按 ROI 降序，返回第一个数量最少的作物
        for (CropROI cr : ranked) {
            if (cropCounts.getOrDefault(cr.getCrop().getId(), 0) == minCount) {
                return cr.getCrop();
            }
        }
        return null;
    }

    private int cropMonthCost(Crop crop) {
        CropRevenue revenue = CropCalculator.calculate(crop);
        return revenue != null ? revenue.getTotalCost() : crop.getSeedPrice();
    }

    /**
     * 清理孤立喷水器：如果一个自动喷水器的4格覆盖范围内没有任何作物，则移除它
     */
    private void cleanupOrphanedSprinklers() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (grid[y][x] == 3 && gridSource[y][x] == 0) {
                        boolean hasCrop = false;
                        for (int[] off : sprinklerOffsets) {
                            int cy = y + off[0], cx = x + off[1];
                            if (cy >= 0 && cy < height && cx >= 0 && cx < width
                                    && (grid[cy][cx] == 1 || grid[cy][cx] == 2)) {
                                hasCrop = true;
                                break;
                            }
                        }
                        if (!hasCrop) {
                            // 移除孤立喷水器
                            grid[y][x] = 0;
                            gridItemId[y][x] = null;
                            gridItemName[y][x] = null;
                            gridSource[y][x] = 0;
                            // 更新覆盖地图
                            for (int[] off : sprinklerOffsets) {
                                int cy = y + off[0], cx = x + off[1];
                                if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                                    sprinklerCoverage[cy][cx] = Math.max(0, sprinklerCoverage[cy][cx] - 1);
                                }
                            }
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    // ============ Phase 4: BFS连通性验证+修复 ============

    /**
     * BFS连通性验证：确保所有作物从地图边缘可达
     * 不可达的自动作物：尝试修复（棚架转可踩踏 → 移除）
     * 如果移除作物导致喷水器覆盖区域出现空地，也移除该喷水器（级联清理）
     */
    private void phase4_connectivityValidation() {
        boolean[][] reachable = bfsFromEdge();

        for (int attempt = 0; attempt < 3; attempt++) {
            List<int[]> unreachable = new ArrayList<>();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (grid[y][x] == 1 || grid[y][x] == 2) {
                        if (!hasReachableNeighbor(y, x, reachable)) {
                            unreachable.add(new int[]{y, x});
                        }
                    }
                }
            }

            if (unreachable.isEmpty()) break;

            for (int[] pos : unreachable) {
                if (gridSource[pos[0]][pos[1]] == 1) continue;

                if (attempt == 0 && grid[pos[0]][pos[1]] == 2) {
                    grid[pos[0]][pos[1]] = 1;
                } else {
                    grid[pos[0]][pos[1]] = 0;
                    gridItemId[pos[0]][pos[1]] = null;
                    gridItemName[pos[0]][pos[1]] = null;
                    gridSource[pos[0]][pos[1]] = 0;
                }
            }

            reachable = bfsFromEdge();
        }

        // 级联清理：移除作物后可能导致喷水器覆盖区域有空地
        cleanupSprinklerEmptyCells();
    }

    /**
     * 清理没有保护任何作物的自动稻草人。
     */
    private void cleanupOrphanedScarecrows() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] != 4 || gridSource[y][x] == 1) continue;
                boolean hasCrop = false;
                for (int[] off : scarecrowOffsets) {
                    int cy = y + off[0], cx = x + off[1];
                    if (cy >= 0 && cy < height && cx >= 0 && cx < width
                            && (grid[cy][cx] == 1 || grid[cy][cx] == 2)) {
                        hasCrop = true;
                        break;
                    }
                }
                if (!hasCrop) {
                    grid[y][x] = 0;
                    gridItemId[y][x] = null;
                    gridItemName[y][x] = null;
                    gridSource[y][x] = 0;
                }
            }
        }
    }

    /**
     * 清理喷水器覆盖区域内因BFS修复而产生的空地
     */
    private void cleanupSprinklerEmptyCells() {
        // BFS 移除作物后，喷水器覆盖范围可能出现空地。
        // 只移除完全没有作物的喷水器（与 cleanupOrphanedSprinklers 等价），
        // 保留仍有作物覆盖的喷水器，避免出现"作物无喷水器覆盖"的约束违规。
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (grid[y][x] == 3 && gridSource[y][x] == 0) {
                        boolean hasCrop = false;
                        for (int[] off : sprinklerOffsets) {
                            int cy = y + off[0], cx = x + off[1];
                            if (cy >= 0 && cy < height && cx >= 0 && cx < width
                                    && (grid[cy][cx] == 1 || grid[cy][cx] == 2)) {
                                hasCrop = true;
                                break;
                            }
                        }
                        if (!hasCrop) {
                            grid[y][x] = 0;
                            gridItemId[y][x] = null;
                            gridItemName[y][x] = null;
                            gridSource[y][x] = 0;
                            for (int[] off : sprinklerOffsets) {
                                int cy = y + off[0], cx = x + off[1];
                                if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                                    sprinklerCoverage[cy][cx] = Math.max(0, sprinklerCoverage[cy][cx] - 1);
                                }
                            }
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * 查找ROI最高的作物
     */
    private Crop findHighestROICrop() {
        Crop bestCrop = null;
        double bestROI = 0;
        for (Crop crop : selectedCrops) {
            CropRevenue rev = CropCalculator.calculate(crop);
            if (rev != null && rev.getRoi() > bestROI) {
                bestROI = rev.getRoi();
                bestCrop = crop;
            }
        }
        return bestCrop;
    }

    // ============ Phase 5: 最终约束验证 ============

    /**
     * 全面验证约束条件，输出审计日志
     */
    private void phase5_finalVerification() {
        // 验证喷水器覆盖无重叠（仅检查自动放置的喷水器）
        int sprinklerOverlapCount = 0;
        int scarecrowOverlapCount = 0;

        // 重新计算覆盖地图进行审计
        int[][] auditSprinkler = new int[height][width];
        int[][] auditScarecrow = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 3) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            auditSprinkler[cy][cx]++;
                            if (auditSprinkler[cy][cx] > 1) {
                                sprinklerOverlapCount++;
                            }
                        }
                    }
                }
                if (grid[y][x] == 4) {
                    for (int[] off : scarecrowOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            auditScarecrow[cy][cx]++;
                            if (auditScarecrow[cy][cx] > 1) {
                                scarecrowOverlapCount++;
                            }
                        }
                    }
                }
            }
        }

        if (sprinklerOverlapCount > 0) {
            log.warn("[Phase5审计] 发现{}个喷水器覆盖重叠格子（可能来自用户放置的喷水器）", sprinklerOverlapCount);
        }
        if (scarecrowOverlapCount > 0) {
            log.warn("[Phase5审计] 发现{}个稻草人覆盖重叠格子（可能来自用户放置的稻草人）", scarecrowOverlapCount);
        }

        // 验证所有自动喷水器覆盖范围内无空地
        int emptyInSprinkler = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 3 && gridSource[y][x] == 0) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width && grid[cy][cx] == 0) {
                            emptyInSprinkler++;
                        }
                    }
                }
            }
        }
        if (emptyInSprinkler > 0) {
            log.warn("[Phase5审计] 自动喷水器覆盖范围内有{}个空地", emptyInSprinkler);
        }
    }

    /**
     * 放置作物
     */
    private void placeCrop(int y, int x, Crop crop) {
        grid[y][x] = crop.getIsWalkable() ? 1 : 2;
        gridItemId[y][x] = crop.getId();
        gridItemName[y][x] = crop.getName();
    }

    /**
     * 检查作物格是否被喷水器覆盖
     */
    private boolean isSprayed(int cy, int cx) {
        for (int[] off : sprinklerOffsets) {
            int sy = cy + off[0];
            int sx = cx + off[1];
            if (sy >= 0 && sy < height && sx >= 0 && sx < width && grid[sy][sx] == 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * BFS从地图边缘开始，返回可达性矩阵
     */
    private boolean[][] bfsFromEdge() {
        boolean[][] visited = new boolean[height][width];
        Queue<int[]> queue = new LinkedList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((y == 0 || y == height - 1 || x == 0 || x == width - 1) && isPassable(y, x)) {
                    queue.offer(new int[]{y, x});
                    visited[y][x] = true;
                }
            }
        }

        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            for (int[] d : dirs) {
                int ny = curr[0] + d[0], nx = curr[1] + d[1];
                if (ny >= 0 && ny < height && nx >= 0 && nx < width
                    && !visited[ny][nx] && isPassable(ny, nx)) {
                    visited[ny][nx] = true;
                    queue.offer(new int[]{ny, nx});
                }
            }
        }

        return visited;
    }

    private boolean isPassable(int y, int x) {
        return grid[y][x] == 0 || grid[y][x] == 1;
    }

    private boolean hasReachableNeighbor(int y, int x, boolean[][] reachable) {
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            int ny = y + d[0], nx = x + d[1];
            if (ny >= 0 && ny < height && nx >= 0 && nx < width && reachable[ny][nx]) {
                return true;
            }
        }
        return false;
    }

    // ============ 构建结果 ============

    private PlanningResult buildResult() {
        StatsResponse stats = calculateStats();
        ConstraintCheck check = checkConstraints();
        stats.setConstraintCheck(check);

        List<List<GridCell>> resultGrid = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            List<GridCell> row = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                GridCell cell = new GridCell();
                if (grid[y][x] == 0) {
                    cell.setType("empty");
                    cell.setWalkable(true);
                    cell.setSource(null);
                } else if (grid[y][x] == 1 || grid[y][x] == 2) {
                    cell.setType("crop");
                    cell.setItemId(gridItemId[y][x]);
                    cell.setName(gridItemName[y][x]);
                    cell.setWalkable(grid[y][x] == 1);
                    cell.setSource(gridSource[y][x] == 1 ? "user" : "auto");
                } else if (grid[y][x] == 3) {
                    cell.setType("tool");
                    cell.setItemId(sprinkler.getId());
                    cell.setName(sprinkler.getName());
                    cell.setWalkable(false);
                    cell.setSource(gridSource[y][x] == 1 ? "user" : "auto");
                } else if (grid[y][x] == 4) {
                    cell.setType("tool");
                    cell.setItemId(scarecrow.getId());
                    cell.setName(scarecrow.getName());
                    cell.setWalkable(false);
                    cell.setSource(gridSource[y][x] == 1 ? "user" : "auto");
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

    private StatsResponse calculateStats() {
        Map<String, Integer> cropCounts = new HashMap<>();
        Map<String, Integer> toolCounts = new HashMap<>();
        int totalCost = 0;
        int totalRevenue = 0;

        Map<String, Crop> cropMap = allSeasonCrops.stream()
            .collect(Collectors.toMap(Crop::getId, c -> c));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cellType = grid[y][x];

                if (cellType == 1 || cellType == 2) {
                    String cropId = gridItemId[y][x];
                    String cropName = gridItemName[y][x];
                    cropCounts.merge(cropName, 1, Integer::sum);

                    Crop crop = cropMap.get(cropId);
                    if (crop != null) {
                        CropRevenue revenue = CropCalculator.calculate(crop);
                        if (revenue != null) {
                            totalCost += revenue.getTotalCost();
                            totalRevenue += revenue.getTotalRevenue();
                        }
                    }
                } else if (cellType == 3) {
                    toolCounts.merge(sprinkler.getName(), 1, Integer::sum);
                } else if (cellType == 4) {
                    toolCounts.merge(scarecrow.getName(), 1, Integer::sum);
                }
            }
        }

        double roi = totalCost > 0 ? (double) totalRevenue / totalCost : 0.0;
        int budgetRemaining = budget - totalCost;

        StatsResponse stats = new StatsResponse();
        stats.setCropCounts(cropCounts);
        stats.setToolCounts(toolCounts);
        stats.setTotalCost(totalCost);
        stats.setTotalRevenue(totalRevenue);
        stats.setRoi(roi);
        stats.setBudgetRemaining(budgetRemaining);

        return stats;
    }

    /**
     * 约束检查（含重叠审计）
     */
    private ConstraintCheck checkConstraints() {
        boolean[][] reachable = bfsFromEdge();
        int unsprayedCrops = 0;
        int unprotectedCrops = 0;
        int unreachableCrops = 0;
        int sprinklerEmptyCells = 0;
        int sprinklerOverlapCells = 0;
        int scarecrowOverlapCells = 0;
        List<String> messages = new ArrayList<>();

        // 重建审计覆盖地图
        int[][] auditSprinkler = new int[height][width];
        int[][] auditScarecrow = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 3) {
                    for (int[] off : sprinklerOffsets) {
                        int sy = y + off[0], sx = x + off[1];
                        if (sy >= 0 && sy < height && sx >= 0 && sx < width) {
                            auditSprinkler[sy][sx]++;
                        }
                    }
                }
                if (grid[y][x] == 4) {
                    for (int[] off : scarecrowOffsets) {
                        int sy = y + off[0], sx = x + off[1];
                        if (sy >= 0 && sy < height && sx >= 0 && sx < width) {
                            auditScarecrow[sy][sx]++;
                        }
                    }
                }
            }
        }

        // 统计重叠格子
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (auditSprinkler[y][x] > 1) sprinklerOverlapCells++;
                if (auditScarecrow[y][x] > 1) scarecrowOverlapCells++;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cellType = grid[y][x];

                if (cellType == 1 || cellType == 2) {
                    if (!hasReachableNeighbor(y, x, reachable)) {
                        unreachableCrops++;
                    }
                    if (sprinklerCoverage[y][x] == 0) {
                        unsprayedCrops++;
                    }
                    boolean protectedFlag = false;
                    for (int[] off : scarecrowOffsets) {
                        int sy = y + off[0], sx = x + off[1];
                        if (sy >= 0 && sy < height && sx >= 0 && sx < width && grid[sy][sx] == 4) {
                            protectedFlag = true;
                            break;
                        }
                    }
                    if (!protectedFlag) {
                        unprotectedCrops++;
                    }
                }

                if (cellType == 3) {
                    for (int[] off : sprinklerOffsets) {
                        int sy = y + off[0], sx = x + off[1];
                        if (sy >= 0 && sy < height && sx >= 0 && sx < width && grid[sy][sx] == 0) {
                            sprinklerEmptyCells++;
                        }
                    }
                }
            }
        }

        boolean allSatisfied = (unreachableCrops == 0 && unsprayedCrops == 0
                && unprotectedCrops == 0 && sprinklerEmptyCells == 0
                && sprinklerOverlapCells == 0 && scarecrowOverlapCells == 0);

        if (!allSatisfied) {
            if (unreachableCrops > 0) {
                messages.add(String.format("%d株作物不可达", unreachableCrops));
            }
            if (unsprayedCrops > 0) {
                messages.add(String.format("%d株作物未被喷水器覆盖", unsprayedCrops));
            }
            if (unprotectedCrops > 0) {
                messages.add(String.format("%d株作物未被稻草人覆盖", unprotectedCrops));
            }
            if (sprinklerEmptyCells > 0) {
                messages.add(String.format("%d格喷水器覆盖区域空置", sprinklerEmptyCells));
            }
            if (sprinklerOverlapCells > 0) {
                messages.add(String.format("%d格喷水器覆盖重叠", sprinklerOverlapCells));
            }
            if (scarecrowOverlapCells > 0) {
                messages.add(String.format("%d格稻草人覆盖重叠", scarecrowOverlapCells));
            }
        }

        ConstraintCheck check = new ConstraintCheck();
        check.setAllSatisfied(allSatisfied);
        check.setUnreachableCrops(unreachableCrops);
        check.setUnsprayedCrops(unsprayedCrops);
        check.setUnprotectedCrops(unprotectedCrops);
        check.setSprinklerEmptyCells(sprinklerEmptyCells);
        check.setSprinklerOverlapCells(sprinklerOverlapCells);
        check.setScarecrowOverlapCells(scarecrowOverlapCells);
        check.setMessages(messages);

        return check;
    }

    // ============ 内部辅助类 ============

    private static class CropROI {
        private final Crop crop;
        private final CropRevenue revenue;

        CropROI(Crop crop, CropRevenue revenue) {
            this.crop = crop;
            this.revenue = revenue;
        }

        Crop getCrop() { return crop; }
        CropRevenue getRevenue() { return revenue; }
    }
}
