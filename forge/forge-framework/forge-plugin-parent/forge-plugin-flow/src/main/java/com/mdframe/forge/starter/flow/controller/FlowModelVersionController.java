package com.mdframe.forge.starter.flow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.flow.dto.VersionCompareDTO;
import com.mdframe.forge.starter.flow.dto.VersionRevertDTO;
import com.mdframe.forge.starter.flow.dto.VersionTagUpdateDTO;
import com.mdframe.forge.starter.flow.entity.FlowModelVersion;
import com.mdframe.forge.starter.flow.service.FlowModelVersionService;
import com.mdframe.forge.starter.flow.vo.VersionCompareVO;
import com.mdframe.forge.starter.flow.vo.VersionDetailVO;
import com.mdframe.forge.starter.flow.vo.VersionRevertVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/flow/model/version")
@RequiredArgsConstructor
public class FlowModelVersionController {

    private final FlowModelVersionService flowModelVersionService;

    @GetMapping("/list")
    public RespInfo<IPage<FlowModelVersion>> pageVersionList(
            @RequestParam String modelId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<FlowModelVersion> page = new Page<>(pageNum, pageSize);
        return RespInfo.success(flowModelVersionService.pageVersionList(page, modelId));
    }

    @GetMapping("/{versionId}")
    public RespInfo<VersionDetailVO> getVersionDetail(@PathVariable String versionId) {
        return RespInfo.success(flowModelVersionService.getVersionDetail(versionId));
    }

    @PostMapping("/compare")
    public RespInfo<VersionCompareVO> compareVersions(@RequestBody VersionCompareDTO dto) {
        return RespInfo.success(flowModelVersionService.compareVersions(dto));
    }

    @PostMapping("/revert")
    public RespInfo<VersionRevertVO> revertVersion(@RequestBody VersionRevertDTO dto) {
        return RespInfo.success(flowModelVersionService.revertVersion(dto));
    }

    @PutMapping("/{versionId}/tag")
    public RespInfo<Void> updateVersionTag(@PathVariable String versionId, @RequestBody VersionTagUpdateDTO dto) {
        flowModelVersionService.updateVersionTag(versionId, dto.getVersionTag());
        return RespInfo.success();
    }

    @DeleteMapping("/{versionId}")
    public RespInfo<Void> deleteVersion(@PathVariable String versionId) {
        flowModelVersionService.deleteVersion(versionId);
        return RespInfo.success();
    }

    @GetMapping("/download/{versionId}")
    public void downloadVersion(@PathVariable String versionId, HttpServletResponse response) {
        try {
            VersionDetailVO version = flowModelVersionService.getVersionDetail(versionId);

            response.setContentType("application/xml");
            response.setHeader("Content-Disposition", "attachment;filename=" + version.getVersionName() + ".bpmn20.xml");

            OutputStream out = response.getOutputStream();
            out.write(version.getBpmnXml().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        } catch (Exception e) {
            throw new RuntimeException("下载失败：" + e.getMessage());
        }
    }
}