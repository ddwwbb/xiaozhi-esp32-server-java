package com.xiaozhi.personal.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class PersonalModels {

    private PersonalModels() {
    }

    @Data
    public static class MemoryFact {
        private Long memoryId;
        private Integer userId;
        private Integer roleId;
        private String namespace;
        private String factKey;
        private String factValue;
        private BigDecimal confidence;
        private Integer sourceMessageId;
        private String status;
        private LocalDateTime expiresAt;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class KnowledgeBase {
        private Long knowledgeBaseId;
        private Integer userId;
        private Integer roleId;
        private String name;
        private Integer embeddingConfigId;
        private String state;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class KnowledgeDocument {
        private Long documentId;
        private Long knowledgeBaseId;
        private String fileName;
        private String filePath;
        private String mimeType;
        private Long fileSize;
        private String sha256;
        private String status;
        private String errorMessage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class KnowledgeChunk {
        private Long chunkId;
        private Long knowledgeBaseId;
        private Long documentId;
        private Integer ordinalNo;
        private String content;
        private Integer tokenCount;
        private String embedding;
        private LocalDateTime createTime;
    }

    @Data
    public static class VoiceProfile {
        private Long profileId;
        private Integer userId;
        private String displayName;
        private String modelName;
        private String modelVersion;
        private Integer embeddingDimension;
        private String centroidEmbedding;
        private BigDecimal thresholdValue;
        private String state;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class VoiceClone {
        private Long cloneId;
        private Integer userId;
        private Integer configId;
        private String provider;
        private String name;
        private String samplePath;
        private String sampleMimeType;
        private String sampleSha256;
        private String providerTaskId;
        private String providerVoiceId;
        private String status;
        private String errorMessage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class MessageMetric {
        private Long metricId;
        private Integer messageId;
        private Integer userId;
        private String deviceId;
        private Integer roleId;
        private String sessionId;
        private LocalDate statDate;
        private Integer sttDurationMs;
        private Integer llmTtftMs;
        private Integer llmDurationMs;
        private Integer ttsFirstAudioMs;
        private Integer ttsDurationMs;
        private Integer endToEndMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private String sttProvider;
        private String llmProvider;
        private String ttsProvider;
        private Boolean success;
        private String errorCode;
        private LocalDateTime createTime;
    }

    @Data
    public static class Reminder {
        private Long reminderId;
        private Integer userId;
        private String deviceId;
        private String title;
        private String content;
        private String timezone;
        private LocalTime localTime;
        private String recurrenceType;
        private String weekdays;
        private LocalDateTime nextTriggerAt;
        private String deliveryPolicy;
        private String status;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ReminderDelivery {
        private Long deliveryId;
        private Long reminderId;
        private LocalDateTime scheduledAt;
        private Integer attemptNo;
        private String status;
        private LocalDateTime sentAt;
        private LocalDateTime ackAt;
        private String errorMessage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class PendingReminderDelivery {
        private Long deliveryId;
        private Long reminderId;
        private Integer userId;
        private String deviceId;
        private String title;
        private String content;
        private String deliveryPolicy;
    }
}
