package com.gopair.chatservice.service;

import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
import com.gopair.chatservice.domain.vo.ConversationVO;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.common.core.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 私聊消息服务接口。
 *
 * @author gopair
 */
public interface PrivateMessageService {

    /**
     * 发送私聊消息。
     *
     * @param dto 消息内容
     * @param senderId 发送者ID
     * @return 消息VO
     */
    PrivateMessageVO sendMessage(SendPrivateMessageDto dto, Long senderId);

    /**
     * 获取会话列表（按最后消息时间倒序）。
     *
     * @param userId 当前用户ID
     * @return 会话VO列表
     */
    List<ConversationVO> getConversations(Long userId);

    /**
     * 获取会话消息历史（Cursor 分页）。
     *
     * @param conversationId 会话ID
     * @param beforeMessageId 游标：消息ID，查此 ID 之前的消息，传 null 表示首次加载最新消息
     * @param pageSize 每页条数
     * @param currentUserId 当前用户ID
     * @return 分页消息列表
     */
    PageResult<PrivateMessageVO> getMessages(Long conversationId, Long beforeMessageId, int pageSize, Long currentUserId);

    /**
     * 删除私聊消息。
     *
     * @param messageId 消息ID
     * @param currentUserId 当前用户ID
     */
    void deleteMessage(Long messageId, Long currentUserId);

    /**
     * 撤回私聊消息（仅发送者可在2分钟内撤回）。
     *
     * @param messageId 消息ID
     * @param currentUserId 当前用户ID
     */
    void recallMessage(Long messageId, Long currentUserId);

    /**
     * 根据会话ID判断用户是否为会话参与者。
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否为参与者
     */
    boolean isParticipant(Long conversationId, Long userId);

    /**
     * 查询指定消息ID之后的私聊消息（用于 WebSocket 离线补发）
     *
     * @param conversationId 会话ID
     * @param lastMessageId 最后已知消息ID
     * @param limit 最大返回条数
     * @return 消息Map列表
     */
    List<Map<String, Object>> queryMessagesAfter(Long conversationId, Long lastMessageId, int limit);
}
