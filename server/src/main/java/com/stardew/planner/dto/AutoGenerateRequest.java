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
    private List<List<GridCell>> existingGrid;

    /** 算法模式: "max_roi"(默认), "weighted_balanced", "fully_balanced" */
    private String mode = "max_roi";
}
