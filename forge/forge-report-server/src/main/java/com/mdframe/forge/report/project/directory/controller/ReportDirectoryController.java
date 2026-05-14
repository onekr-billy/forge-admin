package com.mdframe.forge.report.project.directory.controller;

import com.mdframe.forge.report.project.directory.domain.ReportDirectory;
import com.mdframe.forge.report.project.directory.dto.ReportDirectoryMoveDTO;
import com.mdframe.forge.report.project.directory.service.ReportDirectoryService;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报表目录 Controller
 */
@RestController
@RequestMapping("/report/directory")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
public class ReportDirectoryController {

    private final ReportDirectoryService directoryService;

    /**
     * 查询目录树
     */
    @GetMapping("/tree")
    public RespInfo<List<ReportDirectory>> tree() {
        return RespInfo.success(directoryService.listTree());
    }

    /**
     * 查询目录详情
     */
    @GetMapping("/{id}")
    public RespInfo<ReportDirectory> getById(@PathVariable Long id) {
        return RespInfo.success(directoryService.getById(id));
    }

    /**
     * 创建目录
     */
    @PostMapping
    public RespInfo<ReportDirectory> create(@RequestBody ReportDirectory directory) {
        return RespInfo.success(directoryService.createDirectory(directory));
    }

    /**
     * 更新目录
     */
    @PutMapping
    public RespInfo<Void> update(@RequestBody ReportDirectory directory) {
        directoryService.updateDirectory(directory);
        return RespInfo.success();
    }

    /**
     * 移动目录
     */
    @PutMapping("/move")
    public RespInfo<Void> move(@RequestBody ReportDirectoryMoveDTO moveDTO) {
        directoryService.moveDirectory(moveDTO);
        return RespInfo.success();
    }

    /**
     * 删除目录
     */
    @DeleteMapping("/{id}")
    public RespInfo<Void> delete(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return RespInfo.success();
    }
}
