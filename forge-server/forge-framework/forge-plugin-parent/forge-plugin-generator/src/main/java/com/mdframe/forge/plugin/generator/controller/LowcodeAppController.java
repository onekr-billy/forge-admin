package com.mdframe.forge.plugin.generator.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAppDraftDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeCodegenRequest;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeMoveDomainDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeCodegenService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeAppService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePublishService;
import com.mdframe.forge.plugin.generator.vo.lowcode.LowcodeAppDetailVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.LowcodeCodePreviewVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.LowcodeVersionVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 低代码 CRUD 应用接口。
 */
@Slf4j
@RestController
@RequestMapping("/ai/lowcode/app")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class LowcodeAppController {

    private final LowcodeAppService appService;
    private final LowcodePublishService publishService;
    private final LowcodeCodegenService codegenService;

    @GetMapping("/page")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "分页查询低代码应用")
    public RespInfo<Page<LowcodeAppDetailVO>> page(PageQuery pageQuery,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String publishStatus,
                                                   @RequestParam(required = false) Long domainId,
                                                   @RequestParam(required = false) String domainCode,
                                                   @RequestParam(required = false) Boolean generalDomain) {
        return RespInfo.success(appService.page(pageQuery, keyword, publishStatus, domainId, domainCode, generalDomain));
    }

    @GetMapping("/{id}")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "查询低代码应用详情")
    public RespInfo<LowcodeAppDetailVO> detail(@PathVariable Long id) {
        return RespInfo.success(appService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "低代码应用", type = OperationType.DELETE, desc = "删除低代码应用")
    public RespInfo<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return RespInfo.success();
    }

    @PostMapping("/draft")
    @OperationLog(module = "低代码应用", type = OperationType.UPDATE, desc = "保存低代码应用草稿")
    public RespInfo<Long> saveDraft(@RequestBody LowcodeAppDraftDTO dto) {
        return RespInfo.success(appService.saveDraft(dto));
    }

    @PutMapping("/{id}/move-domain")
    @OperationLog(module = "低代码应用", type = OperationType.UPDATE, desc = "迁移低代码应用业务领域")
    public RespInfo<Void> moveDomain(@PathVariable Long id, @RequestBody LowcodeMoveDomainDTO dto) {
        appService.moveDomain(id, dto);
        return RespInfo.success();
    }

    @PostMapping("/{id}/preview")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "预览低代码应用草稿")
    public RespInfo<AiCrudConfigRenderVO> preview(@PathVariable Long id,
                                                  @RequestBody(required = false) LowcodeAppDraftDTO dto) {
        return RespInfo.success(appService.preview(id, dto));
    }

    @PostMapping("/{id}/publish")
    @OperationLog(module = "低代码应用", type = OperationType.UPDATE, desc = "发布低代码应用")
    public RespInfo<Long> publish(@PathVariable Long id, @RequestBody LowcodePublishDTO dto) {
        return RespInfo.success(publishService.publish(id, dto));
    }

    @GetMapping("/{id}/versions")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "查询低代码应用版本")
    public RespInfo<List<LowcodeVersionVO>> versions(@PathVariable Long id) {
        return RespInfo.success(publishService.listVersions(id));
    }

    @PostMapping("/{id}/rollback/{versionId}")
    @OperationLog(module = "低代码应用", type = OperationType.UPDATE, desc = "回滚低代码应用版本")
    public RespInfo<Void> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        publishService.rollback(id, versionId);
        return RespInfo.success();
    }

    @GetMapping("/{id}/code/preview")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "预览低代码应用代码")
    public RespInfo<LowcodeCodePreviewVO> previewCode(@PathVariable Long id, LowcodeCodegenRequest request) {
        return RespInfo.success(codegenService.previewCode(id, request));
    }

    @GetMapping("/{id}/code/download")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "下载低代码应用代码")
    public void downloadCode(@PathVariable Long id,
                             LowcodeCodegenRequest request,
                             HttpServletResponse response) throws Exception {
        byte[] zipBytes = codegenService.downloadCode(id, request);
        String filename = appService.getDetail(id).getConfigKey() + "-code.zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(zipBytes.length);
        response.getOutputStream().write(zipBytes);
        response.getOutputStream().flush();
    }

    @GetMapping("/{id}/code/options")
    @OperationLog(module = "低代码应用", type = OperationType.QUERY, desc = "查询低代码应用代码生成配置")
    public RespInfo<Map<String, Object>> codeOptions(@PathVariable Long id) {
        return RespInfo.success(codegenService.getOptions(id));
    }

    @PutMapping("/{id}/code/options")
    @OperationLog(module = "低代码应用", type = OperationType.UPDATE, desc = "保存低代码应用代码生成配置")
    public RespInfo<Void> saveCodeOptions(@PathVariable Long id, @RequestBody LowcodeCodegenRequest request) {
        codegenService.saveOptions(id, request);
        return RespInfo.success();
    }
}
