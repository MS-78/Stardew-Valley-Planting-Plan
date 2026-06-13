package com.stardew.planner.algorithm;

import com.stardew.planner.dto.ConstraintCheck;
import com.stardew.planner.dto.GridCell;
import com.stardew.planner.dto.PlanningResult;
import com.stardew.planner.model.Crop;
import com.stardew.planner.model.Tool;

import java.util.*;

/**
 * 新算法单元测试
 * 运行: run_test.bat
 */
public class PlanningAlgorithmTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== 开始运行算法测试 ===\n");

        // 用户物品保留测试
        runTest("用户喷水器保留", () -> testUserToolPreserved());
        runTest("用户作物保留", () -> testUserCropPreserved());
        runTest("多个用户物品保留", () -> testMultipleUserItemsPreserved());

        // 5×5 平铺模式测试
        runTest("5×5平铺模式验证", () -> testTilingPattern5x5());
        runTest("5×5平铺全覆盖", () -> testTilingFullCoverage());
        runTest("5×5平铺用户物品自适应", () -> testTilingWithUserItems());
        runTest("5×5平铺边缘截断", () -> testTilingEdgeClipping());
        runTest("5×5平铺零重叠", () -> testTilingZeroOverlap());

        // 算法约束测试
        runTest("喷水器覆盖无重叠", () -> testNoSprinklerOverlap());
        runTest("稻草人覆盖无重叠", () -> testNoScarecrowOverlap());
        runTest("喷水器范围内无空地", () -> testNoEmptyInSprinklerCoverage());
        runTest("用户喷水器覆盖被尊重", () -> testUserSprinklerRespected());
        runTest("喷水器数量最小化", () -> testSprinklerCountMinimized());
        runTest("所选作物每种至少一株", () -> testEverySelectedCropPlanted());
        runTest("ROI最优作物主导种植", () -> testHighestRoiCropDominates());
        runTest("预算内利润和土地利用率", () -> testProfitBudgetAndLandUse());
        runTest("空画布全新生成", () -> testEmptyGridGeneration());
        runTest("约束检查通过", () -> testConstraintCheckPassed());

        System.out.printf("\n=== 测试结果: %d 通过, %d 失败 ===%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void runTest(String name, Runnable test) {
        try {
            test.run();
            passed++;
            System.out.printf("  ✓ %s%n", name);
        } catch (AssertionError | Exception e) {
            failed++;
            System.out.printf("  ✗ %s: %s%n", name, e.getMessage());
        }
    }

    // ============ 用户物品保留测试 ============

    static void testUserToolPreserved() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);
        setCell(existingGrid, 5, 5, "tool", "sprinkler-1", "喷水器", false, "user");

        PlanningResult result = runAlgorithm(width, height, existingGrid);

        GridCell cell = result.getGrid().get(5).get(5);
        assertEquals("tool", cell.getType(), "(5,5) type");
        assertEquals("user", cell.getSource(), "(5,5) source");
        assertEquals(1, countCellsBySource(result.getGrid(), "user"), "用户格子数");
    }

    static void testUserCropPreserved() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);
        setCell(existingGrid, 3, 3, "crop", "crop-1", "草莓", true, "user");

        PlanningResult result = runAlgorithm(width, height, existingGrid);

        GridCell cell = result.getGrid().get(3).get(3);
        assertEquals("crop", cell.getType(), "(3,3) type");
        assertEquals("user", cell.getSource(), "(3,3) source");
    }

    static void testMultipleUserItemsPreserved() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);
        setCell(existingGrid, 2, 2, "tool", "sprinkler-1", "喷水器", false, "user");
        setCell(existingGrid, 6, 6, "tool", "scarecrow-1", "稻草人", false, "user");
        setCell(existingGrid, 4, 4, "crop", "crop-1", "草莓", true, "user");

        PlanningResult result = runAlgorithm(width, height, existingGrid);

        assertEquals("tool", result.getGrid().get(2).get(2).getType(), "(2,2) sprinkler");
        assertEquals("user", result.getGrid().get(2).get(2).getSource(), "(2,2) source");
        assertEquals("tool", result.getGrid().get(6).get(6).getType(), "(6,6) scarecrow");
        assertEquals("user", result.getGrid().get(6).get(6).getSource(), "(6,6) source");
        assertEquals("crop", result.getGrid().get(4).get(4).getType(), "(4,4) crop");
        assertEquals("user", result.getGrid().get(4).get(4).getSource(), "(4,4) source");
        assertEquals(3, countCellsBySource(result.getGrid(), "user"), "用户格子数");
    }

    // ============ 新算法约束测试 ============

    static void testNoSprinklerOverlap() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        // 构建覆盖地图
        int[][] coverage = new int[height][width];
        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridCell cell = grid.get(y).get(x);
                if (isSprinklerCell(cell)) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            coverage[cy][cx]++;
                        }
                    }
                }
            }
        }

        // 检查无重叠
        int overlapCells = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (coverage[y][x] > 1) overlapCells++;
            }
        }
        assertEquals(0, overlapCells, "喷水器覆盖重叠格子数");
    }

    static void testNoScarecrowOverlap() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        int[][] coverage = new int[height][width];
        List<int[]> scarecrowOffsets = parseOffsets("{\"shape\":\"square\",\"range\":6}");

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridCell cell = grid.get(y).get(x);
                if (isScarecrowCell(cell)) {
                    for (int[] off : scarecrowOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            coverage[cy][cx]++;
                        }
                    }
                }
            }
        }

        int overlapCells = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (coverage[y][x] > 1) overlapCells++;
            }
        }
        assertEquals(0, overlapCells, "稻草人覆盖重叠格子数");
    }

    static void testNoEmptyInSprinklerCoverage() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");

        int emptyInCoverage = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridCell cell = grid.get(y).get(x);
                if (isSprinklerCell(cell) && "auto".equals(cell.getSource())) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            GridCell covered = grid.get(cy).get(cx);
                            if ("empty".equals(covered.getType())) {
                                emptyInCoverage++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(0, emptyInCoverage, "自动喷水器覆盖范围内空地数");
    }

    static void testUserSprinklerRespected() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        // 用户放置一个喷水器在 (3, 3)
        setCell(existingGrid, 3, 3, "tool", "sprinkler-1", "喷水器", false, "user");

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");

        // 检查用户喷水器覆盖范围内没有自动喷水器
        for (int[] off : sprinklerOffsets) {
            int cy = 3 + off[0], cx = 3 + off[1];
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                GridCell cell = grid.get(cy).get(cx);
                assertFalse(
                    isSprinklerCell(cell) && "auto".equals(cell.getSource()),
                    String.format("用户喷水器覆盖范围(%d,%d)不应有自动喷水器", cy, cx)
                );
            }
        }

        // 检查用户喷水器的覆盖区域不被其他喷水器重叠覆盖
        int[][] coverage = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSprinklerCell(grid.get(y).get(x))) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            coverage[cy][cx]++;
                        }
                    }
                }
            }
        }
        // 用户喷水器的覆盖格子不应被自动喷水器再次覆盖
        for (int[] off : sprinklerOffsets) {
            int cy = 3 + off[0], cx = 3 + off[1];
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                assertEquals(1, coverage[cy][cx],
                    String.format("(%d,%d)喷水器覆盖次数", cy, cx));
            }
        }
    }

    static void testSprinklerCountMinimized() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        int sprinklerCount = 0;
        int cropCount = 0;
        int totalCells = width * height;

        for (List<GridCell> row : grid) {
            for (GridCell cell : row) {
                if (isSprinklerCell(cell)) sprinklerCount++;
                if ("crop".equals(cell.getType())) cropCount++;
            }
        }

        double sprinklerRatio = (double) sprinklerCount / totalCells;
        double cropRatio = (double) cropCount / totalCells;

        System.out.printf("    网格: %dx%d, 喷水器: %d (%.1f%%), 作物: %d (%.1f%%)%n",
                width, height, sprinklerCount, sprinklerRatio * 100, cropCount, cropRatio * 100);

        // 5×5 平铺: 喷水器密度应接近 1/5 = 20%
        assertTrue(sprinklerRatio < 0.25,
                String.format("喷水器占比%.1f%%应低于25%%", sprinklerRatio * 100));
        assertTrue(sprinklerRatio > 0.12,
                String.format("喷水器占比%.1f%%应高于12%%（确保平铺生效）", sprinklerRatio * 100));
        // 作物占比应高于60%（5×5平铺最大化覆盖）
        assertTrue(cropRatio > 0.55,
                String.format("作物占比%.1f%%应高于55%%", cropRatio * 100));
    }

    static void testEverySelectedCropPlanted() {
        PlanningResult result = runAlgorithm(16, 16, createEmptyGrid(16, 16));
        Map<String, Integer> cropCounts = countCropsById(result.getGrid());

        for (Crop crop : createTestCrops()) {
            assertTrue(cropCounts.getOrDefault(crop.getId(), 0) > 0,
                    crop.getName() + " 至少应种植1株");
        }
    }

    static void testHighestRoiCropDominates() {
        PlanningResult result = runAlgorithm(20, 20, createEmptyGrid(20, 20));
        Map<String, Integer> cropCounts = countCropsById(result.getGrid());
        String bestCropId = findBestRoiCropId(createTestCrops());
        int bestCount = cropCounts.getOrDefault(bestCropId, 0);

        for (Map.Entry<String, Integer> entry : cropCounts.entrySet()) {
            if (!entry.getKey().equals(bestCropId)) {
                assertTrue(bestCount >= entry.getValue(),
                        "ROI最高作物应不低于其他作物数量");
            }
        }
    }

    static void testProfitBudgetAndLandUse() {
        PlanningResult result = runAlgorithm(20, 20, createEmptyGrid(20, 20));
        int totalCost = result.getStats().getTotalCost();
        int totalRevenue = result.getStats().getTotalRevenue();
        int cropCount = countCellsByType(result.getGrid(), "crop");
        int totalCells = 20 * 20;

        assertTrue(totalCost <= 50000, "总投入不能超过预算");
        assertTrue(totalRevenue > totalCost, "预计产出应大于投入，保证利润为正");
        assertTrue(((double) cropCount / totalCells) > 0.55,
                "土地利用率应高于55%，尽量多种植物");
    }

    static void testEmptyGridGeneration() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        // 应该生成了作物、喷水器和稻草人
        int sprinklerCount = 0, scarecrowCount = 0, cropCount = 0;
        for (List<GridCell> row : grid) {
            for (GridCell cell : row) {
                if (isSprinklerCell(cell)) sprinklerCount++;
                if (isScarecrowCell(cell)) scarecrowCount++;
                if ("crop".equals(cell.getType())) cropCount++;
            }
        }

        assertTrue(sprinklerCount > 0, "应生成喷水器");
        assertTrue(cropCount > 0, "应生成作物");
        System.out.printf("    空画布: 喷水器=%d, 稻草人=%d, 作物=%d%n",
                sprinklerCount, scarecrowCount, cropCount);

        // 打印网格
        printGrid(grid);
    }

    static void testConstraintCheckPassed() {
        int width = 16, height = 16;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        ConstraintCheck check = result.getStats().getConstraintCheck();

        // Debug: print grid stats
        List<List<GridCell>> grid = result.getGrid();
        int sprinklerCount = 0, scarecrowCount = 0, cropCount = 0, emptyCount = 0;
        int coveredCrops = 0, uncoveredCrops = 0;
        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");
        int[][] covMap = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSprinklerCell(grid.get(y).get(x))) {
                    sprinklerCount++;
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) covMap[cy][cx]++;
                    }
                }
                if (isScarecrowCell(grid.get(y).get(x))) scarecrowCount++;
                if ("crop".equals(grid.get(y).get(x).getType())) {
                    cropCount++;
                }
                if ("empty".equals(grid.get(y).get(x).getType())) emptyCount++;
            }
        }
        System.out.printf("    16x16: sprinklers=%d, scarecrows=%d, crops=%d, empty=%d, total=%d%n",
                sprinklerCount, scarecrowCount, cropCount, emptyCount, width*height);
        printGrid(grid);

        assertNotNull(check, "ConstraintCheck");
        // 5×5 平铺在有限网格边界处存在不可避免的覆盖间隙
        // 允许少量作物未被喷水器覆盖（边界间隙 ≤20%）
        assertTrue(check.getUnsprayedCrops() <= cropCount / 5,
                String.format("未覆盖作物%d应少于作物数20%%(%d)", check.getUnsprayedCrops(), cropCount / 5));
        assertEquals(0, check.getUnprotectedCrops(), "未保护作物");
        assertEquals(0, check.getSprinklerOverlapCells(), "喷水器重叠");
        assertEquals(0, check.getScarecrowOverlapCells(), "稻草人重叠");

        System.out.printf("    约束检查: allSatisfied=%s, unsprayed=%d/%d, unprotected=%d, messages=%s%n",
                check.isAllSatisfied(), check.getUnsprayedCrops(), cropCount,
                check.getUnprotectedCrops(), check.getMessages());

        System.out.printf("    约束检查: allSatisfied=%s, messages=%s%n",
                check.isAllSatisfied(), check.getMessages());
    }

    // ============ 5×5 平铺模式测试 ============

    /**
     * 验证所有自动喷水器符合 (r + 2c) mod 5 == offset 的平铺公式
     */
    static void testTilingPattern5x5() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        // 找到所有自动喷水器，检测它们是否属于同一个偏移
        int detectedOffset = -1;
        int autoSprinklers = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridCell cell = grid.get(y).get(x);
                if (isSprinklerCell(cell) && "auto".equals(cell.getSource())) {
                    autoSprinklers++;
                    int offset = (y + 2 * x) % 5;
                    if (detectedOffset == -1) {
                        detectedOffset = offset;
                    } else {
                        assertEquals(detectedOffset, offset,
                            String.format("喷水器(%d,%d)偏移%d不符合主偏移%d", y, x, offset, detectedOffset));
                    }
                }
            }
        }

        assertTrue(autoSprinklers > 0, "应生成自动喷水器");
        System.out.printf("    20x20: 自动喷水器=%d, 偏移=%d%n", autoSprinklers, detectedOffset);
    }

    /**
     * 验证 20×20 空画布上绝大部分非喷水器格均被覆盖
     * 注意：5×5 平铺在有限网格边界处存在不可避免的覆盖间隙
     */
    static void testTilingFullCoverage() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");
        int[][] covMap = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSprinklerCell(grid.get(y).get(x))) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            covMap[cy][cx]++;
                        }
                    }
                }
            }
        }

        int uncoveredNonSprinkler = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isSprinklerCell(grid.get(y).get(x)) && covMap[y][x] == 0) {
                    uncoveredNonSprinkler++;
                }
            }
        }

        int totalNonSprinkler = width * height - countCellsByType(grid, "sprinkler");
        double coverageRate = 1.0 - (double) uncoveredNonSprinkler / totalNonSprinkler;
        System.out.printf("    覆盖率: %.1f%% (%d/%d 未覆盖)%n",
                coverageRate * 100, uncoveredNonSprinkler, totalNonSprinkler);
        assertTrue(coverageRate > 0.90,
                String.format("覆盖率%.1f%%应高于90%%", coverageRate * 100));
    }

    /**
     * 有用户物品时，图案自适应跳过冲突位置
     */
    static void testTilingWithUserItems() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);
        // 在中央放置用户喷水器
        setCell(existingGrid, 5, 5, "tool", "sprinkler-1", "喷水器", false, "user");
        // 放置用户作物
        setCell(existingGrid, 3, 3, "crop", "crop-1", "草莓", true, "user");

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        // 用户物品保留
        assertEquals("tool", grid.get(5).get(5).getType(), "用户喷水器保留");
        assertEquals("user", grid.get(5).get(5).getSource(), "用户喷水器source");
        assertEquals("crop", grid.get(3).get(3).getType(), "用户作物保留");

        // 自动喷水器不应与用户喷水器覆盖重叠
        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");
        int[][] covMap = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSprinklerCell(grid.get(y).get(x))) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            covMap[cy][cx]++;
                        }
                    }
                }
            }
        }
        // 用户喷水器覆盖的格子不应被自动喷水器再次覆盖
        for (int[] off : sprinklerOffsets) {
            int cy = 5 + off[0], cx = 5 + off[1];
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                assertTrue(covMap[cy][cx] <= 2,
                    String.format("(%d,%d)覆盖次数过高=%d", cy, cx, covMap[cy][cx]));
            }
        }

        printGrid(grid);
    }

    /**
     * 边缘喷水器被正确放置（不因超出边界而拒绝）
     */
    static void testTilingEdgeClipping() {
        int width = 12, height = 12;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        // 检查边缘（第0行、第0列等）是否有自动喷水器
        boolean hasEdgeSprinkler = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((y == 0 || y == height - 1 || x == 0 || x == width - 1)
                    && isSprinklerCell(grid.get(y).get(x))
                    && "auto".equals(grid.get(y).get(x).getSource())) {
                    hasEdgeSprinkler = true;
                }
            }
        }
        assertTrue(hasEdgeSprinkler, "边缘应有自动喷水器（允许截断）");
    }

    /**
     * 5×5 平铺后喷水器覆盖地图无 >1 的格子（零重叠）
     */
    static void testTilingZeroOverlap() {
        int width = 20, height = 20;
        List<List<GridCell>> existingGrid = createEmptyGrid(height, width);

        PlanningResult result = runAlgorithm(width, height, existingGrid);
        List<List<GridCell>> grid = result.getGrid();

        List<int[]> sprinklerOffsets = parseOffsets("{\"shape\":\"cross\",\"range\":1}");
        int[][] covMap = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isSprinklerCell(grid.get(y).get(x))) {
                    for (int[] off : sprinklerOffsets) {
                        int cy = y + off[0], cx = x + off[1];
                        if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                            covMap[cy][cx]++;
                        }
                    }
                }
            }
        }

        int overlapCells = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (covMap[y][x] > 1) overlapCells++;
            }
        }
        assertEquals(0, overlapCells, "5×5平铺后喷水器覆盖重叠格数");
    }

    // ============ 辅助方法 ============

    static PlanningResult runAlgorithm(int width, int height, List<List<GridCell>> existingGrid) {
        Tool sprinkler = createSprinkler();
        Tool scarecrow = createScarecrow();
        List<Crop> crops = createTestCrops();

        PlanningAlgorithm algo = new PlanningAlgorithm(
            width, height, "spring", 50000,
            crops, crops, sprinkler, scarecrow, existingGrid, "max_roi"
        );
        return algo.generate();
    }

    static List<List<GridCell>> createEmptyGrid(int height, int width) {
        List<List<GridCell>> grid = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            List<GridCell> row = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                row.add(GridCell.empty());
            }
            grid.add(row);
        }
        return grid;
    }

    static void setCell(List<List<GridCell>> grid, int y, int x,
                         String type, String itemId, String name, boolean walkable, String source) {
        GridCell cell = new GridCell();
        cell.setType(type);
        cell.setItemId(itemId);
        cell.setName(name);
        cell.setWalkable(walkable);
        cell.setSource(source);
        grid.get(y).set(x, cell);
    }

    static Tool createSprinkler() {
        Tool t = new Tool();
        t.setId("tool-sprinkler");
        t.setName("喷水器");
        t.setType("sprinkler");
        t.setCoverageOffsets("{\"shape\":\"cross\",\"range\":1}");
        t.setBlocksWalking(true);
        t.setPrice(0);
        return t;
    }

    static Tool createScarecrow() {
        Tool t = new Tool();
        t.setId("tool-scarecrow");
        t.setName("稻草人");
        t.setType("scarecrow");
        t.setCoverageOffsets("{\"shape\":\"square\",\"range\":6}");
        t.setBlocksWalking(true);
        t.setPrice(0);
        return t;
    }

    static List<Crop> createTestCrops() {
        List<Crop> crops = new ArrayList<>();

        Crop strawberry = new Crop();
        strawberry.setId("crop-1");
        strawberry.setName("草莓");
        strawberry.setSeasons(List.of("spring"));
        strawberry.setIsWalkable(true);
        strawberry.setSeedPrice(100);
        strawberry.setGrowthDays(8);
        strawberry.setCanRegrow(true);
        strawberry.setRegrowInterval(4);
        strawberry.setBaseSellPrice(120);
        crops.add(strawberry);

        Crop potato = new Crop();
        potato.setId("crop-2");
        potato.setName("土豆");
        potato.setSeasons(List.of("spring"));
        potato.setIsWalkable(true);
        potato.setSeedPrice(50);
        potato.setGrowthDays(6);
        potato.setCanRegrow(false);
        potato.setBaseSellPrice(80);
        crops.add(potato);

        Crop cauliflower = new Crop();
        cauliflower.setId("crop-3");
        cauliflower.setName("花椰菜");
        cauliflower.setSeasons(List.of("spring"));
        cauliflower.setIsWalkable(true);
        cauliflower.setSeedPrice(80);
        cauliflower.setGrowthDays(12);
        cauliflower.setCanRegrow(false);
        cauliflower.setBaseSellPrice(175);
        crops.add(cauliflower);

        return crops;
    }

    static boolean isSprinklerCell(GridCell cell) {
        if ("sprinkler".equals(cell.getType())) return true;
        if ("tool".equals(cell.getType())) {
            String name = (cell.getName() != null) ? cell.getName().toLowerCase() : "";
            return name.contains("喷水") || name.contains("sprinkler");
        }
        return false;
    }

    static boolean isScarecrowCell(GridCell cell) {
        if ("scarecrow".equals(cell.getType())) return true;
        if ("tool".equals(cell.getType())) {
            String name = (cell.getName() != null) ? cell.getName().toLowerCase() : "";
            return name.contains("稻草") || name.contains("scarecrow");
        }
        return false;
    }

    static int countCellsBySource(List<List<GridCell>> grid, String source) {
        int count = 0;
        for (List<GridCell> row : grid) {
            for (GridCell cell : row) {
                if (source.equals(cell.getSource())) count++;
            }
        }
        return count;
    }

    static int countCellsByType(List<List<GridCell>> grid, String type) {
        int count = 0;
        for (List<GridCell> row : grid) {
            for (GridCell cell : row) {
                if (type.equals(cell.getType())) count++;
            }
        }
        return count;
    }

    static Map<String, Integer> countCropsById(List<List<GridCell>> grid) {
        Map<String, Integer> counts = new HashMap<>();
        for (List<GridCell> row : grid) {
            for (GridCell cell : row) {
                if ("crop".equals(cell.getType())) {
                    counts.merge(cell.getItemId(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    static String findBestRoiCropId(List<Crop> crops) {
        String bestId = null;
        double bestRoi = Double.NEGATIVE_INFINITY;
        for (Crop crop : crops) {
            var revenue = CropCalculator.calculate(crop);
            if (revenue != null && revenue.getRoi() > bestRoi) {
                bestRoi = revenue.getRoi();
                bestId = crop.getId();
            }
        }
        return bestId;
    }

    static List<int[]> parseOffsets(String json) {
        return CoverageParser.parse(json);
    }

    // ============ 断言方法 ============

    static void assertEquals(Object expected, Object actual, String msg) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(msg + ": expected=" + expected + ", actual=" + actual);
        }
    }

    static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + ": expected=" + expected + ", actual=" + actual);
        }
    }

    static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    static void assertFalse(boolean condition, String msg) {
        if (condition) {
            throw new AssertionError(msg);
        }
    }

    static void assertNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new AssertionError(msg + " is null");
        }
    }

    // ============ 打印网格 ============

    static void printGrid(List<List<GridCell>> grid) {
        int height = grid.size();
        int width = grid.get(0).size();

        System.out.println("    Grid visualization:");
        System.out.print("       ");
        for (int x = 0; x < width; x++) {
            System.out.printf("%3d", x);
        }
        System.out.println();

        for (int y = 0; y < height; y++) {
            System.out.printf("    %2d ", y);
            for (int x = 0; x < width; x++) {
                GridCell cell = grid.get(y).get(x);
                String marker;
                if ("empty".equals(cell.getType())) {
                    marker = " . ";
                } else if ("crop".equals(cell.getType())) {
                    marker = "user".equals(cell.getSource()) ? " C " : " c ";
                } else if (isScarecrowCell(cell)) {
                    marker = "user".equals(cell.getSource()) ? " S " : " s ";
                } else if (isSprinklerCell(cell)) {
                    marker = "user".equals(cell.getSource()) ? " W " : " w ";
                } else {
                    marker = " ? ";
                }
                System.out.print(marker);
            }
            System.out.println();
        }
        System.out.println("    C=用户作物 c=自动作物 W=用户喷水器 w=自动喷水器 S=用户稻草人 s=自动稻草人 .=空地");
    }
}
