package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.AuthDto;
import com.paicoding.paiswitch.domain.dto.UserDto;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.UserStatus;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserConfigRepository userConfigRepository;
    private final ModelProviderRepository modelProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthDto.LoginResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ResponseCode.USER_ALREADY_EXISTS, "Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ResponseCode.USER_ALREADY_EXISTS, "Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        ModelProvider defaultProvider = modelProviderRepository.findByCode("claude")
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND, "Default provider not found"));

        UserConfig config = UserConfig.builder()
                .user(user)
                .currentProvider(defaultProvider)
                .apiTimeout(600000)
                .build();
        userConfigRepository.save(config);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(mapToUserInfo(user))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BusinessException(ResponseCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResponseCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "Account is not active");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .user(mapToUserInfo(user))
                .build();
    }

    private UserDto.UserInfo mapToUserInfo(User user) {
        return UserDto.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
