package com.xiaozhi.personal.dal;

import com.xiaozhi.personal.model.PersonalModels.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PersonalFeatureMapper {

    @Insert("""
        INSERT INTO memory_fact(userId, roleId, namespace, factKey, factValue, confidence,
            sourceMessageId, status, expiresAt)
        VALUES(#{userId}, #{roleId}, #{namespace}, #{factKey}, #{factValue}, #{confidence},
            #{sourceMessageId}, 'ACTIVE', #{expiresAt})
        ON DUPLICATE KEY UPDATE factValue = VALUES(factValue), confidence = VALUES(confidence),
            sourceMessageId = VALUES(sourceMessageId), status = 'ACTIVE', expiresAt = VALUES(expiresAt)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "memoryId")
    int upsertMemory(MemoryFact fact);

    @Select("""
        SELECT * FROM memory_fact
        WHERE userId = #{userId} AND status = 'ACTIVE'
          AND (roleId = 0 OR roleId = #{roleId})
          AND (expiresAt IS NULL OR expiresAt > #{now})
        ORDER BY confidence DESC, updateTime DESC
        LIMIT #{limit}
        """)
    List<MemoryFact> listActiveMemories(@Param("userId") Integer userId,
                                        @Param("roleId") Integer roleId,
                                        @Param("now") LocalDateTime now,
                                        @Param("limit") int limit);

    @Select("SELECT * FROM memory_fact WHERE userId = #{userId} ORDER BY updateTime DESC LIMIT #{limit}")
    List<MemoryFact> listMemories(@Param("userId") Integer userId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM memory_fact WHERE userId = #{userId} AND status = 'ACTIVE'")
    int countActiveMemories(@Param("userId") Integer userId);

    @Select("""
        SELECT COUNT(*) FROM memory_fact
        WHERE userId = #{userId} AND roleId = #{roleId} AND namespace = #{namespace}
          AND factKey = #{factKey} AND status = 'ACTIVE'
        """)
    int countActiveMemoryByKey(@Param("userId") Integer userId,
                               @Param("roleId") Integer roleId,
                               @Param("namespace") String namespace,
                               @Param("factKey") String factKey);

    @Update("UPDATE memory_fact SET status = 'DELETED' WHERE memoryId = #{memoryId} AND userId = #{userId}")
    int deleteMemory(@Param("userId") Integer userId, @Param("memoryId") Long memoryId);

    @Insert("INSERT INTO knowledge_base(userId, roleId, name, embeddingConfigId) VALUES(#{userId}, #{roleId}, #{name}, #{embeddingConfigId})")
    @Options(useGeneratedKeys = true, keyProperty = "knowledgeBaseId")
    int insertKnowledgeBase(KnowledgeBase base);

    @Select("SELECT * FROM knowledge_base WHERE userId = #{userId} AND state = 'ACTIVE' ORDER BY createTime DESC")
    List<KnowledgeBase> listKnowledgeBases(Integer userId);

    @Select("SELECT * FROM knowledge_base WHERE knowledgeBaseId = #{id} AND userId = #{userId} AND state = 'ACTIVE'")
    KnowledgeBase findKnowledgeBase(@Param("id") Long id, @Param("userId") Integer userId);

    @Select("SELECT * FROM knowledge_base WHERE userId = #{userId} AND state = 'ACTIVE' AND (roleId IS NULL OR roleId = #{roleId})")
    List<KnowledgeBase> findKnowledgeBasesForRole(@Param("userId") Integer userId, @Param("roleId") Integer roleId);

    @Update("UPDATE knowledge_base SET state = 'DELETED' WHERE knowledgeBaseId = #{id} AND userId = #{userId}")
    int deleteKnowledgeBase(@Param("id") Long id, @Param("userId") Integer userId);

    @Insert("""
        INSERT INTO knowledge_document(knowledgeBaseId, fileName, filePath, mimeType, fileSize, sha256, status)
        VALUES(#{knowledgeBaseId}, #{fileName}, #{filePath}, #{mimeType}, #{fileSize}, #{sha256}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "documentId")
    int insertKnowledgeDocument(KnowledgeDocument document);

    @Select("SELECT * FROM knowledge_document WHERE knowledgeBaseId = #{baseId} ORDER BY createTime DESC")
    List<KnowledgeDocument> listKnowledgeDocuments(Long baseId);

    @Update("UPDATE knowledge_document SET status = #{status}, errorMessage = #{errorMessage} WHERE documentId = #{documentId}")
    int updateKnowledgeDocumentStatus(@Param("documentId") Long documentId,
                                      @Param("status") String status,
                                      @Param("errorMessage") String errorMessage);

    @Insert("""
        INSERT INTO knowledge_chunk(knowledgeBaseId, documentId, ordinalNo, content, tokenCount, embedding)
        VALUES(#{knowledgeBaseId}, #{documentId}, #{ordinalNo}, #{content}, #{tokenCount}, #{embedding})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "chunkId")
    int insertKnowledgeChunk(KnowledgeChunk chunk);

    @Insert({
        "<script>",
        "INSERT INTO knowledge_chunk(knowledgeBaseId, documentId, ordinalNo, content, tokenCount, embedding)",
        "VALUES",
        "<foreach collection='chunks' item='chunk' separator=','>",
        "(#{chunk.knowledgeBaseId}, #{chunk.documentId}, #{chunk.ordinalNo},",
        " #{chunk.content}, #{chunk.tokenCount}, #{chunk.embedding})",
        "</foreach>",
        "</script>"
    })
    int batchInsertKnowledgeChunks(@Param("chunks") List<KnowledgeChunk> chunks);

    @Select("SELECT COUNT(*) FROM knowledge_chunk WHERE knowledgeBaseId = #{knowledgeBaseId}")
    int countKnowledgeChunks(@Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("SELECT * FROM knowledge_chunk WHERE knowledgeBaseId = #{baseId} ORDER BY chunkId LIMIT #{limit}")
    List<KnowledgeChunk> listKnowledgeChunks(@Param("baseId") Long baseId, @Param("limit") int limit);

    @Delete("DELETE FROM knowledge_chunk WHERE documentId = #{documentId}")
    int deleteKnowledgeChunks(Long documentId);

    @Insert("""
        INSERT INTO voice_profile(userId, displayName, modelName, modelVersion, embeddingDimension,
            centroidEmbedding, thresholdValue, state)
        VALUES(#{userId}, #{displayName}, #{modelName}, #{modelVersion}, #{embeddingDimension},
            #{centroidEmbedding}, #{thresholdValue}, 'ACTIVE')
        """)
    @Options(useGeneratedKeys = true, keyProperty = "profileId")
    int insertVoiceProfile(VoiceProfile profile);

    @Select("SELECT * FROM voice_profile WHERE userId = #{userId} AND state = 'ACTIVE' ORDER BY createTime DESC")
    List<VoiceProfile> listVoiceProfiles(Integer userId);

    @Update("UPDATE voice_profile SET state = 'DELETED' WHERE profileId = #{profileId} AND userId = #{userId}")
    int deleteVoiceProfile(@Param("profileId") Long profileId, @Param("userId") Integer userId);

    @Insert("""
        INSERT INTO voice_clone(userId, configId, provider, name, samplePath, sampleMimeType,
            sampleSha256, status)
        VALUES(#{userId}, #{configId}, #{provider}, #{name}, #{samplePath}, #{sampleMimeType},
            #{sampleSha256}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "cloneId")
    int insertVoiceClone(VoiceClone clone);

    @Select("SELECT * FROM voice_clone WHERE cloneId = #{cloneId} AND userId = #{userId}")
    VoiceClone findVoiceClone(@Param("cloneId") Long cloneId, @Param("userId") Integer userId);

    @Select("SELECT * FROM voice_clone WHERE userId = #{userId} ORDER BY createTime DESC")
    List<VoiceClone> listVoiceClones(Integer userId);

    @Update("""
        UPDATE voice_clone SET providerTaskId = #{providerTaskId}, providerVoiceId = #{providerVoiceId},
            status = #{status}, errorMessage = #{errorMessage}
        WHERE cloneId = #{cloneId}
        """)
    int updateVoiceClone(VoiceClone clone);

    @Delete("DELETE FROM voice_clone WHERE cloneId = #{cloneId} AND userId = #{userId}")
    int deleteVoiceClone(@Param("cloneId") Long cloneId, @Param("userId") Integer userId);

    @Insert("""
        INSERT INTO message_metrics(messageId, userId, deviceId, roleId, sessionId, statDate,
            sttDurationMs, llmTtftMs, llmDurationMs, ttsFirstAudioMs, ttsDurationMs, endToEndMs,
            promptTokens, completionTokens, sttProvider, llmProvider, ttsProvider, success, errorCode)
        VALUES(#{messageId}, #{userId}, #{deviceId}, #{roleId}, #{sessionId}, #{statDate},
            #{sttDurationMs}, #{llmTtftMs}, #{llmDurationMs}, #{ttsFirstAudioMs}, #{ttsDurationMs}, #{endToEndMs},
            #{promptTokens}, #{completionTokens}, #{sttProvider}, #{llmProvider}, #{ttsProvider}, #{success}, #{errorCode})
        ON DUPLICATE KEY UPDATE sttDurationMs=VALUES(sttDurationMs), llmTtftMs=VALUES(llmTtftMs),
            llmDurationMs=VALUES(llmDurationMs), ttsFirstAudioMs=VALUES(ttsFirstAudioMs),
            ttsDurationMs=VALUES(ttsDurationMs), endToEndMs=VALUES(endToEndMs),
            promptTokens=VALUES(promptTokens), completionTokens=VALUES(completionTokens),
            success=VALUES(success), errorCode=VALUES(errorCode)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "metricId")
    int upsertMessageMetric(MessageMetric metric);

    @Select("""
        SELECT COUNT(*) turnCount, COALESCE(SUM(promptTokens), 0) promptTokens,
            COALESCE(SUM(completionTokens), 0) completionTokens,
            COALESCE(AVG(endToEndMs), 0) avgEndToEndMs,
            COALESCE(AVG(sttDurationMs), 0) avgSttDurationMs,
            COALESCE(AVG(llmDurationMs), 0) avgLlmDurationMs,
            COALESCE(AVG(ttsDurationMs), 0) avgTtsDurationMs,
            COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) errorCount
        FROM message_metrics WHERE userId = #{userId} AND statDate BETWEEN #{from} AND #{to}
        """)
    Map<String, Object> metricsOverview(@Param("userId") Integer userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Select("""
        SELECT statDate, COUNT(*) turnCount, COALESCE(SUM(promptTokens + completionTokens), 0) tokens,
            COALESCE(AVG(endToEndMs), 0) avgEndToEndMs
        FROM message_metrics WHERE userId = #{userId} AND statDate BETWEEN #{from} AND #{to}
        GROUP BY statDate ORDER BY statDate
        """)
    List<Map<String, Object>> metricsTrend(@Param("userId") Integer userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Insert("""
        INSERT INTO reminder(userId, deviceId, title, content, timezone, localTime, recurrenceType,
            weekdays, nextTriggerAt, deliveryPolicy, status)
        VALUES(#{userId}, #{deviceId}, #{title}, #{content}, #{timezone}, #{localTime}, #{recurrenceType},
            #{weekdays}, #{nextTriggerAt}, #{deliveryPolicy}, 'ACTIVE')
        """)
    @Options(useGeneratedKeys = true, keyProperty = "reminderId")
    int insertReminder(Reminder reminder);

    @Select("SELECT * FROM reminder WHERE userId = #{userId} AND status != 'DELETED' ORDER BY nextTriggerAt")
    List<Reminder> listReminders(Integer userId);

    @Select("SELECT * FROM reminder WHERE reminderId = #{reminderId} AND userId = #{userId}")
    Reminder findReminder(@Param("reminderId") Long reminderId, @Param("userId") Integer userId);

    @Select("SELECT * FROM reminder WHERE status = 'ACTIVE' AND nextTriggerAt <= #{now} ORDER BY nextTriggerAt LIMIT #{limit}")
    List<Reminder> findDueReminders(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
        UPDATE reminder SET nextTriggerAt = #{nextTriggerAt}, status = #{status}, version = version + 1
        WHERE reminderId = #{reminderId} AND version = #{version} AND status = 'ACTIVE'
        """)
    int advanceReminder(@Param("reminderId") Long reminderId,
                        @Param("version") Integer version,
                        @Param("nextTriggerAt") LocalDateTime nextTriggerAt,
                        @Param("status") String status);

    @Update("UPDATE reminder SET status = 'DELETED', version = version + 1 WHERE reminderId = #{reminderId} AND userId = #{userId}")
    int deleteReminder(@Param("reminderId") Long reminderId, @Param("userId") Integer userId);

    @Insert("""
        INSERT IGNORE INTO reminder_delivery(reminderId, scheduledAt, status)
        VALUES(#{reminderId}, #{scheduledAt}, 'PENDING')
        """)
    @Options(useGeneratedKeys = true, keyProperty = "deliveryId")
    int insertReminderDelivery(ReminderDelivery delivery);

    @Update("UPDATE reminder_delivery SET status = #{status}, attemptNo = attemptNo + 1, sentAt = #{sentAt}, errorMessage = #{errorMessage} WHERE deliveryId = #{deliveryId}")
    int updateReminderDelivery(ReminderDelivery delivery);

    @Select("""
        SELECT d.deliveryId, d.reminderId, r.userId, r.deviceId, r.title, r.content, r.deliveryPolicy
        FROM reminder_delivery d JOIN reminder r ON r.reminderId = d.reminderId
        WHERE r.deviceId = #{deviceId} AND d.status = 'PENDING'
          AND r.deliveryPolicy = 'NEXT_CONNECT'
        ORDER BY d.scheduledAt LIMIT #{limit}
        """)
    List<PendingReminderDelivery> findPendingDeliveries(@Param("deviceId") String deviceId,
                                                        @Param("limit") int limit);
}
