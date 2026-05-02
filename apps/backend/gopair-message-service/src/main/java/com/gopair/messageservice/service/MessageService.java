package com.gopair.messageservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.messageservice.domain.dto.MessageQueryDto;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;

import java.util.List;

/**
 * 消息服务接口
 * 
 * @author gopair
 */
public interface MessageService {

    /**
     * 发送消息
     * 
     * @param sendMessageDto 发送消息DTO
     * @param senderId 发送者ID
     * @return 消息VO
     */
    MessageVO sendMessage(SendMessageDto sendMessageDto, Long senderId);

    /**
     * 分页查询房间消息列表
     * 
     * @param queryDto 查询条件DTO
     * @return 分页结果
     */
    PageResult<MessageVO> getRoomMessages(MessageQueryDto queryDto);

    /**
     * 获取房间最新消息列表
     * 
     * @param roomId 房间ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<MessageVO> getLatestMessages(Long roomId, Integer limit);

    /**
     * 根据消息ID获取消息详情
     * 
     * @param messageId 消息ID
     * @return 消息VO
     */
    MessageVO getMessageById(Long messageId);

    /**
     * 删除消息
     * 
     * @param messageId 消息ID
     * @param userId 操作用户ID
     * @return 是否删除成功
     */
    Boolean deleteMessage(Long messageId, Long userId);

    /**
     * 统计房间消息数量
     * 
     * @param roomId 房间ID
     * @param messageType 消息类型（可选）
     * @return 消息数量
     */
    Long countRoomMessages(Long roomId, Integer messageType);

    /**
     * 检查用户是否有权限操作该消息
     * 
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    Boolean checkMessagePermission(Long messageId, Long userId);

    /**
     * 检查用户是否在房间内
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否在房间内
     */
    Boolean checkUserInRoom(Long roomId, Long userId);

    /**
     * 撤回消息
     * 仅发送者本人可在限定时间内撤回，撤回后消息保留但标记为已撤回，
     * 同时通过 WebSocket 广播撤回通知，文件类消息同时删除 OSS 对象。
     *
     * @param messageId 消息ID
     * @param userId 操作用户ID（当前登录用户）
     * @return 是否撤回成功
     */
    Boolean recallMessage(Long messageId, Long userId);
}