package com.gopair.chatservice.mapper;

import com.gopair.chatservice.domain.dto.UserPublicProfileDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户公开资料Mapper接口（只读访问 user 表）。
 *
 * @author gopair
 */
@Mapper
public interface UserPublicMapper {

    /**
     * 按用户 ID 列表查询昵称、头像。
     *
     * @param userIds 非空列表（调用方保证）
     * @return 在库中存在行的用户；顺序不保证
     */
    List<UserPublicProfileDto> selectByUserIds(@Param("userIds") List<Long> userIds);
}
