package com.gopair.roomservice.mapper;

import com.gopair.roomservice.domain.dto.UserPublicProfileDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 只读访问 user 表公开字段（与 gopair-message-service 中 MessageMapper 对 user 的用法一致）
 */
public interface UserPublicMapper {

    /**
     * 按用户 ID 列表查询昵称、头像
     *
     * @param userIds 非空列表（调用方保证）
     * @return 在库中存在行的用户；顺序不保证
     */
    List<UserPublicProfileDto> selectByUserIds(@Param("userIds") List<Long> userIds);
}
