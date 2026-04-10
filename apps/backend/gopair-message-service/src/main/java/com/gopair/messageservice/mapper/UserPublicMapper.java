package com.gopair.messageservice.mapper;

import com.gopair.messageservice.domain.dto.UserPublicProfileDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 只读访问 user 表公开字段（昵称、头像），与 MessageMapper JOIN 用法互为冗余降级。
 */
@Mapper
public interface UserPublicMapper {

    /**
     * 按用户 ID 列表查询昵称、头像
     *
     * @param userIds 非空列表（调用方保证）
     * @return 在库中存在行的用户；顺序不保证
     */
    List<UserPublicProfileDto> selectByUserIds(@Param("userIds") List<Long> userIds);
}
