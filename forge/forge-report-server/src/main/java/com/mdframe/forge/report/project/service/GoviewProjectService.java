package com.mdframe.forge.report.project.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.report.project.domain.GoviewProject;
import com.mdframe.forge.report.project.mapper.GoviewProjectMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Go-View 项目 Service
 */
@Service
public class GoviewProjectService extends ServiceImpl<GoviewProjectMapper, GoviewProject> {

    /**
     * 更新项目配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(GoviewProject project) {
        if (project == null || project.getId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        GoviewProject exists = getById(project.getId());
        if (exists == null) {
            throw new BusinessException("项目不存在");
        }
        exists.setProjectName(project.getProjectName());
        exists.setRemark(project.getRemark());
        exists.setIndexImg(project.getIndexImg());
        exists.setStatus(project.getStatus());
        exists.setCanvasWidth(project.getCanvasWidth());
        exists.setCanvasHeight(project.getCanvasHeight());
        exists.setBackgroundColor(project.getBackgroundColor());
        exists.setComponentData(project.getComponentData());

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
        GoviewProject project = getById(id);
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
