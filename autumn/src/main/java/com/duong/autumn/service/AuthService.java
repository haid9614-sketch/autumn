package com.duong.autumn.service;

import com.duong.autumn.dto.request.*;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.TrendingFeedSessionRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final TrendingFeedSessionRepository trendingFeedSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username này đã có người sử dụng.");
        }
        User user = User.builder()
                .name(request.getName()).email(request.getEmail())
                .username(request.getUsername()).dateOfBirth(request.getDateOfBirth())
                .avatar("https://i.imgur.com/default-avatar.png")
                .password(passwordEncoder.encode(request.getPassword()))
                .tokenVersion(0)
                .build();
        userRepository.save(user);
        return new MessageResponse("Đăng ký tài khoản thành công");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication result;
        try {
            result = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Email hoặc mật khẩu không chính xác, hoặc tài khoản đã bị khóa.");
        }
        if (!(result.getPrincipal() instanceof CustomUserDetails details) || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không thể đăng nhập tài khoản này.");
        }
        User user = details.getUser();
        String family = UUID.randomUUID().toString();
        JwtService.TokenPair pair = jwtService.issueTokenPair(details, family);
        int stored = userRepository.storeRefreshTokenOnLogin(user.getId(), version(user), user.getPassword(),
                hash(pair.refreshToken()), family, pair.refreshExpiresAt());
        if (stored != 1) throw invalidSession();
        return response(user, pair);
    }

    // Trả 401 sau khi phát hiện replay vẫn phải COMMIT việc thu hồi phiên.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        Claims claims;
        try {
            claims = jwtService.parseRefreshClaims(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidSession();
        }
        long userId = jwtService.refreshUserId(claims);
        int expectedVersion = jwtService.tokenVersion(claims, false);
        String family = claims.get("refreshFamily", String.class);
        User user = userRepository.findActiveById(userId).orElseThrow(AuthService::invalidSession);
        if (!user.getEmail().equals(claims.getSubject()) || version(user) != expectedVersion
                || !family.equals(user.getRefreshTokenFamily())) {
            throw invalidSession();
        }

        JwtService.TokenPair pair = jwtService.issueTokenPair(new CustomUserDetails(user), family);
        String oldHash = hash(token);
        Instant now = Instant.now();
        int rotated = userRepository.rotateRefreshToken(userId, expectedVersion, family, oldHash,
                hash(pair.refreshToken()), pair.refreshExpiresAt(), now);
        if (rotated != 1) {
            // Family khác (đăng nhập mới) hoặc version đã tăng không bị nhánh này thu hồi nhầm.
            if (userRepository.revokeOnRefreshReplay(userId, expectedVersion, family, oldHash, now) == 1) {
                trendingFeedSessionRepository.releaseAllActiveByUserId(userId, now);
            }
            throw invalidSession();
        }
        return response(user, pair);
    }

    // Đăng xuất: Vô hiệu hóa JWT hiện tại và đóng toàn bộ phiên Trending đang mở của user
    @Transactional
    public MessageResponse logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getId() == null || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }

        Long userId = details.getId();
        if (userRepository.revokeTokensIfVersionMatches(userId, version(details.getUser())) != 1) {
            throw invalidSession();
        }
        trendingFeedSessionRepository.releaseAllActiveByUserId(userId, Instant.now());
        SecurityContextHolder.clearContext();

        return new MessageResponse("Đăng xuất thành công");
    }

    private AuthResponse response(User user, JwtService.TokenPair pair) {
        return AuthResponse.builder().token(pair.accessToken()).refreshToken(pair.refreshToken())
                .accessTokenExpiresAt(pair.accessExpiresAt()).refreshTokenExpiresAt(pair.refreshExpiresAt())
                .id(user.getId()).email(user.getEmail()).username(user.getUsername()).build();
    }

    private int version(User user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    private static ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại.");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng.", exception);
        }
    }
}
