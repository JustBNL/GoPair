package com.gopair.chatservice.controller;

import com.gopair.chatservice.domain.dto.FriendRequestDto;
import com.gopair.chatservice.domain.dto.FriendStatusVO;
import com.gopair.chatservice.domain.vo.FriendRequestVO;
import com.gopair.chatservice.domain.vo.FriendVO;
import com.gopair.chatservice.domain.vo.UserSearchResultVO;
import com.gopair.chatservice.service.FriendService;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.framework.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
    public R<FriendRequestVO> sendRequest(
            @Parameter(description = "好友请求", required = true)
            @Valid @RequestBody FriendRequestDto dto) {
        return R.ok(friendService.sendFriendRequest(dto, getCurrentUserId()));
    }

    @Operation(summary = "同意好友请求")
    @PostMapping("/accept/{requestId}")
    public R<Void> accept(
            @Parameter(description = "申请记录ID", required = true)
            @PathVariable Long requestId) {
        friendService.acceptFriendRequest(requestId, getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "拒绝好友请求")
    @PostMapping("/reject/{requestId}")
    public R<Void> reject(
            @Parameter(description = "申请记录ID", required = true)
            @PathVariable Long requestId) {
        friendService.rejectFriendRequest(requestId, getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "删除好友")
    @DeleteMapping("/{friendId}")
    public R<Void> deleteFriend(
            @Parameter(description = "好友用户ID", required = true)
            @PathVariable Long friendId) {
        friendService.deleteFriend(friendId, getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "获取好友列表")
    @GetMapping
    public R<List<FriendVO>> getFriends(
            @Parameter(description = "搜索关键词（可选）")
            @RequestParam(required = false) String keyword) {
        if (StringUtils.hasText(keyword)) {
            return R.ok(friendService.searchFriends(getCurrentUserId(), keyword));
        }
        return R.ok(friendService.getFriends(getCurrentUserId()));
    }

    @Operation(summary = "获取收到的申请")
    @GetMapping("/request/incoming")
    public R<List<FriendRequestVO>> getIncoming() {
        return R.ok(friendService.getIncomingRequests(getCurrentUserId()));
    }

    @Operation(summary = "获取发出的申请")
    @GetMapping("/request/outgoing")
    public R<List<FriendRequestVO>> getOutgoing() {
        return R.ok(friendService.getOutgoingRequests(getCurrentUserId()));
    }

    @Operation(summary = "检查好友关系状态")
    @GetMapping("/check/{userId}")
    public R<FriendStatusVO> checkFriend(
            @Parameter(description = "目标用户ID", required = true)
            @PathVariable Long userId) {
        return R.ok(friendService.checkFriendStatus(userId, getCurrentUserId()));
    }

    @Operation(summary = "获取用户公开资料")
    @GetMapping("/user/{userId}")
    public R<Map<String, Object>> getUserProfile(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        return R.ok(friendService.getUserPublicProfile(userId));
    }

    @Operation(summary = "搜索用户", description = "通过关键词搜索用户，同时匹配昵称和邮箱（OR 关系）")
    @GetMapping("/search")
    public R<PageResult<UserSearchResultVO>> searchUsers(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(friendService.searchUsers(keyword, pageNum, pageSize, getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        return UserContextHolder.getCurrentUserId();
    }
}
