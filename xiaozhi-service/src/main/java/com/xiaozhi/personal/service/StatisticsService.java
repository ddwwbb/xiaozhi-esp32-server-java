package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.MessageMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final PersonalFeatureMapper mapper;

    public void save(MessageMetric metric) {
        if (metric == null || metric.getDeviceId() == null || metric.getStatDate() == null) {
            throw new IllegalArgumentException("设备和统计日期不能为空");
        }
        metric.setPromptTokens(metric.getPromptTokens() == null ? 0 : metric.getPromptTokens());
        metric.setCompletionTokens(metric.getCompletionTokens() == null ? 0 : metric.getCompletionTokens());
        metric.setSuccess(metric.getSuccess() == null || metric.getSuccess());
        mapper.upsertMessageMetric(metric);
    }

    public Overview overview(Integer userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, Object> row = mapper.metricsOverview(userId, from, to);
        return new Overview(number(row, "turnCount"), number(row, "promptTokens"),
                number(row, "completionTokens"), number(row, "avgEndToEndMs"),
                number(row, "avgSttDurationMs"), number(row, "avgLlmDurationMs"),
                number(row, "avgTtsDurationMs"), number(row, "errorCount"));
    }

    public List<TrendPoint> trend(Integer userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return mapper.metricsTrend(userId, from, to).stream()
                .map(row -> new TrendPoint(String.valueOf(row.get("statDate")), number(row, "turnCount"),
                        number(row, "tokens"), number(row, "avgEndToEndMs")))
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || from.plusDays(366).isBefore(to)) {
            throw new IllegalArgumentException("统计时间范围必须在 366 天以内");
        }
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record Overview(long turnCount, long promptTokens, long completionTokens, long avgEndToEndMs,
                           long avgSttDurationMs, long avgLlmDurationMs, long avgTtsDurationMs, long errorCount) {
    }

    public record TrendPoint(String statDate, long turnCount, long tokens, long avgEndToEndMs) {
    }
}
