package com.dreamhomes.haven.verification.mapping;

import com.dreamhomes.haven.verification.dto.VerificationAdminView;
import com.dreamhomes.haven.verification.model.Verification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VerificationAdminMapper {
    VerificationAdminView toView(Verification verification);
}
