package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.annotation.AdminAudit;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.service.RoomManageService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 房间管理控制器
 */
@Tag(name = "房间管理")
@RestController
@RequestMapping("/admin/rooms")
@RequiredArgsConstructor
public class RoomManageController {

    private final RoomManageService roomManageService;

    @Operation(summary = "分页查询房间")
    @GetMapping("/page")
    public R<Page<Room>> getRoomPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        RoomManageService.RoomPageQuery query = new RoomManageService.RoomPageQuery(pageNum, pageSize, status, keyword);
        return R.ok(roomManageService.getRoomPage(query));
    }

    @Operation(summary = "房间详情")
    @GetMapping("/{roomId}")
    public R<Map<String, Object>> getRoomDetail(@PathVariable Long roomId) {
        try {
            return R.ok(roomManageService.getRoomDetail(roomId));
        } catch (IllegalArgumentException e) {
            return R.fail(404, e.getMessage());
        }
    }

    @Operation(summary = "强制关闭房间")
    @PostMapping("/{roomId}/close")
    @AdminAudit(operation = "ROOM_CLOSE", targetType = "ROOM")
    public R<Void> closeRoom(@PathVariable Long roomId) {
        try {
            roomManageService.closeRoom(roomId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "禁用房间")
    @PostMapping("/{roomId}/disable")
    @AdminAudit(operation = "ROOM_DISABLE", targetType = "ROOM")
    public R<Void> disableRoom(@PathVariable Long roomId, @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.get("reason") : null;
            roomManageService.disableRoom(roomId, reason);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "解禁房间")
    @PostMapping("/{roomId}/enable")
    @AdminAudit(operation = "ROOM_ENABLE", targetType = "ROOM")
    public R<Void> enableRoom(@PathVariable Long roomId) {
        try {
            roomManageService.enableRoom(roomId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }
}
