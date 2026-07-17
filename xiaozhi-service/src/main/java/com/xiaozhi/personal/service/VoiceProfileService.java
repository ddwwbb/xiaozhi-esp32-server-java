package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.VoiceProfile;
import com.xiaozhi.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoiceProfileService {

    private final PersonalFeatureMapper mapper;

    public VoiceProfile enroll(Integer userId, String displayName, String modelName, String modelVersion,
                               List<List<Double>> sampleEmbeddings, BigDecimal threshold) {
        if (userId == null || !StringUtils.hasText(displayName) || sampleEmbeddings == null
                || sampleEmbeddings.size() < 3) {
            throw new IllegalArgumentException("声纹注册至少需要 3 段有效样本");
        }
        int dimension = sampleEmbeddings.getFirst().size();
        if (dimension == 0 || sampleEmbeddings.stream().anyMatch(v -> v.size() != dimension)) {
            throw new IllegalArgumentException("声纹向量维度不一致");
        }
        double[] centroid = new double[dimension];
        for (List<Double> sample : sampleEmbeddings) {
            for (int i = 0; i < dimension; i++) {
                centroid[i] += sample.get(i);
            }
        }
        normalize(centroid, sampleEmbeddings.size());

        VoiceProfile profile = new VoiceProfile();
        profile.setUserId(userId);
        profile.setDisplayName(displayName.trim());
        profile.setModelName(StringUtils.hasText(modelName) ? modelName : "speaker-embedding-onnx");
        profile.setModelVersion(StringUtils.hasText(modelVersion) ? modelVersion : "v1");
        profile.setEmbeddingDimension(dimension);
        profile.setCentroidEmbedding(JsonUtil.toJson(centroid));
        profile.setThresholdValue(threshold == null ? new BigDecimal("0.72") : threshold);
        mapper.insertVoiceProfile(profile);
        return profile;
    }

    public List<VoiceProfile> list(Integer userId) {
        return userId == null ? List.of() : mapper.listVoiceProfiles(userId);
    }

    public MatchResult identify(Integer userId, List<Double> embedding) {
        if (userId == null || embedding == null || embedding.isEmpty()) {
            return MatchResult.unknownResult();
        }
        double[] candidate = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        normalize(candidate, 1);
        VoiceProfile best = null;
        double bestScore = -1;
        double secondScore = -1;
        for (VoiceProfile profile : mapper.listVoiceProfiles(userId)) {
            if (!Integer.valueOf(candidate.length).equals(profile.getEmbeddingDimension())) {
                continue;
            }
            double[] centroid = JsonUtil.fromJson(profile.getCentroidEmbedding(), double[].class);
            double score = cosine(candidate, centroid);
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = profile;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best == null || bestScore < best.getThresholdValue().doubleValue() || bestScore - secondScore < 0.03) {
            return MatchResult.unknownResult();
        }
        return new MatchResult(best.getProfileId(), best.getDisplayName(), bestScore, false);
    }

    public int delete(Integer userId, Long profileId) {
        return mapper.deleteVoiceProfile(profileId, userId);
    }

    private void normalize(double[] vector, int divisor) {
        double norm = 0;
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= divisor;
            norm += vector[i] * vector[i];
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            throw new IllegalArgumentException("声纹向量不能为零向量");
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private double cosine(double[] a, double[] b) {
        double score = 0;
        for (int i = 0; i < a.length; i++) {
            score += a[i] * b[i];
        }
        return score;
    }

    public record MatchResult(Long profileId, String displayName, double score, boolean unknown) {
        public static MatchResult unknownResult() {
            return new MatchResult(null, "UNKNOWN", 0, true);
        }
    }
}
