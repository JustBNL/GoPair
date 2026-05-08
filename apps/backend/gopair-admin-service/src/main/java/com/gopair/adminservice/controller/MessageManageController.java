package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.query.MessagePageQuery;
import com.gopair.adminservice.domain.vo.MessageVO;
import com.gopair.adminservice.service.MessageManageService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息管理控制器
 */
@Tag(name = "消息管理")
@RestController
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class MessageManageController {

    private final MessageManageService messageManageService;

    @Operation(summary = "分页查询消息")
    @GetMapping("/page")
    public R<IPage<MessageVO>> getMessagePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer messageType,
            @RequestParam(required = false) Boolean isRecalled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        MessagePageQuery query = new MessagePageQuery(
                pageNum, pageSize, roomId, senderId, ownerId,
                messageType, isRecalled, keyword, startTime, endTime);
        return R.ok(messageManageService.getMessagePageVO(query));
    }

    @Operation(summary = "按房间查询消息")
    @GetMapping("/room/{roomId}")
    public R<IPage<MessageVO>> getMessageByRoom(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        MessagePageQuery query = new MessagePageQuery(
                pageNum, pageSize, roomId, null, null,
                null, null, null, null, null);
        return R.ok(messageManageService.getMessagePageVO(query));
    }
}
