package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动规划结果
 * POST /api/planning/{id}/auto-generate 的响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanningResult {
    /** 规划上下文ID */
    private String planningId;
    /** 二维网格布局 [height][width] */
    private List<List<GridCell>> grid;
    /** 统计数据 */
    private StatsResponse stats;
}
