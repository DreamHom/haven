package com.dreamhomes.haven.listing.embedding;

import com.pgvector.PGvector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Item 22 (post-session-tasks.md) — unit-level verification of the distance-bounded
 * {@code nearestLive} overload. We mock {@link JdbcTemplate} because the cosine-distance
 * predicate semantics belong to Postgres + pgvector; the only thing we own and need to
 * test here is that the SQL parameters get the right values (including {@code maxDistance})
 * and that the bounded overload uses the SQL that carries the distance predicate.
 */
@ExtendWith(MockitoExtension.class)
class ListingSearchEmbeddingStoreTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void nearestLiveWithMaxDistancePassesThresholdToQuery() {
        ListingSearchEmbeddingStore store = new ListingSearchEmbeddingStore(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of(7L, 3L));

        List<Long> ids = store.nearestLive(new float[]{0.1f, 0.2f, 0.3f}, 5, 0.5);

        assertThat(ids).containsExactly(7L, 3L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> arg1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg2 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg3 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg4 = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class),
                arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture());
        // SQL must carry the cost-defence cutoff predicate.
        assertThat(sql.getValue()).contains("(e.embedding <=> ?) < ?");
        // Param order: query vector, threshold, query vector again (for ORDER BY), limit.
        assertThat(arg1.getValue()).isInstanceOf(PGvector.class);
        assertThat(arg2.getValue()).isEqualTo(0.5);
        assertThat(arg3.getValue()).isInstanceOf(PGvector.class);
        assertThat(arg4.getValue()).isEqualTo(5);
    }

    @Test
    void nearestLiveLegacyOverloadHasNoThresholdPredicate() {
        ListingSearchEmbeddingStore store = new ListingSearchEmbeddingStore(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(1L));

        store.nearestLive(new float[]{0.1f}, 3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(), any());
        assertThat(sql.getValue()).doesNotContain("(e.embedding <=> ?) <");
    }
}
