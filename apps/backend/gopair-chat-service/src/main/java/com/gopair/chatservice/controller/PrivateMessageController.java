package com.gopair.chatservice.controller;

import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
import com.gopair.chatservice.domain.vo.ConversationVO;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.chatservice.service.PrivateMessageService;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.framework.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 私聊消息接口。
 *
 * @author gopair
 */
@Tag(name = "私聊消息", description = "私聊消息相关接口")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final PrivateMessageService privateMessageService;

    @Operation(summary = "发送私聊消息")
    @PostMapping("/message/send")
    public R<PrivateMessageVO> sendMessage(
            @Parameter(description = "消息内容", required = true)
            @Valid @RequestBody SendPrivateMessageDto dto) {
        return R.ok(privateMessageService.sendMessage(dto, getCurrentUserId()));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/conversation")
    public R<List<ConversationVO>> getConversations() {
        return R.ok(privateMessageService.getConversations(getCurrentUserId()));
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/conversation/{conversationId}/message")
    public R<PageResult<PrivateMessageVO>> getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long conversationId,
            @Parameter(description = "游标：消息ID，查此 ID 之前的消息。传空表示首次加载最新消息")
            @RequestParam(required = false) Long beforeMessageId,
            @Parameter(description = "每页条数", example = "50")
            @RequestParam(defaultValue = "50") int pageSize) {
        return R.ok(privateMessageService.getMessages(conversationId, beforeMessageId, pageSize, getCurrentUserId()));
    }

    @Operation(summary = "删除私聊消息")
    @DeleteMapping("/message/{messageId}")
    public R<Void> deleteMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        privateMessageService.deleteMessage(messageId, getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "撤回私聊消息")
    @PostMapping("/message/{messageId}/recall")
    public R<Void> recallMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        privateMessageService.recallMessage(messageId, getCurrentUserId());
        return R.ok();
    }

    private Long getCurrentUserId() {
        return UserContextHolder.getCurrentUserId();
    }
}
