package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.common.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public discovery endpoints carry {@code Cache-Control} so a CDN or browser can serve
 * repeat hits without round-tripping to the database. We own the path patterns and the
 * header value via {@link PublicCacheHeadersInterceptor} — the interceptor mechanism
 * itself is Spring and not retested here.
 */
@AutoConfigureMockMvc
class PublicCacheHeadersIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicListingsBrowseSetsCacheControl() throws Exception {
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        "public, max-age=60, stale-while-revalidate=300"));
    }

    @Test
    void publicListingDetailWith404StillSetsCacheControlOnPath() throws Exception {
        // The interceptor runs preHandle so headers land on every response under the
        // matched paths, including not-found responses — caches benefit from "no, this
        // listing doesn't exist" being short-cached too.
        mockMvc.perform(get("/api/listings/99999"))
                .andExpect(header().string("Cache-Control",
                        "public, max-age=60, stale-while-revalidate=300"));
    }

}
