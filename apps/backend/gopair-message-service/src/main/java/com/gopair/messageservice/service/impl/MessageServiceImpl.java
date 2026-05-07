package com.gopair.messageservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.messageservice.config.MessageProperties;
import com.gopair.messageservice.config.RestTemplateProperties;
import com.gopair.messageservice.domain.dto.MessageQueryDto;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.event.MessageSentEvent;
import com.gopair.messageservice.domain.po.Message;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.enums.MessageType;
import com.gopair.messageservice.enums.MessageErrorCode;
import com.gopair.messageservice.exception.MessageException;
import com.gopair.messageservice.mapper.MessageMapper;
import com.gopair.messageservice.service.MessageService;
import com.gopair.messageservice.service.UserProfileFallbackService;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.logging.annotation.LogRecord;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息服务实现类
 *
 * @author gopair
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private static final String ROOM_SERVICE_URL = "http://room-service/room/";

    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WebSocketMessageProducer webSocketMessageProducer;
    private final MessageProperties messageProperties;
    private final UserProfileFallbackService userProfileFallbackService;
    private final RestTemplate restTemplate;
    private final RestTemplateProperties restTemplateProperties;

    public MessageServiceImpl(MessageMapper messageMapper, ApplicationEventPublisher eventPublisher,
                              WebSocketMessageProducer webSocketMessageProducer,
                              MessageProperties messageProperties,
                              UserProfileFallbackService userProfileFallbackService,
                              RestTemplate restTemplate,
                              RestTemplateProperties restTemplateProperties) {
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
        this.webSocketMessageProducer = webSocketMessageProducer;
        this.messageProperties = messageProperties;
        this.userProfileFallbackService = userProfileFallbackService;
        this.restTemplate = restTemplate;
        this.restTemplateProperties = restTemplateProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "发送消息", module = "消息管理", includeResult = true)
    public MessageVO sendMessage(SendMessageDto sendMessageDto, Long senderId) {
        log.info("发送消息开始, 发送者ID: {}, 房间ID: {}, 消息类型: {}, 内容: {}", 
                 senderId, sendMessageDto.getRoomId(), sendMessageDto.getMessageType(), sendMessageDto.getContent());
        
        try {
            // 检查用户是否在房间内
            if (!checkUserInRoom(sendMessageDto.getRoomId(), senderId)) {
                throw new MessageException(MessageErrorCode.USER_NOT_IN_ROOM);
            }

            // 验证消息类型
            MessageType messageType = MessageType.fromValue(sendMessageDto.getMessageType());
            if (messageType == null) {
                throw new MessageException(MessageErrorCode.MESSAGE_TYPE_INVALID);
            }

            // 根据消息类型进行不同的验证
            validateMessageContent(sendMessageDto, messageType);

            // 构建消息实体
            Message message = BeanCopyUtils.copyBean(sendMessageDto, Message.class);
            message.setSenderId(senderId);

            // 保存到数据库
            messageMapper.insert(message);

            // 查询完整的消息信息返回
            MessageVO result = messageMapper.selectMessageVOById(message.getMessageId());
            userProfileFallbackService.fillMissingProfiles(List.of(result), null);

            // 通过RabbitMQ发送WebSocket消息
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageId", result.getMessageId());
            payload.put("senderId", result.getSenderId());
            payload.put("senderNickname", result.getSenderNickname());
            payload.put("senderAvatar", result.getSenderAvatar());
            payload.put("messageType", result.getMessageType());
            payload.put("createTime", result.getCreateTime());
            
            // 根据消息类型添加相应字段，避免null值
            if (result.getContent() != null) {
                payload.put("content", result.getContent());
            }
            if (result.getFileUrl() != null) {
                payload.put("fileUrl", result.getFileUrl());
            }
            if (result.getFileName() != null) {
                payload.put("fileName", result.getFileName());
            }
            if (result.getFileSize() != null) {
                payload.put("fileSize", result.getFileSize());
            }
            
            webSocketMessageProducer.sendChatMessageToRoom(sendMessageDto.getRoomId(), payload);

            // 发布消息发送事件
            try {
                MessageSentEvent event = new MessageSentEvent(this, result, sendMessageDto.getRoomId());
                eventPublisher.publishEvent(event);
                log.info("消息事件发布成功, 消息ID: {}, 房间ID: {}", message.getMessageId(), sendMessageDto.getRoomId());
            } catch (Exception e) {
                log.warn("消息事件发布失败, 消息ID: {}, 房间ID: {}, 错误: {}", 
                         message.getMessageId(), sendMessageDto.getRoomId(), e.getMessage());
                // 事件发布失败不影响接口返回成功
            }
            
            log.info("发送消息成功, 消息ID: {}, 发送者ID: {}", message.getMessageId(), senderId);
            return result;
            
        } catch (Exception e) {
            log.error("发送消息失败, 发送者ID: {}, 房间ID: {}, 错误信息: {}", 
                     senderId, sendMessageDto.getRoomId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @LogRecord(operation = "分页查询房间消息", module = "消息管理")
    public PageResult<MessageVO> getRoomMessages(MessageQueryDto queryDto) {
        log.info("查询房间消息列表, 房间ID: {}, 页码: {}, 页大小: {}",
                 queryDto.getRoomId(), queryDto.getPageNum(), queryDto.getPageSize());

        Page<MessageVO> page = new Page<>(queryDto.getPageNum(), queryDto.getPageSize());

        IPage<MessageVO> result = messageMapper.selectMessageVOPage(
            page,
            queryDto.getRoomId(),
            queryDto.getMessageType(),
            queryDto.getSenderId(),
            queryDto.getKeyword()
        );

        if (!result.getRecords().isEmpty()) {
            List<Long> replyToIds = collectReplyToIdsNeedingProfile(result.getRecords());
            userProfileFallbackService.fillMissingProfiles(result.getRecords(), replyToIds);
        }

        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 从消息列表中提取 replyToId 非空且 replyToSenderNickname 为空的消息 ID
     */
    private List<Long> collectReplyToIdsNeedingProfile(List<MessageVO> messages) {
        return messages.stream()
                .filter(m -> m.getReplyToId() != null && !StringUtils.hasText(m.getReplyToSenderNickname()))
                .map(MessageVO::getReplyToId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @LogRecord(operation = "获取房间最新消息", module = "消息管理")
    public List<MessageVO> getLatestMessages(Long roomId, Integer limit) {
        log.info("获取房间最新消息, 房间ID: {}, 限制数量: {}", roomId, limit);

        List<MessageVO> messages = messageMapper.selectLatestMessages(roomId, limit);
        if (!messages.isEmpty()) {
            List<Long> replyToIds = collectReplyToIdsNeedingProfile(messages);
            userProfileFallbackService.fillMissingProfiles(messages, replyToIds);
        }
        return messages;
    }

    @Override
    @LogRecord(operation = "查询消息详情", module = "消息管理")
    public MessageVO getMessageById(Long messageId) {
        log.info("获取消息详情, 消息ID: {}", messageId);

        MessageVO messageVO = messageMapper.selectMessageVOById(messageId);
        if (messageVO == null) {
            throw new MessageException(MessageErrorCode.MESSAGE_NOT_FOUND);
        }
        userProfileFallbackService.fillMissingProfiles(List.of(messageVO), null);
        return messageVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "删除消息", module = "消息管理", includeResult = true)
    public Boolean deleteMessage(Long messageId, Long userId) {
        log.info("删除消息, 消息ID: {}, 操作用户ID: {}", messageId, userId);

        // 检查权限
        if (!checkMessagePermission(messageId, userId)) {
            throw new MessageException(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE);
        }

        // 执行删除
        int result = messageMapper.deleteById(messageId);
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "撤回消息", module = "消息管理", includeResult = true)
    public Boolean recallMessage(Long messageId, Long userId) {
        log.info("撤回消息, 消息ID: {}, 操作用户ID: {}", messageId, userId);

        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new MessageException(MessageErrorCode.MESSAGE_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(message.getIsRecalled())) {
            throw new MessageException(MessageErrorCode.MESSAGE_ALREADY_RECALLED);
        }

        if (!message.getSenderId().equals(userId)) {
            throw new MessageException(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE);
        }

        long elapsedSeconds = java.time.Duration.between(
                message.getCreateTime(), LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > messageProperties.getRecallTimeLimitSeconds()) {
            throw new MessageException(MessageErrorCode.MESSAGE_RECALL_TIME_EXPIRED);
        }

        Message update = new Message();
        update.setMessageId(messageId);
        update.setIsRecalled(true);
        update.setRecalledAt(LocalDateTime.now());
        messageMapper.updateById(update);

        deleteOssFileIfNeeded(message);

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("messageId", messageId);
        payload.put("roomId", message.getRoomId());
        payload.put("recalledAt", update.getRecalledAt().toString());
        webSocketMessageProducer.sendEventToRoom(
                message.getRoomId(), "message_recall", payload);

        log.info("撤回消息成功, 消息ID: {}, 房间ID: {}", messageId, message.getRoomId());
        return true;
    }

    /**
     * 若消息为文件类型（图片/文件/语音）且有 fileUrl，则通知 file-service 删除 OSS 对象。
     * 从 fileUrl 中提取 MinIO objectKey：
     *   格式：{endpoint}/{bucket}/{objectKey}，例如 http://127.0.0.1:9000/gopair-files/room/123/original/abc.jpg
     *   → objectKey = room/123/original/abc.jpg
     */
    private void deleteOssFileIfNeeded(Message message) {
        if (message.getFileUrl() == null || message.getFileUrl().trim().isEmpty()) {
            return;
        }
        try {
            String fileUrl = message.getFileUrl().trim();
            String objectKey = extractObjectKeyFromUrl(fileUrl);
            if (objectKey == null) {
                log.warn("无法从 fileUrl 提取 objectKey，跳过 OSS 删除: {}", fileUrl);
                return;
            }
            String fileServiceUrl = restTemplateProperties.getFileServiceUrl();
            restTemplate.delete(fileServiceUrl + "file/by-key?objectKey=" + objectKey);
            log.info("已通知 file-service 删除 OSS 对象: {}", objectKey);
        } catch (Exception e) {
            log.warn("通知 file-service 删除 OSS 文件失败（不影响撤回结果）: {}", e.getMessage());
        }
    }

    private String extractObjectKeyFromUrl(String fileUrl) {
        String[] markers = {"gopair-files/", "gopair-files-test/"};
        for (String marker : markers) {
            int idx = fileUrl.indexOf(marker);
            if (idx >= 0) {
                return fileUrl.substring(idx + marker.length());
            }
        }
        if (fileUrl.contains("/room/")) {
            int idx = fileUrl.indexOf("/room/");
            return fileUrl.substring(idx + 1);
        }
        return null;
    }

    @Override
    @LogRecord(operation = "统计房间消息数量", module = "消息管理")
    public Long countRoomMessages(Long roomId, Integer messageType) {
        log.info("统计房间消息数量, 房间ID: {}, 消息类型: {}", roomId, messageType);

        return messageMapper.countRoomMessages(roomId, messageType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupRoomMessages(Long roomId) {
        int deleted = messageMapper.deleteByRoomId(roomId);
        log.info("[消息服务] 清理房间{}的消息 {} 条", roomId, deleted);
        return deleted;
    }

    @Override
    @LogRecord(operation = "检查消息操作权限", module = "消息管理", includeResult = true)
    public Boolean checkMessagePermission(Long messageId, Long userId) {
        log.info("检查消息权限, 消息ID: {}, 用户ID: {}", messageId, userId);

        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            return false;
        }

        // 消息发送者可以删除自己的消息
        if (message.getSenderId().equals(userId)) {
            return true;
        }

        // 发送者仅可删除自己的消息（管理员权限由业务决定是否开放，本服务采用 sender-only 模型）
        return message.getSenderId().equals(userId);
    }

    @Override
    @LogRecord(operation = "检查用户是否在房间", module = "消息管理", includeResult = true)
    public Boolean checkUserInRoom(Long roomId, Long userId) {
        log.info("检查用户是否在房间内, 房间ID: {}, 用户ID: {}", roomId, userId);

        try {
            String url = ROOM_SERVICE_URL + roomId + "/members/" + userId + "/check";
            log.info("[DEBUG] 调用成员校验, URL={}, 当前线程上下文UserId={}",
                    url, UserContextHolder.getCurrentUserId());

            R<Boolean> response = restTemplate.getForObject(url, R.class);
            Boolean isMember = (response != null) ? response.getData() : false;
            log.info("[DEBUG] 成员校验响应: code={}, data={}, raw={}",
                    response != null ? response.getCode() : "null",
                    isMember,
                    response);
            log.info("房间成员校验结果, 房间ID: {}, 用户ID: {}, 成员状态: {}", roomId, userId, isMember);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("房间成员校验接口调用失败, 房间ID: {}, 用户ID: {}, 错误: {}",
                     roomId, userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据消息类型验证消息内容
     */
    private void validateMessageContent(SendMessageDto dto, MessageType messageType) {
        switch (messageType) {
            case TEXT:
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
                    throw new MessageException(MessageErrorCode.MESSAGE_CONTENT_EMPTY);
                }
                if (dto.getContent().length() > messageProperties.getMaxContentLength()) {
                    throw new MessageException(MessageErrorCode.MESSAGE_CONTENT_TOO_LONG);
                }
                break;
            case IMAGE:
            case FILE:
            case VOICE:
                if (dto.getFileUrl() == null || dto.getFileUrl().trim().isEmpty()) {
                    throw new MessageException(MessageErrorCode.FILE_URL_EMPTY);
                }
                if (dto.getFileName() == null || dto.getFileName().trim().isEmpty()) {
                    throw new MessageException(MessageErrorCode.FILE_NAME_EMPTY);
                }
                break;
            case EMOJI:
                // Emoji 消息只需 content 不为空，长度不超过配置上限（兼容 ❤️ 等多码点 Emoji）
                if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
                    throw new MessageException(MessageErrorCode.MESSAGE_CONTENT_EMPTY);
                }
                if (dto.getContent().length() > messageProperties.getEmojiMaxLength()) {
                    throw new MessageException(MessageErrorCode.MESSAGE_TYPE_INVALID);
                }
                break;
            default:
                throw new MessageException(MessageErrorCode.MESSAGE_TYPE_INVALID);
        }
    }
}
