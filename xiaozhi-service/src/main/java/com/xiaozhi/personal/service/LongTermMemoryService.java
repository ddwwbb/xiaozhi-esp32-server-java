package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.MemoryFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private static final int MAX_FACTS = 500;
    private static final int MAX_RETRIEVED_FACTS = 10;

    private final PersonalFeatureMapper mapper;
    private final Clock clock;

    public MemoryFact save(Integer userId, Integer roleId, String namespace, String key, String value,
                           BigDecimal confidence, Integer sourceMessageId, LocalDateTime expiresAt) {
        if (userId == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            throw new IllegalArgumentException("userId、记忆键和值不能为空");
        }
        String normalizedNamespace = StringUtils.hasText(namespace) ? namespace.trim() : "profile";
        String normalizedKey = normalizeKey(key);
        Integer normalizedRoleId = roleId == null ? 0 : roleId;
        boolean createsNewFact = mapper.countActiveMemoryByKey(
                userId, normalizedRoleId, normalizedNamespace, normalizedKey) == 0;
        if (createsNewFact && mapper.countActiveMemories(userId) >= MAX_FACTS) {
            throw new IllegalStateException("长期记忆已达到个人版上限 " + MAX_FACTS);
        }
        MemoryFact fact = new MemoryFact();
        fact.setUserId(userId);
        fact.setRoleId(normalizedRoleId);
        fact.setNamespace(normalizedNamespace);
        fact.setFactKey(normalizedKey);
        fact.setFactValue(value.trim());
        fact.setConfidence(confidence == null ? BigDecimal.ONE : confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        fact.setSourceMessageId(sourceMessageId);
        fact.setExpiresAt(expiresAt);
        mapper.upsertMemory(fact);
        return fact;
    }

    public List<MemoryFact> list(Integer userId) {
        return userId == null ? List.of() : mapper.listMemories(userId, MAX_FACTS);
    }

    public int delete(Integer userId, Long memoryId) {
        return userId == null || memoryId == null ? 0 : mapper.deleteMemory(userId, memoryId);
    }

    public List<MemoryFact> retrieve(Integer userId, Integer roleId, String query) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<MemoryFact> candidates = mapper.listActiveMemories(userId, roleId, nowUtc, 100);
        if (!StringUtils.hasText(query)) {
            return candidates.stream().limit(MAX_RETRIEVED_FACTS).toList();
        }
        Set<String> terms = tokenize(query);
        return candidates.stream()
                .sorted(Comparator.<MemoryFact>comparingInt(f -> relevance(f, terms)).reversed()
                        .thenComparing(MemoryFact::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(f -> relevance(f, terms) > 0)
                .limit(MAX_RETRIEVED_FACTS)
                .toList();
    }

    public String renderForPrompt(Integer userId, Integer roleId, String query) {
        List<MemoryFact> facts = retrieve(userId, roleId, query);
        if (facts.isEmpty()) {
            return "";
        }
        return facts.stream()
                .map(f -> "- " + f.getFactKey() + "：" + f.getFactValue())
                .collect(Collectors.joining("\n", "以下是用户明确保存的长期记忆：\n", ""));
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,，。！？；：:!?;]+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private int relevance(MemoryFact fact, Set<String> terms) {
        String haystack = (fact.getFactKey() + " " + fact.getFactValue()).toLowerCase(Locale.ROOT);
        return (int) terms.stream().filter(haystack::contains).count();
    }
}
