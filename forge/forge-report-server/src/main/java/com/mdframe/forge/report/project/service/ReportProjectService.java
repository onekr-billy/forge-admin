package com.mdframe.forge.report.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.report.project.directory.service.ReportDirectoryService;
import com.mdframe.forge.report.project.domain.ReportProject;
import com.mdframe.forge.report.project.mapper.ReportProjectMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Go-View 项目 Service
 */
@Service
@RequiredArgsConstructor
public class ReportProjectService extends ServiceImpl<ReportProjectMapper, ReportProject> {

    private final ReportDirectoryService directoryService;

    /**
     * 分页查询项目
     */
    public Page<ReportProject> pageProjects(Integer pageNum, Integer pageSize, String projectName, Long directoryId) {
        Page<ReportProject> page = new Page<>(pageNum, pageSize);
        List<Long> directoryIds = null;
        if (directoryId != null && directoryId > 0) {
            directoryIds = directoryService.selectDirectoryAndChildrenIds(directoryId);
            if (directoryIds.isEmpty()) {
                page.setRecords(Collections.emptyList());
                page.setTotal(0);
                return page;
            }
        }
        String keyword = StringUtils.hasText(projectName) ? projectName.trim() : null;
        return baseMapper.selectProjectPage(page, keyword, directoryIds);
    }

    /**
     * 创建项目
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportProject createProject(ReportProject project) {
        if (project == null) {
            throw new BusinessException("项目参数不能为空");
        }
        directoryService.validateDirectoryExists(project.getDirectoryId());
        if (!StringUtils.hasText(project.getProjectName())) {
            project.setProjectName("新项目");
        }
        if (!StringUtils.hasText(project.getStatus())) {
            project.setStatus("0");
        }
        if (!StringUtils.hasText(project.getPublishStatus())) {
            project.setPublishStatus("0");
        }
        save(project);
        return project;
    }

    /**
     * 更新项目配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(ReportProject project) {
        if (project == null || project.getId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        ReportProject exists = getById(project.getId());
        if (exists == null) {
            throw new BusinessException("项目不存在");
        }
        if (project.getProjectName() != null) {
            exists.setProjectName(project.getProjectName());
        }
        if (project.getRemark() != null) {
            exists.setRemark(project.getRemark());
        }
        if (project.getIndexImg() != null) {
            exists.setIndexImg(project.getIndexImg());
        }
        if (project.getStatus() != null) {
            exists.setStatus(project.getStatus());
        }
        if (project.getDirectoryId() != null && project.getDirectoryId() > 0) {
            directoryService.validateDirectoryExists(project.getDirectoryId());
            exists.setDirectoryId(project.getDirectoryId());
        }
        if (project.getCanvasWidth() != null) {
            exists.setCanvasWidth(project.getCanvasWidth());
        }
        if (project.getCanvasHeight() != null) {
            exists.setCanvasHeight(project.getCanvasHeight());
        }
        if (project.getBackgroundColor() != null) {
            exists.setBackgroundColor(project.getBackgroundColor());
        }
        if (project.getComponentData() != null) {
            exists.setComponentData(project.getComponentData());
        }

        boolean updated = updateById(exists);
        if (!updated) {
            throw new BusinessException("项目配置保存失败，请检查租户或项目状态");
        }
    }

    /**
     * 发布项目
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishProject(Long id, String publishUrl) {
        ReportProject project = getById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        project.setPublishStatus("1");
        project.setPublishUrl(publishUrl);
        project.setPublishTime(new Date());
        boolean updated = updateById(project);
        if (!updated) {
            throw new BusinessException("项目发布状态保存失败，请检查租户或项目状态");
        }
    }
}
