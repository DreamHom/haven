package com.dreamhomes.haven.domain.user.service;

import com.dreamhomes.haven.domain.user.dto.LoginRequest;
import com.dreamhomes.haven.domain.user.dto.RegisterRequest;
import com.dreamhomes.haven.domain.user.model.User;
import com.dreamhomes.haven.domain.user.repository.UserRepository;
import com.dreamhomes.haven.exception.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }
        
        var user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setDisplayName(req.firstName());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public void authenticate(LoginRequest req) {
  
    }
}

