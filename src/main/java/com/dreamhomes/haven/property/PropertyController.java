package com.dreamhomes.haven.property;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PropertyResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                                   @Valid @RequestBody CreatePropertyRequest request) {
        Property saved = propertyService.create(principal.userId(), request.toCommand());
        return new PropertyResponse(saved.getId(), saved.getOwnerId(), saved.getType(),
                saved.getAddress(), saved.getBedrooms(), saved.getBathrooms(),
                saved.getSizeSqm(), saved.getDescription(), saved.getCreatedAt());
    }
}
