package com.dreamhomes.haven.user.mapping;

import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserCredentialsMapper {
    /**
     * {@code suspended} isn't a column on {@link User} — it's derived from
     * {@code suspendedAt}. MapStruct can't infer that, so we spell it out.
     */
    @Mapping(target = "suspended", expression = "java(user.getSuspendedAt() != null)")
    UserCredentials toCredentials(User user);
}
