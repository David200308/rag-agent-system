package com.agentsystem.auth.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Same Redis cache key convention as agent-system-rest's UserAccountServiceImpl
 * ({@code user:email:<uuid>}, 3h TTL) — both point at the same Redis instance, so
 * entries written by one side are usable by the other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserServiceImpl implements AuthUserService {

    private final AuthUserRepository  userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String   EMAIL_CACHE_KEY_PREFIX = "user:email:";
    private static final Duration EMAIL_CACHE_TTL         = Duration.ofHours(3);

    @Override
    @Transactional
    public AuthUser registerOrGetPending(String email) {
        String normalised = email.trim().toLowerCase();
        return userRepository.findByEmail(normalised)
                .orElseGet(() -> {
                    AuthUser user = new AuthUser(UUID.randomUUID().toString(), normalised, "PRE_USER", true);
                    AuthUser saved = userRepository.save(user);
                    log.info("[AuthUserService] Registered pending user uuid={}", saved.getUuid());
                    return saved;
                });
    }

    @Override
    public Optional<AuthUser> findActiveUser(String email) {
        String normalised = email.trim().toLowerCase();
        return userRepository.findByEmail(normalised)
                .filter(u -> "USER".equals(u.getStatus()) && u.isEnabled());
    }

    @Override
    public Optional<AuthUser> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    @Override
    public String getEmailByUuid(String uuid) {
        String cacheKey = EMAIL_CACHE_KEY_PREFIX + uuid;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        String email = userRepository.findByUuid(uuid)
                .map(AuthUser::getEmail)
                .orElse(null);
        if (email != null) {
            redisTemplate.opsForValue().set(cacheKey, email, EMAIL_CACHE_TTL);
        }
        return email;
    }
}
