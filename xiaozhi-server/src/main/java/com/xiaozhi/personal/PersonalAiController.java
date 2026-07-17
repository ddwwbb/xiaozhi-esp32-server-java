package com.xiaozhi.personal;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.ai.knowledge.KnowledgeService;
import com.xiaozhi.ai.voiceclone.VoiceCloneService;
import com.xiaozhi.ai.voiceprint.SpeakerRecognitionService;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.personal.service.LongTermMemoryService;
import com.xiaozhi.personal.service.ReminderService;
import com.xiaozhi.personal.service.StatisticsService;
import com.xiaozhi.personal.service.VoiceProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/personal")
@Tag(name = "个人版 AI 能力", description = "RAG、长期记忆、声纹、音色克隆、统计与提醒")
public class PersonalAiController {

    private final LongTermMemoryService memoryService;
    private final KnowledgeService knowledgeService;
    private final VoiceProfileService voiceProfileService;
    private final SpeakerRecognitionService speakerRecognitionService;
    private final VoiceCloneService voiceCloneService;
    private final StatisticsService statisticsService;
    private final ReminderService reminderService;

    @PostMapping("/memories")
    @Operation(summary = "保存或纠正长期记忆")
    public ApiResponse<?> saveMemory(@Valid @RequestBody MemorySaveReq req) {
        return ApiResponse.success(memoryService.save(userId(), req.roleId(), req.namespace(), req.key(), req.value(),
                req.confidence(), req.sourceMessageId(), req.expiresAt()));
    }

    @GetMapping("/memories")
    @Operation(summary = "查询长期记忆")
    public ApiResponse<?> listMemories() {
        return ApiResponse.success(memoryService.list(userId()));
    }

    @DeleteMapping("/memories/{memoryId}")
    @Operation(summary = "删除长期记忆")
    public ApiResponse<?> deleteMemory(@PathVariable Long memoryId) {
        return ApiResponse.success(memoryService.delete(userId(), memoryId));
    }

    @PostMapping("/knowledge-bases")
    @Operation(summary = "创建知识库")
    public ApiResponse<?> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreateReq req) {
        return ApiResponse.success(knowledgeService.createBase(userId(), req.roleId(), req.name(), req.embeddingConfigId()));
    }

    @GetMapping("/knowledge-bases")
    @Operation(summary = "查询知识库")
    public ApiResponse<?> listKnowledgeBases() {
        return ApiResponse.success(knowledgeService.listBases(userId()));
    }

    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}")
    @Operation(summary = "删除知识库")
    public ApiResponse<?> deleteKnowledgeBase(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(knowledgeService.deleteBase(userId(), knowledgeBaseId));
    }

    @PostMapping(value = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传并索引知识库文档")
    public ApiResponse<?> uploadKnowledgeDocument(@PathVariable Long knowledgeBaseId,
                                                  @RequestPart("file") MultipartFile file) throws Exception {
        return ApiResponse.success(knowledgeService.upload(userId(), knowledgeBaseId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    @Operation(summary = "查询知识库文档")
    public ApiResponse<?> listKnowledgeDocuments(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(knowledgeService.listDocuments(userId(), knowledgeBaseId));
    }

    @PostMapping("/knowledge/search")
    @Operation(summary = "调试知识库检索")
    public ApiResponse<?> searchKnowledge(@Valid @RequestBody KnowledgeSearchReq req) {
        return ApiResponse.success(knowledgeService.search(userId(), req.roleId(), req.query(), req.topK()));
    }

    @PostMapping("/voice-profiles")
    @Operation(summary = "使用声纹向量注册说话人")
    public ApiResponse<?> enrollVoiceProfile(@Valid @RequestBody VoiceProfileEnrollReq req) {
        return ApiResponse.success(voiceProfileService.enroll(userId(), req.displayName(), req.modelName(),
                req.modelVersion(), req.sampleEmbeddings(), req.threshold()));
    }

    @PostMapping(value = "/voice-profiles/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "使用音频注册说话人", description = "至少 3 段、每段至少 1 秒的 16kHz 单声道 WAV/PCM")
    public ApiResponse<?> enrollVoiceProfileAudio(
            @RequestPart("files") @Size(min = 3, max = 10) List<MultipartFile> files,
            @RequestParam @Size(min = 1, max = 100) String displayName,
            @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal threshold)
            throws Exception {
        List<byte[]> samples = new java.util.ArrayList<>(files.size());
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
                throw new IllegalArgumentException("单个声纹样本必须为 1 字节至 10MB");
            }
            samples.add(file.getBytes());
        }
        return ApiResponse.success(speakerRecognitionService.enroll(userId(), displayName, samples, threshold));
    }

    @GetMapping("/voice-profiles")
    @Operation(summary = "查询声纹档案")
    public ApiResponse<?> listVoiceProfiles() {
        return ApiResponse.success(voiceProfileService.list(userId()));
    }

    @DeleteMapping("/voice-profiles/{profileId}")
    @Operation(summary = "删除声纹档案")
    public ApiResponse<?> deleteVoiceProfile(@PathVariable Long profileId) {
        return ApiResponse.success(voiceProfileService.delete(userId(), profileId));
    }

    @PostMapping(value = "/voice-clones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建克隆音色", description = "支持 aliyun、volcengine、minimax、mimo Provider")
    public ApiResponse<?> createVoiceClone(@RequestPart("file") MultipartFile file,
                                           @RequestParam Integer configId,
                                           @RequestParam @Size(min = 1, max = 100) String name,
                                           @RequestParam(required = false) String sourceUrl,
                                           @RequestParam(required = false) String requestedVoiceId,
                                           @RequestParam(required = false) String sampleText) throws Exception {
        return ApiResponse.success(voiceCloneService.create(userId(), configId, name, file.getContentType(),
                file.getBytes(), sourceUrl, requestedVoiceId, sampleText));
    }

    @GetMapping("/voice-clones")
    @Operation(summary = "查询克隆音色")
    public ApiResponse<?> listVoiceClones() {
        return ApiResponse.success(voiceCloneService.list(userId()));
    }

    @PostMapping("/voice-clones/{cloneId}/refresh")
    @Operation(summary = "刷新克隆音色训练状态")
    public ApiResponse<?> refreshVoiceClone(@PathVariable Long cloneId) {
        return ApiResponse.success(voiceCloneService.refresh(userId(), cloneId));
    }

    @PostMapping("/voice-clones/{cloneId}/preview")
    @Operation(summary = "试听克隆音色")
    public ResponseEntity<byte[]> previewVoiceClone(@PathVariable Long cloneId,
                                                    @Valid @RequestBody VoicePreviewReq req) {
        VoiceCloneService.PreviewAudio audio = voiceCloneService.preview(userId(), cloneId, req.text());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(audio.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("voice-preview." + audio.extension(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(audio.bytes());
    }

    @DeleteMapping("/voice-clones/{cloneId}")
    @Operation(summary = "删除克隆音色和本地私有样本")
    public ApiResponse<?> deleteVoiceClone(@PathVariable Long cloneId) {
        return ApiResponse.success(voiceCloneService.delete(userId(), cloneId));
    }

    @GetMapping("/statistics/overview")
    @Operation(summary = "查询使用统计概览")
    public ApiResponse<?> statisticsOverview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.overview(userId(), from, to));
    }

    @GetMapping("/statistics/trend")
    @Operation(summary = "查询每日统计趋势")
    public ApiResponse<?> statisticsTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.trend(userId(), from, to));
    }

    @PostMapping("/reminders")
    @Operation(summary = "创建闹钟或主动提醒")
    public ApiResponse<?> createReminder(@Valid @RequestBody ReminderCreateReq req) {
        return ApiResponse.success(reminderService.create(userId(), req.deviceId(), req.title(), req.content(),
                req.timezone(), req.firstTriggerLocal(), req.recurrenceType(), req.weekdays(), req.deliveryPolicy()));
    }

    @GetMapping("/reminders")
    @Operation(summary = "查询闹钟和提醒")
    public ApiResponse<?> listReminders() {
        return ApiResponse.success(reminderService.list(userId()));
    }

    @DeleteMapping("/reminders/{reminderId}")
    @Operation(summary = "删除闹钟或提醒")
    public ApiResponse<?> deleteReminder(@PathVariable Long reminderId) {
        return ApiResponse.success(reminderService.delete(userId(), reminderId));
    }

    private Integer userId() {
        return StpUtil.getLoginIdAsInt();
    }

    public record MemorySaveReq(Integer roleId, @Size(max = 32) String namespace,
                                @NotBlank @Size(max = 100) String key,
                                @NotBlank @Size(max = 2000) String value,
                                @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
                                Integer sourceMessageId, LocalDateTime expiresAt) {
    }

    public record KnowledgeBaseCreateReq(Integer roleId, @NotBlank @Size(max = 100) String name,
                                         @NotNull @Positive Integer embeddingConfigId) {
    }

    public record KnowledgeSearchReq(Integer roleId, @NotBlank @Size(max = 2000) String query,
                                     @Min(1) @Max(20) int topK) {
        public KnowledgeSearchReq {
            if (topK == 0) topK = 5;
        }
    }

    public record VoiceProfileEnrollReq(@NotBlank @Size(max = 100) String displayName,
                                        String modelName, String modelVersion,
                                        @NotEmpty @Size(min = 3, max = 10) List<@NotEmpty List<@NotNull Double>> sampleEmbeddings,
                                        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal threshold) {
    }

    public record VoicePreviewReq(@NotBlank @Size(max = 500) String text) {
    }

    public record ReminderCreateReq(@NotBlank @Size(max = 64) String deviceId,
                                    @NotBlank @Size(max = 100) String title,
                                    @Size(max = 500) String content,
                                    @NotBlank @Size(max = 64) String timezone,
                                    @NotNull LocalDateTime firstTriggerLocal,
                                    @NotBlank String recurrenceType,
                                    String weekdays,
                                    String deliveryPolicy) {
    }
}
