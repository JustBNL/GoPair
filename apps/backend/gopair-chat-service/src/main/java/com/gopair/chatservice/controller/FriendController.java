package com.gopair.chatservice.controller;

import com.gopair.chatservice.domain.dto.FriendRequestDto;
import com.gopair.chatservice.domain.dto.FriendStatusVO;
import com.gopair.chatservice.domain.vo.FriendRequestVO;
import com.gopair.chatservice.domain.vo.FriendVO;
import com.gopair.chatservice.domain.vo.UserSearchResultVO;
import com.gopair.chatservice.service.FriendService;
import com.gopair.common.core.PageResult;
import com.gopair.framework.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友关系管理接口。
 *
 * @author gopair
 */
@Tag(name = "好友管理", description = "好友关系与申请相关接口")
@RestController
@RequestMapping("/chat/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "发送好友请求")
    @PostMapping("/request")
    public FriendRequestVO sendRequest(
            @Parameter(description = "好友请求", required = true)
            @Valid @RequestBody FriendRequestDto dto) {
        return friendService.sendFriendRequest(dto, getCurrentUserId());
    }

    @Operation(summary = "同意好友请求")
    @PostMapping("/accept/{requestId}")
    public void accept(
            @Parameter(description = "申请记录ID", required = true)
            @PathVariable Long requestId) {
        friendService.acceptFriendRequest(requestId, getCurrentUserId());
    }

    @Operation(summary = "拒绝好友请求")
    @PostMapping("/reject/{requestId}")
    public void reject(
            @Parameter(description = "申请记录ID", required = true)
            @PathVariable Long requestId) {
        friendService.rejectFriendRequest(requestId, getCurrentUserId());
    }

    @Operation(summary = "删除好友")
    @DeleteMapping("/{friendId}")
    public void deleteFriend(
            @Parameter(description = "好友用户ID", required = true)
            @PathVariable Long friendId) {
        friendService.deleteFriend(friendId, getCurrentUserId());
    }

    @Operation(summary = "获取好友列表")
    @GetMapping
    public List<FriendVO> getFriends() {
        return friendService.getFriends(getCurrentUserId());
    }

    @Operation(summary = "获取收到的申请")
    @GetMapping("/request/incoming")
    public List<FriendRequestVO> getIncoming() {
        return friendService.getIncomingRequests(getCurrentUserId());
    }

    @Operation(summary = "获取发出的申请")
    @GetMapping("/request/outgoing")
    public List<FriendRequestVO> getOutgoing() {
        return friendService.getOutgoingRequests(getCurrentUserId());
    }

    @Operation(summary = "检查好友关系状态")
    @GetMapping("/check/{userId}")
    public FriendStatusVO checkFriend(
            @Parameter(description = "目标用户ID", required = true)
            @PathVariable Long userId) {
        return friendService.checkFriendStatus(userId, getCurrentUserId());
    }

    @Operation(summary = "获取用户公开资料")
    @GetMapping("/user/{userId}")
    public Object getUserProfile(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        return friendService.getUserPublicProfile(userId);
    }

    @Operation(summary = "搜索用户", description = "通过关键词搜索用户，同时匹配昵称和邮箱（OR 关系）")
    @GetMapping("/search")
    public PageResult<UserSearchResultVO> searchUsers(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize) {
        return friendService.searchUsers(keyword, pageNum, pageSize, getCurrentUserId());
    }

    private Long getCurrentUserId() {
        return UserContextHolder.getCurrentUserId();
    }
}
