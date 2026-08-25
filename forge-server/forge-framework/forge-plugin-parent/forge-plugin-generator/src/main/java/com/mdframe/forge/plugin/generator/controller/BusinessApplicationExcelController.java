package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationExcelImportService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationExcelImportResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationExcelPreviewVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 业务应用 Excel 建模入口；multipart 请求不经过通用请求体解密。 */
@RestController
@RequestMapping("/ai/business/application")
@RequiredArgsConstructor
public class BusinessApplicationExcelController {

    private final BusinessApplicationExcelImportService excelImportService;

    @PostMapping("/excel/preview")
    @SaCheckPermission("ai:businessApplication:add")
    @ApiEncrypt
    @OperationLog(module = "业务应用", type = OperationType.QUERY, desc = "预览 Excel 应用字段")
    public RespInfo<BusinessApplicationExcelPreviewVO> preview(
            @RequestParam("file") MultipartFile file) {
        return RespInfo.success(excelImportService.preview(file));
    }

    @PostMapping("/{id}/import-excel")
    @SaCheckPermission("ai:businessApplication:edit")
    @ApiEncrypt
    @OperationLog(module = "业务应用", type = OperationType.ADD, desc = "从 Excel 初始化业务应用")
    public RespInfo<BusinessApplicationExcelImportResultVO> initialize(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String objectName,
            @RequestParam(required = false) String objectCode,
            @RequestParam(required = false) String fields) {
        return RespInfo.success(excelImportService.initialize(
                id, file, objectName, objectCode, fields));
    }
}
