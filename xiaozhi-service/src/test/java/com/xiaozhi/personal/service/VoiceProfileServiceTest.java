package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.VoiceProfile;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceProfileServiceTest {

    private final PersonalFeatureMapper mapper = mock(PersonalFeatureMapper.class);
    private final VoiceProfileService service = new VoiceProfileService(mapper);

    @Test
    void identifiesClearBestMatch() {
        when(mapper.listVoiceProfiles(7)).thenReturn(List.of(
                profile(1L, "张三", new double[]{1, 0}, "0.70"),
                profile(2L, "李四", new double[]{-1, 0}, "0.70")));

        VoiceProfileService.MatchResult result = service.identify(7, List.of(0.99, 0.01));

        assertThat(result.unknown()).isFalse();
        assertThat(result.profileId()).isEqualTo(1L);
        assertThat(result.displayName()).isEqualTo("张三");
    }

    @Test
    void rejectsAmbiguousMatchEvenWhenThresholdPasses() {
        when(mapper.listVoiceProfiles(7)).thenReturn(List.of(
                profile(1L, "张三", new double[]{1, 0}, "0.70"),
                profile(2L, "李四", new double[]{0.999, 0.04}, "0.70")));

        VoiceProfileService.MatchResult result = service.identify(7, List.of(1.0, 0.0));

        assertThat(result.unknown()).isTrue();
    }

    private VoiceProfile profile(Long id, String name, double[] centroid, String threshold) {
        VoiceProfile profile = new VoiceProfile();
        profile.setProfileId(id);
        profile.setDisplayName(name);
        profile.setEmbeddingDimension(centroid.length);
        profile.setCentroidEmbedding(JsonUtil.toJson(centroid));
        profile.setThresholdValue(new BigDecimal(threshold));
        return profile;
    }
}
