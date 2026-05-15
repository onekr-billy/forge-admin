package com.mdframe.forge.plugin.data.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mdframe.forge.plugin.data.dto.DataDimensionItemDTO;
import com.mdframe.forge.plugin.data.dto.DataDimensionSaveDTO;
import com.mdframe.forge.plugin.data.entity.DataDimension;
import com.mdframe.forge.plugin.data.entity.DataDimensionItem;
import com.mdframe.forge.plugin.data.service.DataDimensionService;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data/dimension")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class DataDimensionController {

    private final DataDimensionService dimensionService;

    @GetMapping("/page")
    public RespInfo<IPage<DataDimension>> page(
        @RequestParam(required = false) String dimensionName,
        @RequestParam(required = false) String sourceType,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize) {
        return RespInfo.success(dimensionService.page(dimensionName, sourceType, status, pageNum, pageSize));
    }

    @GetMapping("/list")
    public RespInfo<List<DataDimension>> list() {
        return RespInfo.success(dimensionService.listEnabled());
    }

    @GetMapping("/{id}")
    public RespInfo<DataDimension> getById(@PathVariable Long id) {
        return RespInfo.success(dimensionService.getById(id));
    }

    @PostMapping
    @OperationLog(module = "数据资产", desc = "新增维度：{{#dto.dimensionName}}")
    public RespInfo<DataDimension> add(@Validated @RequestBody DataDimensionSaveDTO dto) {
        return RespInfo.success(dimensionService.saveDimension(dto));
    }

    @PutMapping
    @OperationLog(module = "数据资产", desc = "修改维度：{{#dto.dimensionName}}")
    public RespInfo<DataDimension> edit(@Validated @RequestBody DataDimensionSaveDTO dto) {
        return RespInfo.success(dimensionService.updateDimension(dto));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "数据资产", desc = "删除维度")
    public RespInfo<Void> remove(@PathVariable Long id) {
        dimensionService.deleteDimension(id);
        return RespInfo.success();
    }

    @GetMapping("/{id}/items")
    public RespInfo<List<DataDimensionItem>> items(@PathVariable Long id) {
        return RespInfo.success(dimensionService.listItems(id));
    }

    @PutMapping("/{id}/items")
    @OperationLog(module = "数据资产", desc = "保存维度值")
    public RespInfo<Void> saveItems(@PathVariable Long id, @RequestBody List<DataDimensionItemDTO> items) {
        dimensionService.saveItems(id, items);
        return RespInfo.success();
    }

    @PostMapping("/{id}/sync")
    @OperationLog(module = "数据资产", desc = "同步维度值")
    public RespInfo<List<DataDimensionItem>> sync(@PathVariable Long id) {
        return RespInfo.success(dimensionService.syncItems(id));
    }
}
