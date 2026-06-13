package com.stardew.planner.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stardew.planner.model.Crop;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CropMapper extends BaseMapper<Crop> {
}
