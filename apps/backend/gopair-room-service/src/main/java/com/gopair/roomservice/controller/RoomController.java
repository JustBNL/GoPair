package com.gopair.roomservice.controller;

import com.gopair.framework.context.UserContextHolder;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RenewRoomDto;
import com.gopair.roomservice.domain.dto.ReopenRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.dto.UpdateRoomPasswordDto;
import com.gopair.roomservice.domain.dto.UpdatePasswordVisibilityDto;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomPasswordVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.roomservice.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 房间管理控制器
 *
 * @author gopair
 */
@Tag(name = "房间管理", description = "房间相关接口")
@RestController
@RequestMapping("/room")
public class RoomController {

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;

    public RoomController(RoomService roomService, RoomMemberService roomMemberService) {
        this.roomService = roomService;
        this.roomMemberService = roomMemberService;
    }

    /** 创建房间 */
    @Operation(summary = "创建房间", description = "创建新房间")
    @PostMapping
    public R<RoomVO> createRoom(
            @Parameter(description = "房间信息", required = true)
            @Validated @RequestBody RoomDto roomDto) {
        Long userId = UserContextHolder.getCurrentUserId();
        RoomVO roomVO = roomService.createRoom(roomDto, userId);
        return R.ok(roomVO);
    }

    /** 加入房间（异步） */
    @Operation(summary = "加入房间(异步)", description = "通过房间码加入房间，返回受理token")
    @PostMapping("/join/async")
    public R<JoinAcceptedVO> joinRoomAsync(
            @Parameter(description = "加入房间信息", required = true)
            @RequestBody JoinRoomDto joinRoomDto) {
        Long userId = UserContextHolder.getCurrentUserId();
        JoinAcceptedVO accepted = roomService.joinRoomAsync(joinRoomDto, userId);
        return R.ok(accepted);
    }

    /** 查询加入结果 */
    @Operation(summary = "查询加入结果", description = "根据token查询加入结果")
    @GetMapping("/join/result")
    public R<JoinStatusVO> joinResult(@RequestParam("token") String token) {
        JoinStatusVO status = roomService.queryJoinResult(token);
        return R.ok(status);
    }

    /** 离开房间 */
    @Operation(summary = "离开房间", description = "离开指定房间")
    @PostMapping("/{roomId}/leave")
    public R<Boolean> leaveRoom(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        boolean result = roomService.leaveRoom(roomId, userId);
        return R.ok(result);
    }

    /** 根据房间码查询房间信息 */
    @Operation(summary = "查询房间信息", description = "根据房间码查询房间信息")
    @GetMapping("/code/{roomCode}")
    public R<RoomVO> getRoomByCode(
            @Parameter(description = "房间码", required = true)
            @PathVariable String roomCode) {
        RoomVO roomVO = roomService.getRoomByCode(roomCode);
        return R.ok(roomVO);
    }

    /** 获取房间成员列表（仅房间成员可查询） */
    @Operation(summary = "获取房间成员", description = "获取指定房间的成员列表，仅房间成员可访问")
    @GetMapping("/{roomId}/members")
    public R<List<RoomMemberVO>> getRoomMembers(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            throw new com.gopair.roomservice.exception.RoomException(
                    com.gopair.roomservice.enums.RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        // 权限检查：仅房间成员可查询成员列表
        if (!roomService.isMemberInRoom(roomId, userId)) {
            throw new com.gopair.roomservice.exception.RoomException(
                    com.gopair.roomservice.enums.RoomErrorCode.NO_PERMISSION);
        }
        List<RoomMemberVO> members = roomService.getRoomMembers(roomId);
        return R.ok(members);
    }

    /** 关闭房间（仅房主） */
    @Operation(summary = "关闭房间", description = "房主关闭房间")
    @PostMapping("/{roomId}/close")
    public R<Boolean> closeRoom(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        boolean result = roomService.closeRoom(roomId, userId);
        return R.ok(result);
    }

    /** 续期房间（仅房主） */
    @Operation(summary = "续期房间", description = "房主将房间续期，ACTIVE 或 EXPIRED 状态均可续期，恢复为 ACTIVE")
    @PostMapping("/{roomId}/renew")
    public R<RoomVO> renewRoom(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "续期请求", required = true)
            @RequestBody @Validated RenewRoomDto dto) {
        Long userId = UserContextHolder.getCurrentUserId();
        RoomVO result = roomService.renewRoom(roomId, userId, dto.getExtendHours());
        return R.ok(result);
    }

    /** 重新开启房间（仅房主） */
    @Operation(summary = "重新开启房间", description = "房主将已关闭的房间重新开启，仅限手动关闭的房间")
    @PostMapping("/{roomId}/reopen")
    public R<RoomVO> reopenRoom(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "重新开启请求", required = true)
            @RequestBody @Validated ReopenRoomDto dto) {
        Long userId = UserContextHolder.getCurrentUserId();
        RoomVO result = roomService.reopenRoom(roomId, userId, dto.getExpireHours());
        return R.ok(result);
    }

    /** 获取用户的房间列表 */
    @Operation(summary = "获取用户房间", description = "获取用户创建或加入的房间列表")
    @GetMapping("/my")
    public R<PageResult<RoomVO>> getUserRooms(RoomQueryDto query) {
        Long userId = UserContextHolder.getCurrentUserId();
        PageResult<RoomVO> result = roomService.getUserRooms(userId, query);
        return R.ok(result);
    }

    /** 更新房间密码设置（仅房主） */
    @Operation(summary = "更新房间密码", description = "房主设置/关闭/切换房间密码模式")
    @PatchMapping("/{roomId}/password")
    public R<Void> updateRoomPassword(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @RequestBody UpdateRoomPasswordDto dto) {
        Long userId = UserContextHolder.getCurrentUserId();
        roomService.updateRoomPassword(roomId, userId, dto.getMode(), dto.getRawPassword(), dto.getVisible());
        return R.ok(null);
    }

    /** 更新房间密码可见性（仅房主） */
    @Operation(summary = "更新房间密码可见性", description = "房主切换密码是否对成员可见")
    @PatchMapping("/{roomId}/password/visibility")
    public R<Void> updatePasswordVisibility(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @RequestBody @Validated UpdatePasswordVisibilityDto dto) {
        Long userId = UserContextHolder.getCurrentUserId();
        roomService.updatePasswordVisibility(roomId, userId, dto.getVisible());
        return R.ok(null);
    }

    /** 获取当前房间密码/令牌（仅房主） */
    @Operation(summary = "获取当前密码", description = "房主查询当前有效密码或动态令牌")
    @GetMapping("/{roomId}/password/current")
    public R<RoomPasswordVO> getRoomCurrentPassword(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        RoomPasswordVO vo = roomService.getRoomCurrentPassword(roomId, userId);
        return R.ok(vo);
    }

    /** 踢出房间成员（仅房主） */
    @Operation(summary = "踢出成员", description = "房主将指定成员移除房间")
    @DeleteMapping("/{roomId}/members/{userId}")
    public R<Void> kickMember(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "被踢出的用户ID", required = true)
            @PathVariable Long userId) {
        Long operatorId = UserContextHolder.getCurrentUserId();
        roomService.kickMember(roomId, operatorId, userId);
        return R.ok(null);
    }

    /**
     * 检查指定用户是否为房间成员（内部服务调用，无需鉴权拦截）
     * 用于 message-service 等内部服务校验成员身份。
     *
     * userId 从路径变量读取，不依赖请求头，
     * 避免 RestTemplate 转发链路中 header 丢失导致下游 Context 为空。
     */
    @Operation(summary = "检查成员身份", description = "内部接口，校验用户是否为指定房间成员")
    @GetMapping("/{roomId}/members/{userId}/check")
    public R<Boolean> checkMember(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        boolean result = roomMemberService.isMemberInRoom(roomId, userId);
        return R.ok(result);
    }

    /**
     * 批量添加房间成员（压测场景专用，内部调用）。
     *
     * @param roomId 房间ID
     * @param userIds 要添加的用户ID列表
     * @return 成功添加的数量
     */
    @Operation(summary = "批量添加成员", description = "内部接口，批量将用户加入房间")
    @PostMapping("/{roomId}/members/batch")
    public R<Integer> addMembersBatch(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "用户ID列表", required = true)
            @RequestBody List<Long> userIds) {
        int count = roomMemberService.addMembers(roomId, userIds);
        return R.ok(count);
    }
}
