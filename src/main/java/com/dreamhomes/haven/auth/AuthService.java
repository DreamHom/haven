package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String login(LoginCommand cmd) {
        User user = userRepository.findByEmail(cmd.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(cmd.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return jwtService.issue(user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional
    public User register(RegisterCommand cmd) {
        if (userRepository.existsByEmail(cmd.email())) {
            throw new EmailAlreadyRegisteredException(cmd.email());
        }
        User user = User.builder()
                .email(cmd.email())
                .passwordHash(passwordEncoder.encode(cmd.password()))
                .role(cmd.role())
                .fullName(cmd.fullName())
                .phone(cmd.phone())
                .createdAt(Instant.now())
                .build();
        return userRepository.save(user);
    }
}
