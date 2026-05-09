package com.gopair.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.messageservice.domain.po.Message;
import com.gopair.messageservice.domain.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 消息Mapper接口
 * 
 * @author gopair
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 分页查询房间消息列表（包含发送者信息和回复信息）
     * 
     * @param page 分页对象
     * @param roomId 房间ID
     * @param messageType 消息类型（可选）
     * @param senderId 发送者ID（可选）
     * @param keyword 搜索关键词（可选）
     * @return 消息VO列表
     */
    IPage<MessageVO> selectMessageVOPage(
        @Param("page") Page<MessageVO> page,
        @Param("roomId") Long roomId,
        @Param("messageType") Integer messageType,
        @Param("senderId") Long senderId,
        @Param("keyword") String keyword
    );

    /**
     * 根据消息ID查询消息详情（包含发送者信息和回复信息）
     * 
     * @param messageId 消息ID
     * @return 消息VO
     */
    MessageVO selectMessageVOById(@Param("messageId") Long messageId);

    /**
     * 查询房间最新的N条消息
     * 
     * @param roomId 房间ID
     * @param limit 限制数量
     * @return 消息VO列表
     */
    List<MessageVO> selectLatestMessages(
        @Param("roomId") Long roomId, 
        @Param("limit") Integer limit
    );

    /**
     * 统计房间消息数量
     * 
     * @param roomId 房间ID
     * @param messageType 消息类型（可选）
     * @return 消息数量
     */
    Long countRoomMessages(
        @Param("roomId") Long roomId,
        @Param("messageType") Integer messageType
    );

    /**
     * 根据房间ID删除所有消息
     *
     * @param roomId 房间ID
     * @return 删除的消息数量
     */
    int deleteByRoomId(@Param("roomId") Long roomId);

    /**
     * 游标分页查询房间历史消息（查 beforeMessageId 之前的消息，按 create_time DESC 返回）
     *
     * @param roomId 房间ID
     * @param beforeMessageId 游标消息ID（查询此 ID 之前的消息）
     * @param pageSize 每页大小
     * @return 消息VO列表（需在Service层翻转回正序）
     */
    List<MessageVO> selectHistoryMessagesBefore(
        @Param("roomId") Long roomId,
        @Param("beforeMessageId") Long beforeMessageId,
        @Param("pageSize") int pageSize
    );

    /**
     * 查询指定消息ID之后的房间消息（用于 WebSocket 离线补发）
     *
     * @param roomId 房间ID
     * @param lastMessageId 最后已知消息ID
     * @param limit 最大返回条数
     * @return 消息Map列表
     */
    List<Map<String, Object>> selectMessagesAfter(
        @Param("roomId") Long roomId,
        @Param("lastMessageId") Long lastMessageId,
        @Param("limit") int limit
    );
} 