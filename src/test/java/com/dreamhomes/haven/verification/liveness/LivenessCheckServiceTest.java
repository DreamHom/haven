package com.dreamhomes.haven.verification.liveness;

import com.dreamhomes.haven.verification.exception.LivenessCheckAlreadyConsumedException;
import com.dreamhomes.haven.verification.exception.LivenessCheckNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LivenessCheckServiceTest {

    @Mock LivenessCheckResultRepository repository;

    LivenessCheckService service;

    @BeforeEach
    void setUp() {
        service = new LivenessCheckService(repository);
    }

    @Test
    void runMockedCheckPersistsPassedRowAtFixedScoreFromMockProvider() {
        when(repository.save(any(LivenessCheckResult.class)))
                .thenAnswer(inv -> { LivenessCheckResult r = inv.getArgument(0); r.setId(42L); return r; });

        LivenessCheckResult result = service.runMockedCheck(50L);

        ArgumentCaptor<LivenessCheckResult> cap = ArgumentCaptor.forClass(LivenessCheckResult.class);
        verify(repository).save(cap.capture());
        LivenessCheckResult saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(50L);
        assertThat(saved.getStatus()).isEqualTo("PASSED");
        assertThat(saved.getScore()).isEqualByComparingTo(new BigDecimal("0.97"));
        assertThat(saved.getProviderName()).isEqualTo("MOCK");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(result).isSameAs(saved);
    }

    @Test
    void consumeMarksUnconsumedRowAndReturnsIt() {
        LivenessCheckResult row = LivenessCheckResult.builder()
                .id(42L).userId(50L).status("PASSED")
                .score(new BigDecimal("0.97")).providerName("MOCK")
                .createdAt(Instant.now()).build();
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        when(repository.save(any(LivenessCheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

        LivenessCheckResult consumed = service.consume(50L, 42L);

        assertThat(consumed.getConsumedAt()).isNotNull();
        verify(repository).save(row);
    }

    @Test
    void consumeRejectsForeignLivenessIdAs403() {
        LivenessCheckResult row = LivenessCheckResult.builder()
                .id(42L).userId(999L).status("PASSED")
                .score(new BigDecimal("0.97")).providerName("MOCK")
                .createdAt(Instant.now()).build();
        when(repository.findById(42L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.consume(50L, 42L))
                .isInstanceOf(LivenessCheckNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void consumeRejectsMissingLivenessIdAs403() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume(50L, 404L))
                .isInstanceOf(LivenessCheckNotFoundException.class);
    }

    @Test
    void consumeRejectsAlreadyConsumedRowAs409() {
        LivenessCheckResult row = LivenessCheckResult.builder()
                .id(42L).userId(50L).status("PASSED")
                .score(new BigDecimal("0.97")).providerName("MOCK")
                .createdAt(Instant.now().minusSeconds(60))
                .consumedAt(Instant.now().minusSeconds(30))
                .build();
        when(repository.findById(42L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.consume(50L, 42L))
                .isInstanceOf(LivenessCheckAlreadyConsumedException.class);

        verify(repository, never()).save(any());
    }
}
