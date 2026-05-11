package com.dreamhomes.haven.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mintsRequestIdWhenHeaderAbsentAndStampsItIntoResponseAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Capture the MDC value mid-chain — once doFilter returns, the filter clears it.
        String[] seenInChain = new String[1];
        filter.doFilter(request, response, (req, res) -> {
            seenInChain[0] = MDC.get(RequestIdFilter.MDC_KEY);
        });

        assertThat(seenInChain[0]).isNotBlank().hasSize(36); // UUID format
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seenInChain[0]);
    }

    @Test
    void honoursIncomingRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "preset-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenInChain = new String[1];
        filter.doFilter(request, response, (req, res) ->
                seenInChain[0] = MDC.get(RequestIdFilter.MDC_KEY));

        assertThat(seenInChain[0]).isEqualTo("preset-correlation-id");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("preset-correlation-id");
    }

    @Test
    void clearsMdcAfterChainCompletesEvenWhenChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("downstream blew up");
            });
        } catch (RuntimeException expected) {
            // expected
        }

        // MDC must not leak into the next request handled by the same thread (thread-pool reuse).
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void blankIncomingHeaderIsTreatedAsAbsentAndIdMinted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotBlank()
                .hasSize(36);
    }
}
