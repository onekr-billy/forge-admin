package com.mdframe.forge.plugin.ai.multimodal.image;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.model.adapter.AiModelAdapterRegistry;
import com.mdframe.forge.plugin.ai.model.domain.AiModel;
import com.mdframe.forge.plugin.ai.model.service.AiModelService;
import com.mdframe.forge.plugin.ai.multimodal.image.adapter.AiImageModelAdapter;
import com.mdframe.forge.plugin.ai.multimodal.image.domain.AiImageGenerateRecord;
import com.mdframe.forge.plugin.ai.multimodal.image.enums.AiImageGenerateStatus;
import com.mdframe.forge.plugin.ai.multimodal.image.mapper.AiImageGenerateRecordMapper;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.mapper.AiProviderMapper;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AI图片生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageGenerationService {

    private final AiImageGenerateRecordMapper recordMapper;
    private final AiModelAdapterRegistry adapterRegistry;
    private final AiModelService modelService;
    private final AiProviderMapper providerMapper;
    private final AiSecretCrypto aiSecretCrypto;
    private final FileManager fileManager;

    /**
     * 发起图片生成（异步）
     *
     * @param record 生成记录（含 prompt/size/providerId/modelId 等）
     * @return 记录ID
     */
    public Long generate(AiImageGenerateRecord record) {
        if (record.getPrompt() == null || record.getPrompt().isBlank()) {
            throw new BusinessException("提示词不能为空");
        }

        // 解析模型和供应商
        AiModel model = modelService.getById(record.getModelId());
        if (model == null) {
            throw new BusinessException("模型不存在: " + record.getModelId());
        }
        AiProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new BusinessException("供应商不存在: " + model.getProviderId());
        }

        record.setProviderId(provider.getId());
        record.setStatus(AiImageGenerateStatus.PENDING.getCode());
        recordMapper.insert(record);

        // 异步执行生成
        doGenerateAsync(record.getId(), provider, model);

        return record.getId();
    }

    /**
     * 分页查询生成记录
     */
    public Page<AiImageGenerateRecord> page(Integer pageNum, Integer pageSize, Long userId, String status) {
        Long tenantId = TenantContextHolder.getTenantId();
        return recordMapper.selectRecordPage(new Page<>(pageNum, pageSize), tenantId, userId, status);
    }

    /**
     * 获取生成结果
     */
    public AiImageGenerateRecord getResult(Long id) {
        AiImageGenerateRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("生成记录不存在: " + id);
        }
        return record;
    }

    @Async
    protected void doGenerateAsync(Long recordId, AiProvider provider, AiModel model) {
        AiImageGenerateRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            return;
        }

        try {
            record.setStatus(AiImageGenerateStatus.GENERATING.getCode());
            recordMapper.updateById(record);

            // 解密 API Key
            String apiKey = provider.getApiKey();
            if (AiSecretCrypto.isEncrypted(apiKey)) {
                apiKey = aiSecretCrypto.decrypt(apiKey);
            }

            // 调用图片生成适配器
            AiImageModelAdapter adapter = adapterRegistry.getImage(model.getModelId());
            String imageUrlOrBase64 = adapter.generate(
                    provider.getBaseUrl(), apiKey, model.getModelId(),
                    record.getPrompt(), record.getNegativePrompt(), record.getSize());

            // 下载图片并存储到文件系统
            Long fileId = storeImage(imageUrlOrBase64, record.getPrompt());

            record.setResultFileId(fileId);
            record.setStatus(AiImageGenerateStatus.SUCCESS.getCode());
            recordMapper.updateById(record);

            log.info("[AI Image] 图片生成成功, recordId={}, fileId={}", recordId, fileId);
        } catch (Exception e) {
            log.error("[AI Image] 图片生成失败, recordId={}", recordId, e);
            record.setStatus(AiImageGenerateStatus.FAILED.getCode());
            record.setErrorMsg(truncate(e.getMessage(), 1000));
            recordMapper.updateById(record);
        }
    }

    /**
     * 存储图片到文件系统
     */
    private Long storeImage(String imageUrlOrBase64, String prompt) {
        try {
            byte[] imageBytes;
            if (imageUrlOrBase64.startsWith("data:image/")) {
                // base64 格式
                String[] parts = imageUrlOrBase64.split(",", 2);
                String base64Data = parts.length > 1 ? parts[1] : parts[0];
                imageBytes = java.util.Base64.getDecoder().decode(base64Data);
            } else {
                // URL 格式：下载后存储
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrlOrBase64))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                imageBytes = response.body();
            }

            InputStream is = new ByteArrayInputStream(imageBytes);
            FileMetadata metadata = fileManager.upload(is, "ai-image.png", "image/png",
                    "ai_image_generate", null);
            return Long.parseLong(metadata.getFileId());
        } catch (Exception e) {
            throw new BusinessException("图片存储失败: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
