package com.mdframe.forge.report.project.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.report.project.template.domain.ReportTemplate;
import com.mdframe.forge.report.project.template.dto.TemplateCopyDTO;
import com.mdframe.forge.report.project.template.service.ReportTemplateService;
import com.mdframe.forge.report.project.template.vo.TemplateCopyResultVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Go-View 模板 Controller
 */
@RestController
@RequestMapping("/report/template")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
public class ReportTemplateController {

    private final ReportTemplateService templateService;

    /**
     * 分页查询我的模板
     */
    @GetMapping("/page")
    public RespInfo<Page<ReportTemplate>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String publishStatus) {
        return RespInfo.success(templateService.pageMyTemplates(pageNum, pageSize, templateName, publishStatus));
    }

    /**
     * 分页查询模板市场
     */
    @GetMapping("/market/page")
    public RespInfo<Page<ReportTemplate>> marketPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String templateName) {
        return RespInfo.success(templateService.pageTemplateMarket(pageNum, pageSize, templateName));
    }

    /**
     * 查询模板详情
     */
    @GetMapping("/{id}")
    public RespInfo<ReportTemplate> getById(@PathVariable Long id) {
        return RespInfo.success(templateService.getTemplateDetail(id));
    }

    /**
     * 从项目创建模板
     */
    @PostMapping("/from-project")
    public RespInfo<ReportTemplate> createFromProject(@RequestBody ReportTemplate template) {
        return RespInfo.success(templateService.createTemplateFromProject(template));
    }

    /**
     * 更新模板
     */
    @PutMapping
    public RespInfo<Void> update(@RequestBody ReportTemplate template) {
        templateService.updateTemplate(template);
        return RespInfo.success();
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public RespInfo<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return RespInfo.success();
    }

    /**
     * 发布模板到市场
     */
    @PostMapping("/publish/{id}")
    public RespInfo<Void> publish(@PathVariable Long id,
                                  @RequestParam(required = false) String publishUrl) {
        templateService.publishTemplate(id, publishUrl);
        return RespInfo.success();
    }

    /**
     * 基于模板创建新项目
     */
    @PostMapping("/copy-to-project")
    public RespInfo<TemplateCopyResultVO> copyToProject(@RequestBody TemplateCopyDTO copyDTO) {
        return RespInfo.success(templateService.copyToProject(copyDTO));
    }
}
