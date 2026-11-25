package com.duong.autumn.service;

import com.duong.autumn.dto.request.RegisterDeviceTokenRequest;
import com.duong.autumn.entity.DeviceToken;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.DeviceTokenRepository;
import com.duong.autumn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void registerToken(Long userId, RegisterDeviceTokenRequest request) {
        String token = request.getToken().trim();
        String model = request.getModel().trim();

        if (!isValidExpoPushToken(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expo Push Token không đúng định dạng (ExponentPushToken[...] hoặc ExpoPushToken[...]).");
        }

        User user = userRepository.getReferenceById(userId);
        List<DeviceToken> existing = deviceTokenRepository.findAllByFcmToken(token);

        if (existing.isEmpty()) {
            deviceTokenRepository.save(DeviceToken.builder()
                    .user(user)
                    .fcmToken(token)
                    .model(model)
                    .build());
            return;
        }

        // Gắn token thiết bị vật lý này về đúng tài khoản đang đăng nhập hiện tại
        DeviceToken primary = existing.get(0);
        primary.setUser(user);
        primary.setModel(model);
        deviceTokenRepository.save(primary);

        // Xóa các bản ghi trùng lặp nếu có
        if (existing.size() > 1) {
            deviceTokenRepository.deleteAll(existing.subList(1, existing.size()));
        }
    }

    @Transactional
    public void unregisterToken(Long userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByUserIdAndFcmToken(userId, rawToken.trim());
    }

    @Transactional
    public void unregisterAllMyTokens(Long userId) {
        deviceTokenRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<String> getActiveTokensForUser(Long userId) {
        return deviceTokenRepository.findByUserId(userId).stream()
                .map(DeviceToken::getFcmToken)
                .filter(this::isValidExpoPushToken)
                .distinct()
                .toList();
    }

    @Transactional
    public void deleteInvalidTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        deviceTokenRepository.deleteByFcmTokenIn(tokens);
    }

    private boolean isValidExpoPushToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 512) {
            return false;
        }
        return (token.startsWith("ExponentPushToken[") || token.startsWith("ExpoPushToken["))
                && token.endsWith("]");
    }
}