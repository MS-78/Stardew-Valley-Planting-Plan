package com.stardew.planner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stardew.planner.model.Tool;
import com.stardew.planner.repository.ToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolMapper toolMapper;

    public List<Tool> list(String keyword) {
        LambdaQueryWrapper<Tool> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Tool::getName, keyword);
        }
        wrapper.orderByAsc(Tool::getName);
        return toolMapper.selectList(wrapper);
    }

    public Tool getById(String id) {
        return toolMapper.selectById(id);
    }

    public Tool create(Tool tool) {
        toolMapper.insert(tool);
        return tool;
    }

    public Tool update(String id, Tool tool) {
        tool.setId(id);
        toolMapper.updateById(tool);
        return toolMapper.selectById(id);
    }

    public void delete(String id) {
        toolMapper.deleteById(id);
    }
}
