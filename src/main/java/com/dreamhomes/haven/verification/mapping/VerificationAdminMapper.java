package com.dreamhomes.haven.verification.mapping;

import com.dreamhomes.haven.verification.dto.VerificationAdminView;
import com.dreamhomes.haven.verification.model.Verification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VerificationAdminMapper {

    /**
     * Maps to the admin view without the {@code automatedChecks} list. The admin service
     * passes that list in separately because the source data lives in a sibling
     * aggregate ({@code verification_automation_results}) — see Item 20.
     */
    @Mapping(target = "automatedChecks", ignore = true)
    VerificationAdminView toView(Verification verification);
}
