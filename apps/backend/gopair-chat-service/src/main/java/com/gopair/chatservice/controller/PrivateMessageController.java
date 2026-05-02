package com.gopair.chatservice.controller;

import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
import com.gopair.chatservice.domain.vo.ConversationVO;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.chatservice.service.PrivateMessageService;
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
    public PrivateMessageVO sendMessage(
            @Parameter(description = "消息内容", required = true)
            @Valid @RequestBody SendPrivateMessageDto dto) {
        return privateMessageService.sendMessage(dto, getCurrentUserId());
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/conversation")
    public List<ConversationVO> getConversations() {
        return privateMessageService.getConversations(getCurrentUserId());
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/conversation/{conversationId}/message")
    public Object getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long conversationId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "20")
            @RequestParam(defaultValue = "20") int pageSize) {
        return privateMessageService.getMessages(conversationId, pageNum, pageSize, getCurrentUserId());
    }

    @Operation(summary = "删除私聊消息")
    @DeleteMapping("/message/{messageId}")
    public void deleteMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        privateMessageService.deleteMessage(messageId, getCurrentUserId());
    }

    @Operation(summary = "撤回私聊消息")
    @PostMapping("/message/{messageId}/recall")
    public void recallMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        privateMessageService.recallMessage(messageId, getCurrentUserId());
    }

    private Long getCurrentUserId() {
        return UserContextHolder.getCurrentUserId();
    }
}
