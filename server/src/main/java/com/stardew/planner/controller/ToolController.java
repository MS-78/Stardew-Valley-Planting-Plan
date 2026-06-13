package com.stardew.planner.controller;

import com.stardew.planner.dto.ApiResponse;
import com.stardew.planner.model.Tool;
import com.stardew.planner.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    @GetMapping
    public ApiResponse<List<Tool>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(toolService.list(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Tool>> getById(@PathVariable String id) {
        Tool tool = toolService.getById(id);
        if (tool == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "工具不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(tool));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Tool> create(@RequestBody Tool tool) {
        return ApiResponse.created(toolService.create(tool));
    }

    @PutMapping("/{id}")
    public ApiResponse<Tool> update(@PathVariable String id, @RequestBody Tool tool) {
        return ApiResponse.success(toolService.update(id, tool));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        toolService.delete(id);
        return ApiResponse.success("工具已删除");
    }
}
