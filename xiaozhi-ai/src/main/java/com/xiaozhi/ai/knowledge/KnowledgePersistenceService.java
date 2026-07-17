package com.xiaozhi.ai.knowledge;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.KnowledgeChunk;
import com.xiaozhi.personal.model.PersonalModels.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class KnowledgePersistenceService {

    private static final int INSERT_BATCH_SIZE = 20;
    private final PersonalFeatureMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public void save(KnowledgeDocument document, List<KnowledgeChunk> chunks, int maxChunksPerBase) {
        int existing = mapper.countKnowledgeChunks(document.getKnowledgeBaseId());
        if (existing + chunks.size() > maxChunksPerBase) {
            throw new IllegalArgumentException("知识库切片总数超过个人版上限 " + maxChunksPerBase);
        }
        mapper.insertKnowledgeDocument(document);
        for (KnowledgeChunk chunk : chunks) {
            chunk.setDocumentId(document.getDocumentId());
        }
        for (int start = 0; start < chunks.size(); start += INSERT_BATCH_SIZE) {
            mapper.batchInsertKnowledgeChunks(chunks.subList(start, Math.min(start + INSERT_BATCH_SIZE, chunks.size())));
        }
    }
}
