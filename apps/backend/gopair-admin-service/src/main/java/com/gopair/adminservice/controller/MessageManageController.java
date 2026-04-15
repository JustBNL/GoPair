package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.Message;
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
    public R<Page<Message>> getMessagePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String keyword) {
        MessageManageService.MessagePageQuery query = new MessageManageService.MessagePageQuery(pageNum, pageSize, roomId, keyword);
        return R.ok(messageManageService.getMessagePage(query));
    }

    @Operation(summary = "按房间查询消息")
    @GetMapping("/room/{roomId}")
    public R<Page<Message>> getMessageByRoom(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        return R.ok(messageManageService.getMessageByRoom(roomId, pageNum, pageSize));
    }
}
