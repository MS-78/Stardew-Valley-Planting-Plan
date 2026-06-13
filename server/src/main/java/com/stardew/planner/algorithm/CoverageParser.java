package com.stardew.planner.algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖范围解析器
 * 将紧凑格式 {"shape":"cross","range":1} 展开为坐标偏移列表
 * 使用Jackson（Spring Boot自带），无需额外依赖
 */
public class CoverageParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将紧凑格式展开为坐标偏移列表
     * cross: 上下左右各range格 → 4×range个偏移（不含中心点）
     * square: 以自身为中心(2×range+1)²区域 → 包含中心点
     *
     * @param coverageJson 紧凑格式JSON字符串，如 {"shape":"cross","range":1}
     * @return 坐标偏移列表，每个元素为 [dx, dy]
     */
    public static List<int[]> parse(String coverageJson) {
        List<int[]> offsets = new ArrayList<>();
        try {
            JsonNode obj = MAPPER.readTree(coverageJson);
            String shape = obj.get("shape").asText();
            int range = obj.get("range").asInt();

            if ("cross".equals(shape)) {
                // 十字形：上下左右各range格（不含中心点）
                for (int i = 1; i <= range; i++) {
                    offsets.add(new int[]{0, -i});  // 上
                    offsets.add(new int[]{0, i});   // 下
                    offsets.add(new int[]{-i, 0});  // 左
                    offsets.add(new int[]{i, 0});   // 右
                }
            } else if ("square".equals(shape)) {
                // 正方形：以自身为中心(2×range+1)²区域（含中心点）
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        offsets.add(new int[]{dx, dy});
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse coverage_offsets: " + coverageJson, e);
        }
        return offsets;
    }
}
