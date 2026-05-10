package com.dreamhomes.haven.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.service.UserProfileService;

/**
 * Public profile endpoint — no JWT required. Cached at the gateway / CDN layer via
 * {@code Cache-Control}, set by {@code PublicCacheHeadersInterceptor} (registered on
 * this path in {@code WebConfig}).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{id}/profile")
    public PublicUserProfile getPublicProfile(@PathVariable Long id) {
        return userProfileService.findPublicProfile(id);
    }
}
