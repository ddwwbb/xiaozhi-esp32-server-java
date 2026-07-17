package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryServiceTest {

    @Test
    void allowsUpdatingExistingKeyAtPersonalLimit() {
        PersonalFeatureMapper mapper = mock(PersonalFeatureMapper.class);
        when(mapper.countActiveMemoryByKey(7, 0, "profile", "city")).thenReturn(1);
        when(mapper.countActiveMemories(7)).thenReturn(500);
        LongTermMemoryService service = new LongTermMemoryService(mapper, Clock.systemUTC());

        assertThatCode(() -> service.save(7, null, "profile", "city", "杭州",
                BigDecimal.ONE, null, null)).doesNotThrowAnyException();
        verify(mapper).upsertMemory(any());
    }
}
