package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统计数据响应
 * GET /api/planning/{id}/stats
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    /** 作物品种和数量 {"防风草": 15, "土豆": 12} */
    private Map<String, Integer> cropCounts;
    /** 工具品种和数量 {"喷水器": 6, "稻草人": 2} */
    private Map<String, Integer> toolCounts;
    /** 总投入（种子费用之和，不含工具） */
    private int totalCost;
    /** 预计产出 */
    private int totalRevenue;
    /** ROI = 总产出 / 总投入 */
    private double roi;
    /** 预算剩余 = 设定预算 - 总投入 */
    private int budgetRemaining;
    /** 约束检查结果 */
    private ConstraintCheck constraintCheck;
}
