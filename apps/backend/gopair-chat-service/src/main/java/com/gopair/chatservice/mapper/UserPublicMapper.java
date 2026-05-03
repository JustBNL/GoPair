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

    /**
     * 按关键词搜索用户（昵称或邮箱），排除指定用户 ID。
     *
     * @param keyword 搜索关键词（非空）
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页大小
     * @param excludeUserId 排除的用户 ID（当前登录用户）
     * @return 匹配的用户列表
     */
    List<UserPublicProfileDto> searchUsers(
            @Param("keyword") String keyword,
            @Param("pageNum") int pageNum,
            @Param("pageSize") int pageSize,
            @Param("offset") int offset,
            @Param("excludeUserId") Long excludeUserId);

    /**
     * 按关键词统计用户数量（用于分页总数）。
     */
    long countUsers(@Param("keyword") String keyword, @Param("excludeUserId") Long excludeUserId);
}
