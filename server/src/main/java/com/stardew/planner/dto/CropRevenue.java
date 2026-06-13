package com.stardew.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropRevenue {
    private int harvestCount;
    private int totalRevenue;
    private int totalCost;
    private double roi;
}
