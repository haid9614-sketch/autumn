package com.duong.autumn.service;

import com.duong.autumn.dto.request.SendMessageRequest;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.Conversation;
import com.duong.autumn.entity.Message;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.ConversationRepository;
import com.duong.autumn.repository.MessageRepository;
import com.duong.autumn.repository.UserBlockRepository;
import com.duong.autumn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ExpoPushService expoPushService;

    // 1. Mở hoặc tạo cuộc trò chuyện 1-1 với một người dùng (chống trùng phòng & kiểm tra chặn)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse openOrCreateConversation(Long currentUserId, Long partnerId) {
        if (partnerId == null || currentUserId.equals(partnerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể tạo cuộc trò chuyện với chính mình.");
        }

        userRepository.findActiveById(partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng này."));

        if (userBlockRepository.existsBlockBetween(currentUserId, partnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không thể nhắn tin với người dùng này.");
        }

        long u1 = Math.min(currentUserId, partnerId);
        long u2 = Math.max(currentUserId, partnerId);
        conversationRepository.ensureConversation(u1, u2, LocalDateTime.now());

        Conversation conversation = conversationRepository.findActiveByPair(u1, u2)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Không thể khởi tạo cuộc trò chuyện."));

        List<Message> latestList = messageRepository.findLatestMessagesByConversationIds(List.of(conversation.getId()));
        Message lastMessage = latestList.isEmpty() ? null : latestList.get(0);
        return toConversationResponse(conversation, currentUserId, lastMessage);
    }

    // 2. Lấy danh sách hộp thư theo Keyset Cursor (beforeUpdatedAt + beforeId)
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ConversationPageResponse getConversations(
            Long currentUserId,
            LocalDateTime beforeUpdatedAt,
            Long beforeId,
            int limit
    ) {
        validateLimit(limit);
        if ((beforeUpdatedAt == null) != (beforeId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cần truyền đồng thời cả beforeUpdatedAt và beforeId.");
        }

        List<Conversation> rows = conversationRepository.findInboxConversations(
                currentUserId, beforeUpdatedAt, beforeId, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<Conversation> visible = rows.subList(0, Math.min(limit, rows.size()));

        Map<Long, Message> latestMap = visible.isEmpty()
                ? Collections.emptyMap()
                : messageRepository.findLatestMessagesByConversationIds(
                        visible.stream().map(Conversation::getId).toList())
                .stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity(), (a, b) -> a));

        List<ConversationResponse> items = visible.stream()
                .map(c -> toConversationResponse(c, currentUserId, latestMap.get(c.getId())))
                .toList();

        LocalDateTime nextBeforeUpdatedAt = null;
        Long nextBeforeId = null;
        if (hasMore) {
            Conversation last = visible.get(visible.size() - 1);
            nextBeforeUpdatedAt = last.getUpdatedAt();
            nextBeforeId = last.getId();
        }

        return new ConversationPageResponse(items, nextBeforeUpdatedAt, nextBeforeId, hasMore);
    }

    // 3. Tải tin nhắn lần đầu hoặc cuộn lên tải lịch sử cũ hơn beforeId
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public MessagePageResponse getMessages(Long currentUserId, Long conversationId, Long beforeId, int limit) {
        validateLimit(limit);
        Conversation conversation = requireActiveParticipantConversation(currentUserId, conversationId);
        User partner = getPartner(conversation, currentUserId);
        if (userBlockRepository.existsBlockBetween(currentUserId, partner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không thể truy cập cuộc trò chuyện này.");
        }

        List<ChatMessageResponse> rows = messageRepository.findMessagesBefore(
                conversationId, beforeId, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<ChatMessageResponse> items = rows.subList(0, Math.min(limit, rows.size()));

        Long nextBeforeId = (hasMore && !items.isEmpty()) ? items.get(items.size() - 1).id() : null;
        Long latestId = items.isEmpty() ? null : items.get(0).id();

        return new MessagePageResponse(items, nextBeforeId, latestId, hasMore);
    }

    // 4. Polling lấy các tin nhắn mới hơn mốc afterId khi đang mở khung chat
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public MessagePageResponse pollNewMessages(Long currentUserId, Long conversationId, Long afterId, int limit) {
        validateLimit(limit);
        if (afterId == null || afterId < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "afterId phải là số nguyên không âm.");
        }

        Conversation conversation = requireActiveParticipantConversation(currentUserId, conversationId);
        User partner = getPartner(conversation, currentUserId);
        if (userBlockRepository.existsBlockBetween(currentUserId, partner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không thể truy cập cuộc trò chuyện này.");
        }

        List<ChatMessageResponse> rows = messageRepository.findMessagesAfter(
                conversationId, afterId, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<ChatMessageResponse> items = rows.subList(0, Math.min(limit, rows.size()));
        Long latestId = items.isEmpty() ? afterId : items.get(items.size() - 1).id();

        return new MessagePageResponse(items, null, latestId, hasMore);
    }

    // 5. Gửi tin nhắn Text và chỉ bắn Expo Push sau khi Database commit thành công
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChatMessageResponse sendMessage(Long currentUserId, Long conversationId, SendMessageRequest request) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty() || content.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nội dung tin nhắn phải từ 1 đến 2000 ký tự.");
        }

        Conversation conversation = requireActiveParticipantConversation(currentUserId, conversationId);
        User sender = getSelf(conversation, currentUserId);
        User partner = getPartner(conversation, currentUserId);

        if (!Integer.valueOf(0).equals(partner.getIsDelete())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Người nhận không còn tồn tại.");
        }
        if (userBlockRepository.existsBlockBetween(currentUserId, partner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không thể gửi tin nhắn vì một trong hai người đã chặn nhau.");
        }

        Message saved = messageRepository.saveAndFlush(Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .isDelete(0)
                .build());

        conversationRepository.touchUpdatedAt(conversation.getId(), LocalDateTime.now());

        Long recipientId = partner.getId();
        Long savedMessageId = saved.getId();
        String senderName = sender.getName();

        Runnable pushTask = () -> expoPushService.sendChatNotificationAsync(
                recipientId, conversation.getId(), savedMessageId, sender.getId(), senderName, content);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushTask.run();
                }
            });
        } else {
            pushTask.run();
        }

        return new ChatMessageResponse(
                saved.getId(),
                conversation.getId(),
                sender.getId(),
                sender.getName(),
                sender.getUsername(),
                sender.getAvatar(),
                saved.getContent(),
                saved.getCreatedAt()
        );
    }

    // 6. Xóa mềm tin nhắn (chỉ người gửi mới được xóa)
    @Transactional
    public void deleteMessage(Long currentUserId, Long messageId) {
        Message message = messageRepository.findActiveByIdWithRelations(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy tin nhắn."));

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xóa tin nhắn của người khác.");
        }

        message.setIsDelete(1);
        messageRepository.save(message);
    }

    private Conversation requireActiveParticipantConversation(Long currentUserId, Long conversationId) {
        Conversation conversation = conversationRepository.findActiveByIdWithUsers(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc trò chuyện."));

        boolean isParticipant = conversation.getUser1().getId().equals(currentUserId)
                || conversation.getUser2().getId().equals(currentUserId);
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không thuộc cuộc trò chuyện này.");
        }
        return conversation;
    }

    private User getSelf(Conversation conversation, Long currentUserId) {
        return conversation.getUser1().getId().equals(currentUserId)
                ? conversation.getUser1()
                : conversation.getUser2();
    }

    private User getPartner(Conversation conversation, Long currentUserId) {
        return conversation.getUser1().getId().equals(currentUserId)
                ? conversation.getUser2()
                : conversation.getUser1();
    }

    private ConversationResponse toConversationResponse(Conversation c, Long currentUserId, Message lastMessage) {
        User partner = getPartner(c, currentUserId);
        return new ConversationResponse(
                c.getId(),
                partner.getId(),
                partner.getName(),
                partner.getUsername(),
                partner.getAvatar(),
                lastMessage == null ? null : lastMessage.getId(),
                lastMessage == null ? null : lastMessage.getSender().getId(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Limit phải nằm trong khoảng 1 đến 50.");
        }
    }
}