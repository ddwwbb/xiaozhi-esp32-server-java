package com.xiaozhi.dialogue.metrics;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.personal.model.PersonalModels.MessageMetric;
import com.xiaozhi.personal.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class TurnMetricsRecorder {

    private final StatisticsService statisticsService;
    private final Clock clock;
    private final ConcurrentHashMap<String, TurnContext> turns = new ConcurrentHashMap<>();

    @Value("${personal.statistics.zone:Asia/Shanghai}")
    private String statisticsZone;

    public void start(ChatSession session) {
        if (session == null || session.getDevice() == null) return;
        turns.put(session.getSessionId(), new TurnContext(session.getSessionId(), session.getDevice(), System.nanoTime()));
    }

    public void sttCompleted(String sessionId) {
        TurnContext context = turns.get(sessionId);
        if (context != null) context.sttEnd = System.nanoTime();
    }

    public void llmStarted(String sessionId, String provider) {
        TurnContext context = turns.get(sessionId);
        if (context != null) {
            context.llmStart = System.nanoTime();
            context.llmProvider = provider;
        }
    }

    public void firstToken(String sessionId) {
        TurnContext context = turns.get(sessionId);
        if (context != null && context.firstToken == 0) context.firstToken = System.nanoTime();
    }

    public void llmCompleted(String sessionId) {
        TurnContext context = turns.get(sessionId);
        if (context != null) context.llmEnd = System.nanoTime();
    }

    public void ttsStarted(String sessionId, String provider) {
        TurnContext context = turns.get(sessionId);
        if (context != null && context.ttsStart == 0) {
            context.ttsStart = System.nanoTime();
            context.ttsProvider = provider;
        }
    }

    public void firstAudio(String sessionId) {
        TurnContext context = turns.get(sessionId);
        if (context != null && context.firstAudio == 0) context.firstAudio = System.nanoTime();
    }

    public void complete(String sessionId) {
        TurnContext context = turns.remove(sessionId);
        if (context == null || !context.completed.compareAndSet(false, true)) return;
        context.end = System.nanoTime();
        persist(context, true, null);
    }

    public void fail(String sessionId, String errorCode) {
        TurnContext context = turns.remove(sessionId);
        if (context == null || !context.completed.compareAndSet(false, true)) return;
        context.end = System.nanoTime();
        persist(context, false, errorCode);
    }

    private void persist(TurnContext context, boolean success, String errorCode) {
        Thread.startVirtualThread(() -> {
            try {
                MessageMetric metric = new MessageMetric();
                metric.setUserId(context.userId);
                metric.setDeviceId(context.deviceId);
                metric.setRoleId(context.roleId);
                metric.setSessionId(context.sessionId);
                metric.setStatDate(LocalDate.now(clock.withZone(ZoneId.of(statisticsZone))));
                metric.setSttDurationMs(duration(context.start, context.sttEnd));
                metric.setLlmTtftMs(duration(context.llmStart, context.firstToken));
                metric.setLlmDurationMs(duration(context.llmStart, context.llmEnd));
                metric.setTtsFirstAudioMs(duration(context.ttsStart, context.firstAudio));
                metric.setTtsDurationMs(duration(context.ttsStart, context.end));
                metric.setEndToEndMs(duration(context.start, context.end));
                metric.setLlmProvider(context.llmProvider);
                metric.setTtsProvider(context.ttsProvider);
                metric.setSuccess(success);
                metric.setErrorCode(errorCode);
                statisticsService.save(metric);
            } catch (RuntimeException e) {
                log.error("[对话指标] 落库失败，sessionId={}", context.sessionId, e);
            }
        });
    }

    private Integer duration(long start, long end) {
        if (start <= 0 || end <= start) return null;
        long millis = (end - start) / 1_000_000L;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    private static final class TurnContext {
        private final String sessionId;
        private final Integer userId;
        private final String deviceId;
        private final Integer roleId;
        private final long start;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile long sttEnd;
        private volatile long llmStart;
        private volatile long firstToken;
        private volatile long llmEnd;
        private volatile long ttsStart;
        private volatile long firstAudio;
        private volatile long end;
        private volatile String llmProvider;
        private volatile String ttsProvider;

        private TurnContext(String sessionId, DeviceBO device, long start) {
            this.sessionId = sessionId;
            this.userId = device.getUserId();
            this.deviceId = device.getDeviceId();
            this.roleId = device.getRoleId();
            this.start = start;
        }
    }
}
