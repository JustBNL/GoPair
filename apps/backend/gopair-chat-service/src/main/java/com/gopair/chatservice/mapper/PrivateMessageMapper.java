package com.gopair.chatservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.chatservice.domain.po.PrivateMessage;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.chatservice.domain.vo.ConversationDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 私聊消息Mapper接口。
 *
 * @author gopair
 */
@Mapper
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {

    /**
     * 分页查询会话消息（包含发送者信息）。
     */
    IPage<PrivateMessageVO> selectMessageVOPage(
        @Param("page") Page<PrivateMessageVO> page,
        @Param("conversationId") Long conversationId
    );

    /**
     * Cursor 分页查询会话消息（基于 beforeMessageId 获取更早消息）。
     *
     * @param conversationId 会话ID
     * @param beforeMessageId 游标：消息ID，查此 ID 之前的消息，传 null 表示首次加载最新消息
     * @param pageSize 每页大小
     */
    List<PrivateMessageVO> selectMessageVOPageBefore(
        @Param("conversationId") Long conversationId,
        @Param("beforeMessageId") Long beforeMessageId,
        @Param("pageSize") Integer pageSize
    );

    /**
     * 查询会话最新一条消息。
     */
    PrivateMessageVO selectLatestMessageByConversation(@Param("conversationId") Long conversationId);

    /**
     * 查询用户参与的所有会话ID（按最后消息时间倒序）。
     */
    List<Long> selectConversationIdsByUser(@Param("userId") Long userId);

    /**
     * 统计会话消息数量。
     */
    Long countByConversation(@Param("conversationId") Long conversationId);

    /**
     * 批量查询会话详情（每会话最新消息 + 消息总数），合并 N+1 查询。
     */
    List<ConversationDetailVO> selectConversationDetailsBatch(@Param("userId") Long userId);
}
