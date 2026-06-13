package com.stardew.planner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stardew.planner.model.Crop;
import com.stardew.planner.repository.CropMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CropService {

    private final CropMapper cropMapper;

    public List<Crop> list(String season, String keyword) {
        LambdaQueryWrapper<Crop> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(season)) {
            // seasons 列存储逗号分隔字符串，用 FIND_IN_SET 匹配
            wrapper.apply("FIND_IN_SET({0}, seasons)", season);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Crop::getName, keyword);
        }
        wrapper.orderByAsc(Crop::getName);
        return cropMapper.selectList(wrapper);
    }

    public Crop getById(String id) {
        return cropMapper.selectById(id);
    }

    public Crop create(Crop crop) {
        cropMapper.insert(crop);
        return crop;
    }

    public Crop update(String id, Crop crop) {
        crop.setId(id);
        cropMapper.updateById(crop);
        return cropMapper.selectById(id);
    }

    public void delete(String id) {
        cropMapper.deleteById(id);
    }
}
