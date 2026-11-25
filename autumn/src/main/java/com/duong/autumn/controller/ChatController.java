package com.duong.autumn.controller;

import com.duong.autumn.dto.request.SendMessageRequest;
import com.duong.autumn.dto.response.ConversationPageResponse;
import com.duong.autumn.dto.response.ConversationResponse;
import com.duong.autumn.dto.response.MessagePageResponse;
import com.duong.autumn.dto.response.ChatMessageResponse;
import com.duong.autumn.security.CustomUserDetails;
import com.duong.autumn.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/conversations/{partnerId}")
    public ResponseEntity<ConversationResponse> openOrCreateConversation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long partnerId
    ) {
        return ResponseEntity.ok(chatService.openOrCreateConversation(userDetails.getId(), partnerId));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ConversationPageResponse> getConversations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeUpdatedAt,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(chatService.getConversations(
                userDetails.getId(), beforeUpdatedAt, beforeId, limit));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessagePageResponse> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(chatService.getMessages(
                userDetails.getId(), conversationId, beforeId, limit));
    }

    @GetMapping("/conversations/{conversationId}/messages/new")
    public ResponseEntity<MessagePageResponse> pollNewMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId,
            @RequestParam Long afterId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(chatService.pollNewMessages(
                userDetails.getId(), conversationId, afterId, limit));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        ChatMessageResponse response = chatService.sendMessage(userDetails.getId(), conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long messageId
    ) {
        chatService.deleteMessage(userDetails.getId(), messageId);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tin nhắn."));
    }
}
