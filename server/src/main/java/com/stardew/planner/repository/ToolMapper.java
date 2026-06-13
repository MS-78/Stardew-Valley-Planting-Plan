package com.stardew.planner.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stardew.planner.model.Tool;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ToolMapper extends BaseMapper<Tool> {
}
