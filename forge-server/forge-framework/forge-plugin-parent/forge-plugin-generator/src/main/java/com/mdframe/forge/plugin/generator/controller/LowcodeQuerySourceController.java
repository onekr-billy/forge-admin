package com.mdframe.forge.plugin.generator.controller;

import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceRefDTO;
import com.mdframe.forge.plugin.generator.service.lowcode.query.LowcodeQuerySourceService;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceCatalogVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceMetadataVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/lowcode/query-source")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class LowcodeQuerySourceController {

    private final LowcodeQuerySourceService querySourceService;

    @GetMapping("/catalog")
    public RespInfo<List<LowcodeQuerySourceCatalogVO>> catalog(
            @RequestParam(required = false) String keyword) {
        return RespInfo.success(querySourceService.catalog(keyword));
    }

    @PostMapping("/metadata")
    public RespInfo<LowcodeQuerySourceMetadataVO> metadata(
            @RequestBody LowcodeQuerySourceRefDTO dto) {
        return RespInfo.success(querySourceService.metadata(dto));
    }

    @PostMapping("/execute")
    public RespInfo<LowcodeQuerySourceResultVO> execute(
            @RequestBody LowcodeQuerySourceExecuteDTO dto) {
        return RespInfo.success(querySourceService.execute(dto));
    }
}
