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
    private int sprinklerEmptyCells;
    /** 喷水器覆盖重叠的格子数量（审计用） */
    private int sprinklerOverlapCells;
    /** 稻草人覆盖重叠的格子数量（审计用） */
    private int scarecrowOverlapCells;
    /** 提示信息列表（如 "3株作物未被喷水器覆盖"） */
    private List<String> messages;
}
