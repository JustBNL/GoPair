package com.gopair.chatservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.chatservice.config.ChatProperties;
import com.gopair.chatservice.config.ChatWebSocketProducer;
import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
import com.gopair.chatservice.domain.po.PrivateMessage;
import com.gopair.chatservice.domain.vo.ConversationVO;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.chatservice.enums.ChatErrorCode;
import com.gopair.chatservice.enums.PrivateMessageType;
import com.gopair.chatservice.exception.ChatException;
import com.gopair.chatservice.mapper.FriendMapper;
import com.gopair.chatservice.mapper.PrivateMessageMapper;
import com.gopair.chatservice.service.PrivateMessageService;
import com.gopair.chatservice.service.UserProfileFallbackService;
import com.gopair.common.core.PageResult;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊消息服务实现类。
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl implements PrivateMessageService {

    private final PrivateMessageMapper privateMessageMapper;
    private final FriendMapper friendMapper;
    private final ChatWebSocketProducer chatWebSocketProducer;
    private final UserProfileFallbackService userProfileFallbackService;
    private final ChatProperties chatProperties;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long CONVERSATION_ID_MULTIPLIER = 1_000_000_0000L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "发送私聊消息", module = "私聊消息")
    public PrivateMessageVO sendMessage(SendPrivateMessageDto dto, Long senderId) {
        Long receiverId = dto.getReceiverId();

        boolean isFriend = friendMapper.isFriend(senderId, receiverId);
        if (!isFriend) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.NOT_FRIENDS);
        }

        PrivateMessageType msgType = PrivateMessageType.fromCode(dto.getMessageType());
        if (msgType == null) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.MESSAGE_TYPE_INVALID);
        }

        if (msgType == PrivateMessageType.TEXT) {
            if (!StringUtils.hasText(dto.getContent())) {
                throw new ChatException(ChatErrorCode.MESSAGE_CONTENT_EMPTY);
            }
            if (dto.getContent().length() > chatProperties.getMaxContentLength()) {
                throw new ChatException(ChatErrorCode.CONTENT_TOO_LONG);
            }
        }

        if ((msgType == PrivateMessageType.IMAGE || msgType == PrivateMessageType.FILE)
                && !StringUtils.hasText(dto.getFileUrl())) {
            throw new ChatException(ChatErrorCode.FILE_URL_EMPTY);
        }

        Long conversationId = computeConversationId(senderId, receiverId);

        PrivateMessage message = BeanCopyUtils.copyBean(dto, PrivateMessage.class);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        privateMessageMapper.insert(message);

        PrivateMessageVO vo = toVO(message, senderId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", message.getId());
        payload.put("conversationId", conversationId);
        payload.put("senderId", senderId);
        payload.put("receiverId", receiverId);
        payload.put("messageType", dto.getMessageType());
        payload.put("content", dto.getContent());
        payload.put("fileUrl", dto.getFileUrl());
        payload.put("fileName", dto.getFileName());
        payload.put("fileSize", dto.getFileSize());
        payload.put("createTime", message.getCreateTime() != null ? message.getCreateTime().format(DF) : null);

        chatWebSocketProducer.sendPrivateMessage(receiverId, payload);
        chatWebSocketProducer.sendPrivateMessage(senderId, payload);

        log.info("发送私聊消息: senderId={}, receiverId={}, messageId={}", senderId, receiverId, message.getId());
        return vo;
    }

    @Override
    @LogRecord(operation = "获取会话列表", module = "私聊消息")
    public List<ConversationVO> getConversations(Long userId) {
        List<Long> conversationIds = privateMessageMapper.selectConversationIdsByUser(userId);
        if (conversationIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return conversationIds.stream().map(convId -> {
            PrivateMessageVO latest = privateMessageMapper.selectLatestMessageByConversation(convId);
            if (latest == null) {
                return null;
            }

            Long friendId = latest.getSenderId().equals(userId) ? latest.getReceiverId() : latest.getSenderId();

            ConversationVO vo = new ConversationVO();
            vo.setConversationId(convId);
            vo.setFriendId(friendId);
            vo.setLastMessageType(latest.getMessageType());
            vo.setLastMessageContent(truncate(latest.getContent(), 30));
            vo.setLastMessageTime(latest.getCreateTime() != null ? latest.getCreateTime().format(DF) : null);
            vo.setMessageCount(privateMessageMapper.countByConversation(convId));

            List<Long> ids = List.of(friendId);
            List<com.gopair.chatservice.domain.vo.FriendVO> fakeList = List.of(new com.gopair.chatservice.domain.vo.FriendVO());
            userProfileFallbackService.fillMissingFriendProfiles(fakeList, ids);

            return vo;
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    @LogRecord(operation = "获取会话消息历史", module = "私聊消息")
    public PageResult<PrivateMessageVO> getMessages(Long conversationId, int pageNum, int pageSize, Long currentUserId) {
        if (pageSize > chatProperties.getMaxMessagePageSize()) {
            pageSize = chatProperties.getMaxMessagePageSize();
        }

        Page<PrivateMessageVO> page = new Page<>(pageNum, pageSize);
        var result = privateMessageMapper.selectMessageVOPage(page, conversationId);

        result.getRecords().forEach(vo -> vo.setIsOwn(vo.getSenderId().equals(currentUserId)));

        return new PageResult<>(
                result.getRecords(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "删除私聊消息", module = "私聊消息")
    public void deleteMessage(Long messageId, Long currentUserId) {
        PrivateMessage message = privateMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.PRIVATE_MESSAGE_NOT_FOUND);
        }
        if (!message.getSenderId().equals(currentUserId)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.NO_PERMISSION_DELETE_MESSAGE);
        }
        privateMessageMapper.deleteById(messageId);
        log.info("删除私聊消息: messageId={}, userId={}", messageId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "撤回私聊消息", module = "私聊消息")
    public void recallMessage(Long messageId, Long currentUserId) {
        PrivateMessage message = privateMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.PRIVATE_MESSAGE_NOT_FOUND);
        }
        if (!message.getSenderId().equals(currentUserId)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.NO_PERMISSION_RECALL_MESSAGE);
        }
        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.MESSAGE_ALREADY_RECALLED);
        }

        LocalDateTime limit = LocalDateTime.now().minusSeconds(chatProperties.getRecallTimeLimitSeconds());
        if (message.getCreateTime().isBefore(limit)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.RECALL_TIME_EXPIRED);
        }

        message.setIsRecalled(true);
        message.setRecalledAt(LocalDateTime.now());
        privateMessageMapper.updateById(message);

        Map<String, Object> payload = Map.of(
                "messageId", messageId,
                "conversationId", message.getConversationId(),
                "action", "recalled"
        );
        chatWebSocketProducer.sendPrivateMessage(message.getSenderId(), payload);
        chatWebSocketProducer.sendPrivateMessage(message.getReceiverId(), payload);

        log.info("撤回私聊消息: messageId={}, userId={}", messageId, currentUserId);
    }

    @Override
    public boolean isParticipant(Long conversationId, Long userId) {
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessage::getConversationId, conversationId)
                .and(w -> w.eq(PrivateMessage::getSenderId, userId)
                        .or()
                        .eq(PrivateMessage::getReceiverId, userId))
                .last("LIMIT 1");
        return privateMessageMapper.selectCount(wrapper) > 0;
    }

    private Long computeConversationId(Long userIdA, Long userIdB) {
        long min = Math.min(userIdA, userIdB);
        long max = Math.max(userIdA, userIdB);
        return min * CONVERSATION_ID_MULTIPLIER + max;
    }

    private PrivateMessageVO toVO(PrivateMessage msg, Long currentUserId) {
        PrivateMessageVO vo = BeanCopyUtils.copyBean(msg, PrivateMessageVO.class);
        vo.setIsOwn(msg.getSenderId().equals(currentUserId));
        PrivateMessageType t = PrivateMessageType.fromCode(msg.getMessageType());
        vo.setMessageTypeDesc(t != null ? t.getDescription() : "未知类型");
        return vo;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
