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
    private String source;

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
