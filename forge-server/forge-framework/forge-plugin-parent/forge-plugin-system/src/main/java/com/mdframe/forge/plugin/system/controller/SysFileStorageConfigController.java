package com.mdframe.forge.plugin.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.system.entity.SysFileStorageConfig;
import com.mdframe.forge.plugin.system.service.ISysFileStorageConfigService;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.core.util.SensitiveDataUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件存储配置管理
 */
@RestController
@RequestMapping("/system/storage/config")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class SysFileStorageConfigController {
    
    private final ISysFileStorageConfigService storageConfigService;
    
    /**
     * 分页查询
     */
    @GetMapping("/page")
    public RespInfo<Page<SysFileStorageConfig>> page(PageQuery query, SysFileStorageConfig condition) {
        assertPlatformAdmin();
        Page<SysFileStorageConfig> page = storageConfigService.page(query, condition);
        if (page.getRecords() != null) {
            page.getRecords().forEach(this::maskCredentialFields);
        }
        return RespInfo.success(page);
    }
    
    /**
     * 详情
     */
    @PostMapping("/detail")
    public RespInfo<SysFileStorageConfig> detail(@RequestParam Long id) {
        assertPlatformAdmin();
        return RespInfo.success(maskCredentialFields(storageConfigService.getById(id)));
    }
    
    /**
     * 新增
     */
    @PostMapping
    public RespInfo<Void> add(@RequestBody SysFileStorageConfig config) {
        assertPlatformAdmin();
        storageConfigService.save(config);
        return RespInfo.success();
    }
    
    /**
     * 修改
     */
    @PutMapping
    public RespInfo<Void> edit(@RequestBody SysFileStorageConfig config) {
        assertPlatformAdmin();
        storageConfigService.updateById(config);
        return RespInfo.success();
    }
    
    /**
     * 删除
     */
    @DeleteMapping("/{ids}")
    public RespInfo<Void> remove(@PathVariable Long[] ids) {
        assertPlatformAdmin();
        for (Long id : ids) {
            storageConfigService.removeById(id);
        }
        return RespInfo.success();
    }
    
    /**
     * 设置默认配置
     */
    @PutMapping("/default/{id}")
    public RespInfo<Void> setDefault(@PathVariable Long id) {
        assertPlatformAdmin();
        storageConfigService.setDefault(id);
        return RespInfo.success();
    }
    
    /**
     * 启用/禁用
     */
    @PutMapping("/enabled/{id}/{enabled}")
    public RespInfo<Void> updateEnabled(@PathVariable Long id, @PathVariable Boolean enabled) {
        assertPlatformAdmin();
        storageConfigService.updateEnabled(id, enabled);
        return RespInfo.success();
    }
    
    /**
     * 测试连接
     */
    @PostMapping("/test/{id}")
    public RespInfo<Boolean> testConnection(@PathVariable Long id) {
        assertPlatformAdmin();
        return RespInfo.success(storageConfigService.testConnection(id));
    }

    /**
     * 获取默认存储配置（前端上传组件用）
     */
    @GetMapping("/default")
    public RespInfo<SysFileStorageConfig> getDefault() {
        return RespInfo.success(toSafeUploadConfig(storageConfigService.getDefaultConfig()));
    }

    /**
     * 获取启用的存储配置选项（文件列表上传选择用）
     */
    @GetMapping("/options")
    public RespInfo<List<SysFileStorageConfig>> options() {
        return RespInfo.success(storageConfigService.listEnabledOptions());
    }

    /**
     * 创建存储桶/本地目录
     */
    @PostMapping("/bucket/{id}")
    public RespInfo<Boolean> createBucket(@PathVariable Long id) {
        assertPlatformAdmin();
        return RespInfo.success(storageConfigService.createBucket(id));
    }

    private void assertPlatformAdmin() {
        SessionHelper.assertAdmin("只有超级管理员可以维护文件存储配置");
    }

    private SysFileStorageConfig toSafeUploadConfig(SysFileStorageConfig config) {
        if (config == null) {
            return null;
        }
        SysFileStorageConfig safe = new SysFileStorageConfig();
        safe.setId(config.getId());
        safe.setConfigName(config.getConfigName());
        safe.setStorageType(config.getStorageType());
        safe.setIsDefault(config.getIsDefault());
        safe.setEnabled(config.getEnabled());
        safe.setMaxFileSize(config.getMaxFileSize());
        safe.setAllowedTypes(config.getAllowedTypes());
        return safe;
    }

    private SysFileStorageConfig maskCredentialFields(SysFileStorageConfig config) {
        if (config == null) {
            return null;
        }
        config.setAccessKey(SensitiveDataUtil.maskSensitiveValue("accessKey", config.getAccessKey()));
        config.setSecretKey(SensitiveDataUtil.maskSensitiveValue("secretKey", config.getSecretKey()));
        return config;
    }
}
