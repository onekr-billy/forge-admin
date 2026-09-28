package com.mdframe.forge.starter.file.config;

import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.file.spi.StorageConfigProvider;
import com.mdframe.forge.starter.file.storage.FileStorage;
import com.mdframe.forge.starter.file.storage.impl.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 * 文件模块自动配置
 */
@Slf4j
@Configuration
@ComponentScan("com.mdframe.forge.starter.file")
@RequiredArgsConstructor
public class FileAutoConfiguration implements InitializingBean {
    
    private final FileManager fileManager;
    
    @Autowired(required = false)
    private List<FileStorage> storageList;
    
    @Autowired(required = false)
    private StorageConfigProvider configProvider;
    
    @Bean
    @ConditionalOnMissingBean
    public FileManager fileManager() {
        return new FileManager();
    }
    
    @Bean
    @ConditionalOnMissingBean(LocalFileStorage.class)
    public LocalFileStorage localFileStorage() {
        return new LocalFileStorage();
    }
    
    public void init() {
        // 注册所有存储策略
        if (storageList != null && !storageList.isEmpty()) {
            for (FileStorage storage : storageList) {
                fileManager.registerStorage(storage);
                log.info("注册文件存储策略: {}", storage.getStorageType());
            }
        }
        
        if (configProvider != null) {
            fileManager.refreshConfiguredStorages();
        }
        
        log.info("文件管理模块初始化完成");
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
