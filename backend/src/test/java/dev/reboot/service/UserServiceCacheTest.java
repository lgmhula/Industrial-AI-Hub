package dev.reboot.service;

import dev.reboot.config.CacheConfig;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserServiceCacheTest.CacheTestConfig.class)
class UserServiceCacheTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.CACHE_USER_DETAIL);
        }

        @Bean
        UserMapper userMapper() { return mock(UserMapper.class); }

        @Bean
        UserRoleMapper userRoleMapper() { return mock(UserRoleMapper.class); }

        @Bean
        BCryptPasswordEncoder passwordEncoder() { return mock(BCryptPasswordEncoder.class); }

        @Bean
        AuthRateLimitService authRateLimitService() { return mock(AuthRateLimitService.class); }

        @Bean
        TokenBlacklistService tokenBlacklistService() { return mock(TokenBlacklistService.class); }

        @Bean
        UserService userService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                BCryptPasswordEncoder passwordEncoder,
                                AuthRateLimitService authRateLimitService,
                                TokenBlacklistService tokenBlacklistService) {
            return new UserService(userMapper, userRoleMapper, passwordEncoder,
                    authRateLimitService, tokenBlacklistService);
        }
    }

    private User newUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPhone("13800138000");
        u.setStatus(1);
        u.setPassword("encoded-pw");
        u.setIsDeleted(0);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    @Test
    void getById_shouldHitCacheOnSecondCall() {
        User user = newUser(301L, "alice");
        when(userMapper.findById(301L)).thenReturn(user);

        UserVO first = userService.getById(301L);
        UserVO second = userService.getById(301L);

        assertEquals("alice", first.getUsername());
        assertEquals("alice", second.getUsername());
        verify(userMapper, times(1)).findById(301L);
    }

    @Test
    void getById_differentIds_shouldNotShareCache() {
        User u302 = newUser(302L, "alice");
        User u303 = newUser(303L, "bob");
        when(userMapper.findById(302L)).thenReturn(u302);
        when(userMapper.findById(303L)).thenReturn(u303);

        userService.getById(302L);
        userService.getById(303L);

        verify(userMapper, times(1)).findById(302L);
        verify(userMapper, times(1)).findById(303L);
    }

    @Test
    void update_shouldEvictCache() {
        User user = newUser(304L, "alice");
        when(userMapper.findById(304L)).thenReturn(user);

        userService.getById(304L);
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("new@test.com");
        dto.setPhone("13900139000");
        userService.update(304L, dto);
        userService.getById(304L);

        // 3 calls: initial getById, update's internal findById, post-eviction getById
        verify(userMapper, times(3)).findById(304L);
    }

    @Test
    void toggleStatus_shouldEvictCache() {
        User user = newUser(305L, "alice");
        when(userMapper.findById(305L)).thenReturn(user);

        userService.getById(305L);
        userService.toggleStatus(305L);
        userService.getById(305L);

        // 3 calls
        verify(userMapper, times(3)).findById(305L);
    }

    @Test
    void delete_shouldEvictCache() {
        User user = newUser(306L, "alice");
        when(userMapper.findById(306L)).thenReturn(user);
        when(userMapper.softDeleteById(306L)).thenReturn(1);

        userService.getById(306L);
        userService.delete(306L, 999L);
        userService.getById(306L);

        // 3 calls
        verify(userMapper, times(3)).findById(306L);
    }
}
