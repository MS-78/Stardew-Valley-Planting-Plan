package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规划初始化请求
 * POST /api/planning/init
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanningInitRequest {
    /** 季节: spring/summer/fall */
    private String season;
    /** 地块宽度（格子数，>0） */
    private int width;
    /** 地块高度（格子数，>0） */
    private int height;
    /** 种植预算（金币G，>0） */
    private int budget;
}
