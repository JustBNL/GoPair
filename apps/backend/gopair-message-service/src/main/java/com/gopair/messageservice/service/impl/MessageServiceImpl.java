package com.gopair.messageservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.common.util.BeanCopyUtils;
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
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WebSocketMessageProducer webSocketMessageProducer;

    public MessageServiceImpl(MessageMapper messageMapper, ApplicationEventPublisher eventPublisher, WebSocketMessageProducer webSocketMessageProducer) {
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
        this.webSocketMessageProducer = webSocketMessageProducer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            
            // 根据消息类型进行不同的验证
            validateMessageContent(sendMessageDto, messageType);

            // 构建消息实体
            Message message = BeanCopyUtils.copyBean(sendMessageDto, Message.class);
            message.setSenderId(senderId);

            // 保存到数据库
            messageMapper.insert(message);

            // 查询完整的消息信息返回
            MessageVO result = messageMapper.selectMessageVOById(message.getMessageId());

            // 通过RabbitMQ发送WebSocket消息
            Map<String, Object> payload = Map.of(
                    "messageId", result.getMessageId(),
                    "senderId", result.getSenderId(),
                    "senderNickname", result.getSenderNickname(),
                    "content", result.getContent(),
                    "messageType", result.getMessageType(),
                    "createTime", result.getCreateTime()
            );
            
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

        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<MessageVO> getLatestMessages(Long roomId, Integer limit) {
        log.info("获取房间最新消息, 房间ID: {}, 限制数量: {}", roomId, limit);

        return messageMapper.selectLatestMessages(roomId, limit);
    }

    @Override
    public MessageVO getMessageById(Long messageId) {
        log.info("获取消息详情, 消息ID: {}", messageId);

        MessageVO messageVO = messageMapper.selectMessageVOById(messageId);
        if (messageVO == null) {
            throw new MessageException(MessageErrorCode.MESSAGE_NOT_FOUND);
        }

        return messageVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    public Long countRoomMessages(Long roomId, Integer messageType) {
        log.info("统计房间消息数量, 房间ID: {}, 消息类型: {}", roomId, messageType);

        return messageMapper.countRoomMessages(roomId, messageType);
    }

    @Override
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

        // TODO: 房间管理员权限检查
        return false;
    }

    @Override
    public Boolean checkUserInRoom(Long roomId, Long userId) {
        log.info("检查用户是否在房间内, 房间ID: {}, 用户ID: {}", roomId, userId);
        
        // TODO: 实现房间成员检查逻辑，这里暂时返回true
        return true;
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
            default:
                throw new MessageException(MessageErrorCode.MESSAGE_TYPE_INVALID);
        }
    }
} 