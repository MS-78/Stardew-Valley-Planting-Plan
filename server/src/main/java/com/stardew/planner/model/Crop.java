package com.stardew.planner.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "crops", autoResultMap = true)
public class Crop {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;

    @TableField(typeHandler = SeasonsTypeHandler.class)
    private List<String> seasons;

    private Boolean isWalkable;
    private String seedSource;
    private Integer seedPrice;
    private Integer growthDays;
    private Boolean canRegrow;
    private Integer regrowInterval;
    private Integer baseSellPrice;
    private Integer artisanSellPrice;
    private Integer silverPrice;
    private Integer goldPrice;
    private Integer iridiumPrice;
    private Integer baseEnergy;
    private Integer baseHealth;
    private Integer silverEnergy;
    private Integer silverHealth;
    private Integer goldEnergy;
    private Integer goldHealth;
    private Integer iridiumEnergy;
    private Integer iridiumHealth;
    private BigDecimal farmerMult;
    private BigDecimal agriMult;
    private String icon;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
