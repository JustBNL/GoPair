package com.gopair.chatservice.service;

import com.gopair.chatservice.domain.dto.FriendRequestDto;
import com.gopair.chatservice.domain.dto.FriendStatusVO;
import com.gopair.chatservice.domain.vo.FriendRequestVO;
import com.gopair.chatservice.domain.vo.FriendVO;

import java.util.List;

/**
 * 好友关系服务接口。
 *
 * @author gopair
 */
public interface FriendService {

    /**
     * 发送好友请求。
     *
     * @param dto 请求参数
     * @param fromUserId 申请人用户ID
     * @return 申请记录VO
     */
    FriendRequestVO sendFriendRequest(FriendRequestDto dto, Long fromUserId);

    /**
     * 同意好友请求。
     *
     * @param requestId 申请记录ID
     * @param currentUserId 当前用户ID（被申请人）
     */
    void acceptFriendRequest(Long requestId, Long currentUserId);

    /**
     * 拒绝好友请求。
     *
     * @param requestId 申请记录ID
     * @param currentUserId 当前用户ID（被申请人）
     */
    void rejectFriendRequest(Long requestId, Long currentUserId);

    /**
     * 删除好友。
     *
     * @param friendId 好友用户ID
     * @param currentUserId 当前用户ID
     */
    void deleteFriend(Long friendId, Long currentUserId);

    /**
     * 获取当前用户的好友列表。
     *
     * @param userId 用户ID
     * @return 好友VO列表
     */
    List<FriendVO> getFriends(Long userId);

    /**
     * 获取收到的待处理好友申请列表。
     *
     * @param userId 当前用户ID
     * @return 申请VO列表
     */
    List<FriendRequestVO> getIncomingRequests(Long userId);

    /**
     * 获取发出的好友申请列表。
     *
     * @param userId 当前用户ID
     * @return 申请VO列表
     */
    List<FriendRequestVO> getOutgoingRequests(Long userId);

    /**
     * 检查与指定用户的好友关系状态。
     *
     * @param targetUserId 目标用户ID
     * @param currentUserId 当前用户ID
     * @return 状态VO
     */
    FriendStatusVO checkFriendStatus(Long targetUserId, Long currentUserId);

    /**
     * 获取任意用户的公开资料。
     *
     * @param userId 用户ID
     * @return 用户VO（昵称、头像、邮箱）
     */
    Object getUserPublicProfile(Long userId);
}
