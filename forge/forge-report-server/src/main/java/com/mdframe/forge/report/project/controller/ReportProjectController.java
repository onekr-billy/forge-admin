package com.mdframe.forge.report.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.report.project.domain.ReportProject;
import com.mdframe.forge.report.project.service.ReportProjectService;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Go-View 项目 Controller
 */
@RestController
@RequestMapping("/report/project")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
public class ReportProjectController {

    private final ReportProjectService projectService;

    /**
     * 分页查询项目列表
     */
    @GetMapping("/page")
    public RespInfo<Page<ReportProject>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long directoryId) {
        return RespInfo.success(projectService.pageProjects(pageNum, pageSize, projectName, directoryId));
    }

    /**
     * 查询项目详情
     */
    @GetMapping("/{id}")
    public RespInfo<ReportProject> getById(@PathVariable Long id) {
        ReportProject project = projectService.getById(id);
        return RespInfo.success(project);
    }

    /**
     * 创建项目
     */
    @PostMapping
    public RespInfo<ReportProject> create(@RequestBody ReportProject project) {
        return RespInfo.success(projectService.createProject(project));
    }

    /**
     * 更新项目
     */
    @PutMapping
    public RespInfo<Void> update(@RequestBody ReportProject project) {
        projectService.updateProject(project);
        return RespInfo.success();
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public RespInfo<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return RespInfo.success();
    }

    /**
     * 发布项目
     */
    @PostMapping("/publish/{id}")
    public RespInfo<Void> publish(@PathVariable Long id, @RequestParam String publishUrl) {
        projectService.publishProject(id, publishUrl);
        return RespInfo.success();
    }
}
