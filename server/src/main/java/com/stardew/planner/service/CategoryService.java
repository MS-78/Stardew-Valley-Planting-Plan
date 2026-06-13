package com.stardew.planner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stardew.planner.model.Category;
import com.stardew.planner.repository.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> list(String type, String keyword) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(Category::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Category::getName, keyword);
        }
        wrapper.orderByAsc(Category::getType, Category::getName);
        return categoryMapper.selectList(wrapper);
    }

    public Category getById(String id) {
        return categoryMapper.selectById(id);
    }

    public Category create(Category category) {
        categoryMapper.insert(category);
        return category;
    }

    public Category update(String id, Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return categoryMapper.selectById(id);
    }

    public void delete(String id) {
        categoryMapper.deleteById(id);
    }
}
