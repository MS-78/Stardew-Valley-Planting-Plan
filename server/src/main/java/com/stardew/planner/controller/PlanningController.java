package com.stardew.planner.controller;

import com.stardew.planner.algorithm.BudgetInsufficientException;
import com.stardew.planner.dto.ApiResponse;
import com.stardew.planner.dto.AutoGenerateRequest;
import com.stardew.planner.dto.PlanningInitRequest;
import com.stardew.planner.dto.PlanningResult;
import com.stardew.planner.dto.StatsResponse;
import com.stardew.planner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 规划控制器
 * 提供种植规划的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    /**
     * 初始化规划上下文
     *
     * @param request 初始化请求（season, width, height, budget）
     * @return planningId 和上下文信息
     */
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<?>> initPlanning(@RequestBody PlanningInitRequest request) {
        try {
            // 验证参数
            if (request.getWidth() < 6 || request.getHeight() < 6) {
                Map<String, Object> details = new HashMap<>();
                details.put("currentWidth", request.getWidth());
                details.put("currentHeight", request.getHeight());
                details.put("minSize", 6);
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("GRID_TOO_SMALL", "地块尺寸不能小于6×6", details)
                );
            }

            if (request.getBudget() <= 0) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_BUDGET", "预算必须大于0", null)
                );
            }

            String planningId = planningService.initPlanning(
                request.getSeason(),
                request.getWidth(),
                request.getHeight(),
                request.getBudget()
            );

            // 返回完整的规划上下文信息
            Map<String, Object> contextInfo = planningService.getContextInfo(planningId);
            return ResponseEntity.ok(ApiResponse.success(contextInfo));

        } catch (Exception e) {
            log.error("初始化规划失败", e);
            return ResponseEntity.internalServerError().body(
                ApiResponse.error("INIT_FAILED", "初始化规划失败: " + e.getMessage(), null)
            );
        }
    }

    /**
     * 执行自动规划算法
     *
     * @param id      规划ID
     * @param request 自动生成请求（cropIds 作物ID列表）
     * @return PlanningResult 规划结果（包含网格布局和统计数据）
     */
    @PostMapping("/{id}/auto-generate")
    public ResponseEntity<ApiResponse<?>> autoGenerate(
            @PathVariable String id,
            @RequestBody AutoGenerateRequest request) {
        try {
            if (request.getCropIds() == null || request.getCropIds().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("NO_CROPS_SELECTED", "请至少选择一种作物", null)
                );
            }

            PlanningResult result = planningService.autoGenerate(id, request.getCropIds(), request.getExistingGrid(), request.getMode());

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (BudgetInsufficientException e) {
            log.warn("规划 {} 预算不足: {}", id, e.getMessage());
            Map<String, Object> details = new HashMap<>();
            details.put("reason", e.getMessage());
            return ResponseEntity.badRequest().body(
                ApiResponse.error("BUDGET_INSUFFICIENT", "预算不足", details)
            );

        } catch (IllegalArgumentException e) {
            log.warn("规划 {} 参数错误: {}", id, e.getMessage());

            // 判断是否是地块过小的错误
            if (e.getMessage().contains("GRID_TOO_SMALL")) {
                String msg = e.getMessage().replace("GRID_TOO_SMALL: ", "");
                Map<String, Object> details = new HashMap<>();
                details.put("minSize", 6);
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("GRID_TOO_SMALL", msg, details)
                );
            }

            return ResponseEntity.badRequest().body(
                ApiResponse.error("INVALID_ARGUMENT", e.getMessage(), null)
            );

        } catch (Exception e) {
            log.error("规划 {} 自动生成失败", id, e);
            return ResponseEntity.internalServerError().body(
                ApiResponse.error("GENERATE_FAILED", "自动生成失败: " + e.getMessage(), null)
            );
        }
    }

    /**
     * 获取规划统计数据
     *
     * @param id 规划ID
     * @return StatsResponse 统计数据
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<?>> getStats(@PathVariable String id) {
        try {
            StatsResponse stats = planningService.getStats(id);

            return ResponseEntity.ok(ApiResponse.success(stats));

        } catch (IllegalArgumentException e) {
            log.warn("规划上下文不存在: {}", id);
            return ResponseEntity.status(404).body(
                ApiResponse.error(404, "NOT_FOUND", "规划上下文不存在", null)
            );

        } catch (IllegalStateException e) {
            log.warn("规划 {} 无结果: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(
                ApiResponse.error("NO_RESULT", "请先执行自动生成", null)
            );

        } catch (Exception e) {
            log.error("获取规划 {} 统计数据失败", id, e);
            return ResponseEntity.internalServerError().body(
                ApiResponse.error("STATS_FAILED", "获取统计数据失败: " + e.getMessage(), null)
            );
        }
    }
}
