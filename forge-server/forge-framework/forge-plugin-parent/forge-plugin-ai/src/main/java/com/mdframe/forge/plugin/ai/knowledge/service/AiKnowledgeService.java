package com.mdframe.forge.plugin.ai.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeDocument;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeDocumentMapper;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeMapper;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.DocumentProcessEvent;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.DocumentUploadRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 知识库 CRUD 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final DocumentProcessService documentProcessService;
    private final KnowledgeSearchService searchService;

    // ===== 知识库 CRUD =====

    public Page<AiKnowledge> page(Integer pageNum, Integer pageSize, String knowledgeName, String status) {
        Page<AiKnowledge> page = new Page<>(pageNum, pageSize);
        return knowledgeMapper.selectKnowledgePage(page, knowledgeName, status);
    }

    public AiKnowledge getById(Long id) {
        return knowledgeMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiKnowledge create(AiKnowledge knowledge) {
        // 名称唯一性校验
        long count = knowledgeMapper.countByName(knowledge.getKnowledgeName(), null);
        if (count > 0) {
            throw new BusinessException("知识库名称已存在: " + knowledge.getKnowledgeName());
        }
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiKnowledge update(AiKnowledge knowledge) {
        AiKnowledge existing = knowledgeMapper.selectByIdForUpdate(knowledge.getId());
        if (existing == null) {
            throw new BusinessException("知识库不存在");
        }
        // 名称唯一性校验
        if (knowledge.getKnowledgeName() != null) {
            long count = knowledgeMapper.countByName(knowledge.getKnowledgeName(), knowledge.getId());
            if (count > 0) {
                throw new BusinessException("知识库名称已存在: " + knowledge.getKnowledgeName());
            }
        }
        knowledgeMapper.updateById(knowledge);
        return knowledge;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BusinessException("知识库不存在");
        }
        // 删除所有文档（级联删除分块和向量）
        List<AiKnowledgeDocument> documents = documentMapper.selectByKnowledgeId(id);
        for (AiKnowledgeDocument doc : documents) {
            documentProcessService.deleteDocument(doc.getId());
        }
        knowledgeMapper.deleteById(id);
    }

    // ===== 文档管理 =====

    public Page<AiKnowledgeDocument> documentPage(Integer pageNum, Integer pageSize, Long knowledgeId, String docName, String processStatus) {
        Page<AiKnowledgeDocument> page = new Page<>(pageNum, pageSize);
        return documentMapper.selectDocumentPage(page, knowledgeId, docName, processStatus);
    }

    public AiKnowledgeDocument uploadDocument(DocumentUploadRequest request) {
        return documentProcessService.uploadDocument(request);
    }

    public void confirmDocument(Long documentId) {
        documentProcessService.confirmAndProcess(documentId);
    }

    public void reprocessDocument(Long documentId) {
        documentProcessService.reprocessDocument(documentId);
    }

    public List<AiKnowledgeChunk> listDocumentChunks(Long documentId) {
        return documentProcessService.listChunks(documentId);
    }

    public String getDocumentRawContent(Long documentId) {
        return documentProcessService.getDocumentRawContent(documentId);
    }

    public void deleteDocument(Long documentId) {
        documentProcessService.deleteDocument(documentId);
    }

    // ===== 检索 =====

    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        return searchService.search(request);
    }

    // ===== SSE 进度 =====

    public Flux<ServerSentEvent<DocumentProcessEvent>> subscribeDocumentProgress(Long documentId) {
        return documentProcessService.subscribeProgress(documentId);
    }
}
