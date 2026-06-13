package com.stardew.planner.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("categories")
public class Category {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String type;
    private String season;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
