package com.gopair.chatservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.chatservice.domain.po.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友关系Mapper接口。
 *
 * @author gopair
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    /**
     * 查询用户的所有已同意好友（双向查询）。
     *
     * @param userId 用户ID
     * @return 好友用户ID列表
     */
    List<Long> selectFriendIds(@Param("userId") Long userId);

    /**
     * 判断两个用户是否为好友。
     */
    boolean isFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 删除指定双向好友关系。
     */
    int deleteByPair(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);
}
