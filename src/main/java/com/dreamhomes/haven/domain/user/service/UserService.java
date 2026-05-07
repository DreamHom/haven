package com.dreamhomes.haven.domain.user.service;

import com.dreamhomes.haven.domain.user.dto.UpdateProfileRequest;
import com.dreamhomes.haven.domain.user.model.User;
import com.dreamhomes.haven.domain.user.repository.UserRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        var user = getById(userId);
        user.setDisplayName(req.displayName());
        return userRepository.save(user);
    }
}

