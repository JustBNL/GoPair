package com.gopair.roomservice.controller;

import com.gopair.framework.context.UserContextHolder;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.dto.UpdateRoomPasswordDto;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;
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

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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

    /** 获取当前房间密码/令牌（仅房主） */
    @Operation(summary = "获取当前密码", description = "房主查询当前有效密码或动态令牌")
    @GetMapping("/{roomId}/password/current")
    public R<RoomVO> getRoomCurrentPassword(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        RoomVO vo = roomService.getRoomCurrentPassword(roomId, userId);
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
}
