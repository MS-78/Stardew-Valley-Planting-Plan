package com.stardew.planner.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stardew.planner.model.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
