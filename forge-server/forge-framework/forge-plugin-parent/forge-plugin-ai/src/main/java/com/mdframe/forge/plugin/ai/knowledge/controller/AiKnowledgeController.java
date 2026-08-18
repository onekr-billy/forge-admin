package com.mdframe.forge.plugin.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeDocument;
import com.mdframe.forge.plugin.ai.knowledge.service.AiKnowledgeService;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.DocumentProcessEvent;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.DocumentUploadRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 知识库管理接口
 */
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final AiKnowledgeService knowledgeService;

    // ===== 知识库 CRUD =====

    /**
     * 分页查询知识库
     */
    @GetMapping("/page")
    @SaCheckPermission("ai:knowledge:list")
    public RespInfo<Page<AiKnowledge>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String knowledgeName,
            @RequestParam(required = false) String status) {
        return RespInfo.success(knowledgeService.page(pageNum, pageSize, knowledgeName, status));
    }

    /**
     * 查询知识库详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("ai:knowledge:list")
    public RespInfo<AiKnowledge> getById(@PathVariable Long id) {
        return RespInfo.success(knowledgeService.getById(id));
    }

    /**
     * 新增知识库
     */
    @PostMapping
    @SaCheckPermission("ai:knowledge:add")
    public RespInfo<AiKnowledge> create(@RequestBody AiKnowledge knowledge) {
        return RespInfo.success(knowledgeService.create(knowledge));
    }

    /**
     * 修改知识库
     */
    @PutMapping
    @SaCheckPermission("ai:knowledge:edit")
    public RespInfo<AiKnowledge> update(@RequestBody AiKnowledge knowledge) {
        return RespInfo.success(knowledgeService.update(knowledge));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:knowledge:delete")
    public RespInfo<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return RespInfo.success();
    }

    // ===== 文档管理 =====

    /**
     * 分页查询文档
     */
    @GetMapping("/document/page")
    @SaCheckPermission("ai:knowledge:list")
    public RespInfo<Page<AiKnowledgeDocument>> documentPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam Long knowledgeId,
            @RequestParam(required = false) String docName,
            @RequestParam(required = false) String processStatus) {
        return RespInfo.success(knowledgeService.documentPage(pageNum, pageSize, knowledgeId, docName, processStatus));
    }

    /**
     * 上传文档
     */
    @PostMapping("/document/upload")
    @SaCheckPermission("ai:knowledge:add")
    public RespInfo<AiKnowledgeDocument> uploadDocument(@RequestBody DocumentUploadRequest request) {
        return RespInfo.success(knowledgeService.uploadDocument(request));
    }

    /**
     * 确认处理文档（两步上传第二步）
     */
    @PostMapping("/document/{documentId}/confirm")
    @SaCheckPermission("ai:knowledge:edit")
    public RespInfo<Void> confirmDocument(@PathVariable Long documentId) {
        knowledgeService.confirmDocument(documentId);
        return RespInfo.success();
    }

    /**
     * 重新处理失败文档
     */
    @PostMapping("/document/{documentId}/reprocess")
    @SaCheckPermission("ai:knowledge:edit")
    public RespInfo<Void> reprocessDocument(@PathVariable Long documentId) {
        knowledgeService.reprocessDocument(documentId);
        return RespInfo.success();
    }

    /**
     * 查看文档分块列表
     */
    @GetMapping("/document/{documentId}/chunks")
    @SaCheckPermission("ai:knowledge:list")
    public RespInfo<List<AiKnowledgeChunk>> documentChunks(@PathVariable Long documentId) {
        return RespInfo.success(knowledgeService.listDocumentChunks(documentId));
    }

    /**
     * 查看文档原始内容
     */
    @GetMapping("/document/{documentId}/content")
    @SaCheckPermission("ai:knowledge:list")
    public RespInfo<String> documentContent(@PathVariable Long documentId) {
        return RespInfo.success(knowledgeService.getDocumentRawContent(documentId));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/document/{documentId}")
    @SaCheckPermission("ai:knowledge:delete")
    public RespInfo<Void> deleteDocument(@PathVariable Long documentId) {
        knowledgeService.deleteDocument(documentId);
        return RespInfo.success();
    }

    /**
     * 订阅文档处理进度（SSE）
     */
    @GetMapping(value = "/document/{documentId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DocumentProcessEvent>> subscribeProgress(@PathVariable Long documentId) {
        return knowledgeService.subscribeDocumentProgress(documentId);
    }

    // ===== 检索 =====

    /**
     * 知识库检索调试
     */
    @PostMapping("/search")
    @SaCheckPermission("ai:knowledge:search")
    public RespInfo<List<KnowledgeSearchResult>> search(@RequestBody KnowledgeSearchRequest request) {
        return RespInfo.success(knowledgeService.search(request));
    }
}
