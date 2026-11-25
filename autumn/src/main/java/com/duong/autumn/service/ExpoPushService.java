package com.duong.autumn.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private static final URI EXPO_PUSH_URI = URI.create("https://exp.host/--/api/v2/push/send");
    private static final int MAX_BODY_PREVIEW_LENGTH = 120;

    private final ObjectMapper objectMapper;
    private final DeviceTokenService deviceTokenService;

    // Tùy chọn: chỉ cần thêm vào application.properties nếu bật Enhanced Push Security trên Expo.
    @Value("${expo.push.access-token:}")
    private String expoAccessToken;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ExecutorService pushExecutor = Executors.newFixedThreadPool(2);

    public void sendChatNotificationAsync(
            Long recipientId,
            Long conversationId,
            Long messageId,
            Long senderId,
            String senderName,
            String messageContent
    ) {
        pushExecutor.submit(() -> {
            try {
                List<String> tokens = deviceTokenService.getActiveTokensForUser(recipientId);
                if (tokens.isEmpty()) {
                    return;
                }

                String previewBody = truncateSafely(messageContent, MAX_BODY_PREVIEW_LENGTH);
                var payload = new ArrayList<>(tokens.size());
                for (String token : tokens) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("to", token);
                    message.put("sound", "default");
                    message.put("title", senderName);
                    message.put("body", previewBody);
                    message.put("data", Map.of(
                            "type", "CHAT_MESSAGE",
                            "conversationId", conversationId,
                            "messageId", messageId,
                            "senderId", senderId,
                            "senderName", senderName
                    ));
                    payload.add(message);
                }

                String jsonBody = objectMapper.writeValueAsString(payload);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(EXPO_PUSH_URI)
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

                if (expoAccessToken != null && !expoAccessToken.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + expoAccessToken.trim());
                }

                HttpResponse<String> response = httpClient.send(
                        reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Expo Push trả mã HTTP {}: {}", response.statusCode(), response.body());
                    return;
                }

                cleanupDeadTokens(tokens, response.body());
            } catch (Exception ex) {
                log.warn("Không thể gửi Expo Push cho userId={}: {}", recipientId, ex.getMessage());
            }
        });
    }

    private void cleanupDeadTokens(List<String> sentTokens, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return;
            }
            List<String> deadTokens = new ArrayList<>();
            int count = Math.min(sentTokens.size(), data.size());
            for (int i = 0; i < count; i++) {
                JsonNode ticket = data.get(i);
                if ("error".equals(ticket.path("status").asString(""))) {
                    String errorCode = ticket.path("details").path("error").asString("");
                    if ("DeviceNotRegistered".equals(errorCode)) {
                        deadTokens.add(sentTokens.get(i));
                    } else {
                        log.warn("Expo Push lỗi gửi token index {}: {} ({})",
                                i, ticket.path("message").asString(""), errorCode);
                    }
                }
            }
            if (!deadTokens.isEmpty()) {
                deviceTokenService.deleteInvalidTokens(deadTokens);
                log.info("Đã tự động dọn {} Expo Push token không còn đăng ký.", deadTokens.size());
            }
        } catch (Exception ex) {
            log.warn("Không phân tích được phản hồi từ Expo Push: {}", ex.getMessage());
        }
    }

    // Cắt ngắn chuỗi an toàn, không cắt đôi cặp surrogate của Emoji.
    private String truncateSafely(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + "...";
    }

    @PreDestroy
    public void shutdown() {
        pushExecutor.shutdown();
    }
}