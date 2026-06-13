package com.stardew.planner.algorithm;

import com.stardew.planner.dto.CropRevenue;
import com.stardew.planner.model.Crop;

/**
 * 作物收益计算器
 * 严格按照PRD第7节公式实现
 */
public class CropCalculator {

    /**
     * 不可重复收获作物收益计算
     * 收获后植株消失，需补种，每次"种→收"周期 = growthDays + 1 天
     *
     * 收获次数 = floor(27 / growthDays)
     * 总产出 = baseSellPrice × harvests
     * 总投入 = seedPrice × harvests（每次补种都需购买种子）
     */
    public static CropRevenue calculateNonRegrowable(int growthDays, int seedPrice, int baseSellPrice) {
        if (growthDays <= 0 || growthDays >= 28) return null;
        int harvests = (int) Math.floor(27.0 / growthDays);
        int totalRevenue = baseSellPrice * harvests;
        int totalCost = seedPrice * harvests;
        double roi = totalCost == 0 ? Double.MAX_VALUE : (double) totalRevenue / totalCost;
        return new CropRevenue(harvests, totalRevenue, totalCost, roi);
    }

    /**
     * 可重复收获作物收益计算
     * 首次成熟后每隔regrowInterval天收获一次，植株不消失无需补种
     *
     * 首次收获日 = 1 + growthDays
     * 收获次数 = floor((28 - growthDays - 1) / regrowInterval) + 1
     * 总产出 = baseSellPrice × harvests
     * 总投入 = seedPrice（仅初始种子费，无需补种）
     */
    public static CropRevenue calculateRegrowable(int growthDays, int regrowInterval,
                                                   int seedPrice, int baseSellPrice) {
        if (growthDays <= 0 || growthDays >= 28) return null;
        // 防御性处理：regrowInterval=0 降级为一次性计算
        if (regrowInterval <= 0) return calculateNonRegrowable(growthDays, seedPrice, baseSellPrice);
        int harvests = (int) Math.floor((28.0 - growthDays - 1) / regrowInterval) + 1;
        int totalRevenue = baseSellPrice * harvests;
        int totalCost = seedPrice; // 仅初始种子费用
        double roi = totalCost == 0 ? Double.MAX_VALUE : (double) totalRevenue / totalCost;
        return new CropRevenue(harvests, totalRevenue, totalCost, roi);
    }

    /**
     * 统一计算入口：根据作物是否可重复收获选择对应公式
     */
    public static CropRevenue calculate(Crop crop) {
        if (crop.getCanRegrow()) {
            return calculateRegrowable(crop.getGrowthDays(), crop.getRegrowInterval(),
                    crop.getSeedPrice(), crop.getBaseSellPrice());
        } else {
            return calculateNonRegrowable(crop.getGrowthDays(), crop.getSeedPrice(),
                    crop.getBaseSellPrice());
        }
    }
}
