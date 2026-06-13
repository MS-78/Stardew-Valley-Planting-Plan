package com.stardew.planner.controller;

import com.stardew.planner.dto.ApiResponse;
import com.stardew.planner.model.Crop;
import com.stardew.planner.service.CropService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crops")
@RequiredArgsConstructor
public class CropController {

    private final CropService cropService;

    @GetMapping
    public ApiResponse<List<Crop>> list(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(cropService.list(season, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Crop>> getById(@PathVariable String id) {
        Crop crop = cropService.getById(id);
        if (crop == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "作物不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(crop));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Crop> create(@RequestBody Crop crop) {
        return ApiResponse.created(cropService.create(crop));
    }

    @PutMapping("/{id}")
    public ApiResponse<Crop> update(@PathVariable String id, @RequestBody Crop crop) {
        return ApiResponse.success(cropService.update(id, crop));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        cropService.delete(id);
        return ApiResponse.success("作物已删除");
    }
}
