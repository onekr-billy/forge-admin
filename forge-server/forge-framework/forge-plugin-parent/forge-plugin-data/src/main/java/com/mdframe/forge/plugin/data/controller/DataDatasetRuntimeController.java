package com.mdframe.forge.plugin.data.controller;

import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.service.DataDatasetRuntimeService;
import com.mdframe.forge.plugin.data.vo.DataDatasetMetadataVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/data/dataset/runtime")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class DataDatasetRuntimeController {

    private final DataDatasetRuntimeService runtimeService;

    @PostMapping("/query")
    public RespInfo<DataDatasetQueryResultVO> query(@RequestBody DataDatasetQueryDTO dto) {
        return RespInfo.success(runtimeService.query(dto));
    }

    @GetMapping("/{id}/metadata")
    public RespInfo<DataDatasetMetadataVO> getMetadata(@PathVariable Long id) {
        return RespInfo.success(runtimeService.metadata(id));
    }
}
