package com.gopair.chatservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.chatservice.domain.po.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 好友申请Mapper接口。
 *
 * @author gopair
 */
@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {

    /**
     * 检查是否存在待处理的申请记录（任意方向）。
     */
    boolean existsPending(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);
}
