package com.gopair.messageservice.controller;

import com.gopair.framework.context.UserContextHolder;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.messageservice.domain.dto.MessageQueryDto;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 消息控制器
 * 
 * @author gopair
 */
@Tag(name = "消息管理", description = "房间消息相关接口")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 发送消息
     */
    @Operation(summary = "发送消息", description = "向房间发送消息")
    @PostMapping("/send")
    public R<MessageVO> sendMessage(
            @Parameter(description = "发送消息请求", required = true)
            @Valid @RequestBody SendMessageDto sendMessageDto) {
        
        Long userId = UserContextHolder.getCurrentUserId();
        MessageVO messageVO = messageService.sendMessage(sendMessageDto, userId);
        return R.ok(messageVO);
    }

    /**
     * 分页查询房间消息列表
     */
    @Operation(summary = "查询房间消息", description = "分页查询指定房间的消息列表")
    @GetMapping("/room/{roomId}")
    public R<PageResult<MessageVO>> getRoomMessages(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @ModelAttribute MessageQueryDto queryDto) {
        queryDto.setRoomId(roomId);
        PageResult<MessageVO> result = messageService.getRoomMessages(queryDto);
        return R.ok(result);
    }

    /**
     * 获取房间最新消息
     */
    @Operation(summary = "获取最新消息", description = "获取指定房间的最新消息列表")
    @GetMapping("/room/{roomId}/latest")
    public R<List<MessageVO>> getLatestMessages(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "限制数量", example = "10")
            @RequestParam(defaultValue = "10") Integer limit) {
        
        List<MessageVO> messages = messageService.getLatestMessages(roomId, limit);
        return R.ok(messages);
    }

    /**
     * 获取消息详情
     */
    @Operation(summary = "获取消息详情", description = "根据消息ID获取消息详细信息")
    @GetMapping("/{messageId}")
    public R<MessageVO> getMessageById(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        
        MessageVO messageVO = messageService.getMessageById(messageId);
        return R.ok(messageVO);
    }

    /**
     * 删除消息
     */
    @Operation(summary = "删除消息", description = "删除指定的消息")
    @DeleteMapping("/{messageId}")
    public R<Boolean> deleteMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        
        Long userId = UserContextHolder.getCurrentUserId();
        Boolean result = messageService.deleteMessage(messageId, userId);
        return R.ok(result);
    }

    /**
     * 撤回消息
     */
    @Operation(summary = "撤回消息", description = "撤回发送的消息（仅发送者可在2分钟内撤回）")
    @PostMapping("/{messageId}/recall")
    public R<Boolean> recallMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        
        Long userId = UserContextHolder.getCurrentUserId();
        Boolean result = messageService.recallMessage(messageId, userId);
        return R.ok(result);
    }

    /**
     * 统计房间消息数量
     */
    @Operation(summary = "统计消息数量", description = "统计指定房间的消息数量")
    @GetMapping("/room/{roomId}/count")
    public R<Long> countRoomMessages(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId,
            @Parameter(description = "消息类型")
            @RequestParam(required = false) Integer messageType) {
        
        Long count = messageService.countRoomMessages(roomId, messageType);
        return R.ok(count);
    }

    /**
     * 健康检查接口
     */
    @Operation(summary = "健康检查", description = "检查消息服务状态")
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("消息服务运行正常");
    }

    /**
     * 清理房间的所有消息
     */
    @Operation(summary = "清理房间消息", description = "删除指定房间的所有消息")
    @PostMapping("/room/{roomId}/cleanup")
    public R<Integer> cleanupRoomMessages(
            @Parameter(description = "房间ID", required = true)
            @PathVariable Long roomId) {
        int count = messageService.cleanupRoomMessages(roomId);
        return R.ok(count);
    }
} 