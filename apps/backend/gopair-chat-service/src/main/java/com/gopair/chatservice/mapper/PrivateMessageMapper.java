package com.gopair.chatservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.chatservice.domain.po.PrivateMessage;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
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
}
