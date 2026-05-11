package com.dreamhomes.haven.user.mapping;

import com.dreamhomes.haven.user.dto.UserAdminView;
import com.dreamhomes.haven.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAdminMapper {
    UserAdminView toView(User user);
}
