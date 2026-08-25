package com.agentsystem.user.service.impl;

import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.repository.UserRepository;
import com.agentsystem.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository      userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String EMAIL_CACHE_KEY_PREFIX = "user:email:";
    private static final Duration EMAIL_CACHE_TTL       = Duration.ofHours(3);

    @Override
    @Transactional
    public User registerOrGetPending(String email) {
        String normalised = email.trim().toLowerCase();
        return userRepository.findByEmail(normalised)
                .orElseGet(() -> {
                    User user = new User(UUID.randomUUID().toString(), normalised, UserStatus.PRE_USER, true);
                    User saved = userRepository.save(user);
                    log.info("[UserAccountService] Registered pending user uuid={}", saved.getUuid());
                    return saved;
                });
    }

    @Override
    public Optional<User> findActiveUser(String email) {
        String normalised = email.trim().toLowerCase();
        return userRepository.findByEmail(normalised)
                .filter(u -> u.getStatus() == UserStatus.USER && u.isEnabled());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    @Override
    public String getEmailByUuid(String uuid) {
        String cacheKey = EMAIL_CACHE_KEY_PREFIX + uuid;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        String email = userRepository.findByUuid(uuid)
                .map(User::getEmail)
                .orElse(null);
        if (email != null) {
            redisTemplate.opsForValue().set(cacheKey, email, EMAIL_CACHE_TTL);
        }
        return email;
    }
}
