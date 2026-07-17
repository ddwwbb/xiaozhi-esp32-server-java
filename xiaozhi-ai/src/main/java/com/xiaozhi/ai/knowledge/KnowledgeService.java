package com.xiaozhi.ai.knowledge;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.KnowledgeBase;
import com.xiaozhi.personal.model.PersonalModels.KnowledgeChunk;
import com.xiaozhi.personal.model.PersonalModels.KnowledgeDocument;
import com.xiaozhi.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_PARSED_CHARS = 2_000_000;
    private static final int CHUNK_SIZE = 1_200;
    private static final int CHUNK_OVERLAP = 150;
    private static final int MAX_CHUNKS_PER_BASE = 2_000;

    private final PersonalFeatureMapper mapper;
    private final ChatModelFactory chatModelFactory;
    private final RuntimePathConfig runtimePathConfig;
    private final KnowledgePersistenceService persistenceService;
    private final ConfigService configService;

    public KnowledgeBase createBase(Integer userId, Integer roleId, String name, Integer embeddingConfigId) {
        if (userId == null || !StringUtils.hasText(name) || embeddingConfigId == null) {
            throw new IllegalArgumentException("用户、知识库名称和向量模型配置不能为空");
        }
        ConfigBO embeddingConfig = configService.getBO(embeddingConfigId);
        if (embeddingConfig == null || (!userId.equals(embeddingConfig.getUserId())
                && !Integer.valueOf(1).equals(embeddingConfig.getUserId()))) {
            throw new IllegalArgumentException("向量模型配置不存在或不属于当前用户");
        }
        chatModelFactory.getEmbeddingModel(embeddingConfigId);
        KnowledgeBase base = new KnowledgeBase();
        base.setUserId(userId);
        base.setRoleId(roleId);
        base.setName(name.trim());
        base.setEmbeddingConfigId(embeddingConfigId);
        mapper.insertKnowledgeBase(base);
        return base;
    }

    public List<KnowledgeBase> listBases(Integer userId) {
        return mapper.listKnowledgeBases(userId);
    }

    public List<KnowledgeDocument> listDocuments(Integer userId, Long baseId) {
        requireBase(userId, baseId);
        return mapper.listKnowledgeDocuments(baseId);
    }

    public KnowledgeDocument upload(Integer userId, Long baseId, String originalFilename,
                                    String contentType, byte[] bytes) {
        KnowledgeBase base = requireBase(userId, baseId);
        validateUpload(originalFilename, contentType, bytes);
        String sha256 = sha256(bytes);
        String extension = safeExtension(originalFilename);
        Path baseDir = runtimePathConfig.resolveKnowledgeDir().resolve(String.valueOf(baseId)).normalize();
        ensureWithin(runtimePathConfig.resolveKnowledgeDir(), baseDir);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(baseId);
        document.setFileName(safeDisplayName(originalFilename));
        document.setMimeType(contentType);
        document.setFileSize((long) bytes.length);
        document.setSha256(sha256);
        document.setStatus("READY");
        Path target = null;
        try {
            Tika tika = new Tika();
            tika.setMaxStringLength(MAX_PARSED_CHARS);
            String text = tika.parseToString(new ByteArrayInputStream(bytes));
            text = normalizeText(text);
            if (!StringUtils.hasText(text)) {
                throw new IllegalArgumentException("文档中没有可索引文本");
            }
            EmbeddingModel embeddingModel = chatModelFactory.getEmbeddingModel(base.getEmbeddingConfigId());
            List<String> chunks = chunk(text);
            if (chunks.size() > MAX_CHUNKS_PER_BASE) {
                throw new IllegalArgumentException("单个文档切片数量超过个人版上限 " + MAX_CHUNKS_PER_BASE);
            }
            List<KnowledgeChunk> chunkEntities = new ArrayList<>(chunks.size());
            int ordinal = 0;
            for (String content : chunks) {
                float[] embedding = embeddingModel.embed(content);
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeBaseId(baseId);
                chunk.setOrdinalNo(ordinal++);
                chunk.setContent(content);
                chunk.setTokenCount(Math.max(1, content.length() / 2));
                chunk.setEmbedding(JsonUtil.toJson(embedding));
                chunkEntities.add(chunk);
            }

            Files.createDirectories(baseDir);
            target = baseDir.resolve(UUID.randomUUID() + extension).normalize();
            ensureWithin(baseDir, target);
            Files.write(target, bytes);
            document.setFilePath(runtimePathConfig.resolveKnowledgeDir().relativize(target).toString().replace('\\', '/'));
            persistenceService.save(document, chunkEntities, MAX_CHUNKS_PER_BASE);
            return document;
        } catch (Exception e) {
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupError) {
                    log.warn("清理知识库失败文件失败: {}", target, cleanupError);
                }
            }
            throw new IllegalStateException("知识库文档索引失败: " + e.getMessage(), e);
        }
    }

    public List<SearchHit> search(Integer userId, Integer roleId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        int limit = Math.min(Math.max(topK, 1), 20);
        PriorityQueue<SearchHit> hits = new PriorityQueue<>(Comparator.comparingDouble(SearchHit::score));
        for (KnowledgeBase base : mapper.findKnowledgeBasesForRole(userId, roleId)) {
            EmbeddingModel model = chatModelFactory.getEmbeddingModel(base.getEmbeddingConfigId());
            float[] queryEmbedding = model.embed(query);
            for (KnowledgeChunk chunk : mapper.listKnowledgeChunks(base.getKnowledgeBaseId(), MAX_CHUNKS_PER_BASE)) {
                float[] vector = JsonUtil.fromJson(chunk.getEmbedding(), float[].class);
                double score = cosine(queryEmbedding, vector);
                SearchHit hit = new SearchHit(base.getKnowledgeBaseId(), chunk.getDocumentId(), chunk.getChunkId(),
                        chunk.getContent(), score);
                if (hits.size() < limit) {
                    hits.offer(hit);
                } else if (score > Objects.requireNonNull(hits.peek()).score()) {
                    hits.poll();
                    hits.offer(hit);
                }
            }
        }
        List<SearchHit> result = new ArrayList<>(hits);
        result.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return result;
    }

    public String renderForPrompt(Integer userId, Integer roleId, String query) {
        List<SearchHit> hits = search(userId, roleId, query, 5).stream()
                .filter(hit -> hit.score() >= 0.25)
                .toList();
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder("以下内容来自用户知识库。仅在相关时使用，并保留事实边界：\n");
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            prompt.append('[').append(i + 1).append("] ").append(hit.content()).append('\n');
        }
        return prompt.toString();
    }

    public int deleteBase(Integer userId, Long baseId) {
        return mapper.deleteKnowledgeBase(baseId, userId);
    }

    private KnowledgeBase requireBase(Integer userId, Long baseId) {
        KnowledgeBase base = mapper.findKnowledgeBase(baseId, userId);
        if (base == null) {
            throw new IllegalArgumentException("知识库不存在或不属于当前用户");
        }
        return base;
    }

    private void validateUpload(String name, String contentType, byte[] bytes) {
        if (!StringUtils.hasText(name) || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件不能超过 20MB");
        }
        Set<String> allowed = Set.of("text/plain", "text/markdown", "text/html", "application/json",
                "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (!allowed.contains(contentType)) {
            throw new IllegalArgumentException("不支持的知识库文件类型: " + contentType);
        }
    }

    private String safeDisplayName(String name) {
        String value = Path.of(name).getFileName().toString();
        return truncate(value.replaceAll("[\\p{Cntrl}]", ""), 255);
    }

    private String safeExtension(String name) {
        String safe = safeDisplayName(name);
        int dot = safe.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String extension = safe.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.(txt|md|html|json|pdf|docx)") ? extension : "";
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\u0000", "").replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim();
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int breakAt = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                if (breakAt > start + CHUNK_SIZE / 2) {
                    end = breakAt + 1;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return -1;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? -1 : dot / Math.sqrt(na * nb);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private void ensureWithin(Path root, Path target) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record SearchHit(Long knowledgeBaseId, Long documentId, Long chunkId, String content, double score) {
    }
}
