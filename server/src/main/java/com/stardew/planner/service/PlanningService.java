package com.stardew.planner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stardew.planner.algorithm.BudgetInsufficientException;
import com.stardew.planner.algorithm.PlanningAlgorithm;
import com.stardew.planner.dto.PlanningResult;
import com.stardew.planner.dto.StatsResponse;
import com.stardew.planner.model.Crop;
import com.stardew.planner.model.Tool;
import com.stardew.planner.repository.CropMapper;
import com.stardew.planner.repository.ToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 规划服务
 * 管理规划上下文，协调算法执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final CropMapper cropMapper;
    private final ToolMapper toolMapper;

    // 内存存储规划上下文（生产环境应考虑 Redis 或数据库）
    private final Map<String, PlanningContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 初始化规划上下文
     *
     * @param season 季节 (spring/summer/fall)
     * @param width  地块宽度
     * @param height 地块高度
     * @param budget 预算
     * @return planningId
     */
    public String initPlanning(String season, int width, int height, int budget) {
        String planningId = UUID.randomUUID().toString();

        PlanningContext context = new PlanningContext();
        context.planningId = planningId;
        context.season = season;
        context.width = width;
        context.height = height;
        context.budget = budget;

        contextMap.put(planningId, context);

        log.info("Planning context initialized: id={}, season={}, size={}x{}, budget={}",
                planningId, season, width, height, budget);

        return planningId;
    }

    /**
     * 执行自动规划算法
     *
     * @param planningId 规划ID
     * @param cropIds    用户选择的作物ID列表
     * @return PlanningResult 规划结果
     * @throws BudgetInsufficientException 预算不足时抛出
     * @throws IllegalArgumentException    参数无效时抛出
     */
    public PlanningResult autoGenerate(String planningId, List<String> cropIds) {
        return autoGenerate(planningId, cropIds, null, "max_roi");
    }

    /**
     * 执行自动规划算法（支持增量模式）
     *
     * @param planningId   规划ID
     * @param cropIds      用户选择的作物ID列表
     * @param existingGrid 用户已有的画布布局（增量模式），为null则全新生成
     * @param mode         算法模式: "max_roi", "weighted_balanced", "fully_balanced"
     * @return PlanningResult 规划结果
     * @throws BudgetInsufficientException 预算不足时抛出
     * @throws IllegalArgumentException    参数无效时抛出
     */
    public PlanningResult autoGenerate(String planningId, List<String> cropIds,
                                       List<List<com.stardew.planner.dto.GridCell>> existingGrid,
                                       String mode) {
        PlanningContext context = contextMap.get(planningId);
        if (context == null) {
            throw new IllegalArgumentException("Planning context not found: " + planningId);
        }

        // 日志：记录接收到的现有画布
        if (existingGrid != null && !existingGrid.isEmpty()) {
            int userCellCount = 0;
            int totalNonEmpty = 0;
            for (int y = 0; y < existingGrid.size(); y++) {
                List<com.stardew.planner.dto.GridCell> row = existingGrid.get(y);
                for (int x = 0; x < row.size(); x++) {
                    com.stardew.planner.dto.GridCell cell = row.get(x);
                    if (cell != null && !"empty".equals(cell.getType())) {
                        totalNonEmpty++;
                        if ("user".equals(cell.getSource())) {
                            userCellCount++;
                            log.info("[autoGenerate] 用户格子: y={}, x={}, type={}, itemId={}, name={}, source={}",
                                    y, x, cell.getType(), cell.getItemId(), cell.getName(), cell.getSource());
                        }
                    }
                }
            }
            log.info("[autoGenerate] 接收到现有画布: {}行, 非空格子={}, 用户格子={}", existingGrid.size(), totalNonEmpty, userCellCount);
        } else {
            log.info("[autoGenerate] 无现有画布（全新生成模式）");
        }

        // 从数据库查询当季所有作物（seasons 列为逗号分隔，用 FIND_IN_SET 匹配）
        List<Crop> allSeasonCrops = cropMapper.selectList(
            new LambdaQueryWrapper<Crop>()
                .apply("FIND_IN_SET({0}, seasons)", context.season)
        );

        // 查询用户选择的作物
        List<Crop> selectedCrops = cropMapper.selectBatchIds(cropIds);
        if (selectedCrops.isEmpty()) {
            throw new IllegalArgumentException("No crops selected");
        }

        // 查询工具（喷水器和稻草人）
        Tool sprinkler = null;
        Tool scarecrow = null;
        List<Tool> tools = toolMapper.selectList(null);
        for (Tool tool : tools) {
            if ("sprinkler".equals(tool.getType())) {
                sprinkler = tool;
            } else if ("scarecrow".equals(tool.getType())) {
                scarecrow = tool;
            }
        }

        if (sprinkler == null || scarecrow == null) {
            throw new IllegalStateException("Required tools not found in database");
        }

        // 调用算法
        PlanningAlgorithm algorithm = new PlanningAlgorithm(
            context.width,
            context.height,
            context.season,
            context.budget,
            selectedCrops,
            allSeasonCrops,
            sprinkler,
            scarecrow,
            existingGrid,
            mode
        );

        PlanningResult result = algorithm.generate();

        // 保存结果到上下文
        context.lastResult = result;

        log.info("Auto-generate completed: planningId={}, crops={}, grid={}x{}",
                planningId, selectedCrops.size(), context.width, context.height);

        return result;
    }

    /**
     * 获取规划统计数据
     *
     * @param planningId 规划ID
     * @return StatsResponse 统计数据
     */
    public StatsResponse getStats(String planningId) {
        PlanningContext context = contextMap.get(planningId);
        if (context == null) {
            throw new IllegalArgumentException("Planning context not found: " + planningId);
        }

        if (context.lastResult == null) {
            throw new IllegalStateException("No planning result available. Run auto-generate first.");
        }

        return context.lastResult.getStats();
    }

    /**
     * 规划上下文（内部类）
     * 存储单次规划会话的状态
     */
    private static class PlanningContext {
        String planningId;
        String season;
        int width;
        int height;
        int budget;
        PlanningResult lastResult;
    }

    /**
     * 获取规划上下文信息
     *
     * @param planningId 规划ID
     * @return 包含规划参数的Map
     */
    public Map<String, Object> getContextInfo(String planningId) {
        PlanningContext context = contextMap.get(planningId);
        if (context == null) {
            throw new IllegalArgumentException("Planning context not found: " + planningId);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("planningId", context.planningId);
        info.put("season", context.season);
        info.put("width", context.width);
        info.put("height", context.height);
        info.put("budget", context.budget);
        return info;
    }
}
