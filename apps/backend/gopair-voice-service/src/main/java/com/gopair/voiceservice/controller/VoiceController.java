package com.gopair.voiceservice.controller;

import com.gopair.common.core.R;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.service.VoiceCallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 语音通话控制器
 *
 * @author gopair
 */
@Tag(name = "语音通话", description = "语音通话相关接口")
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceCallService voiceCallService;

    /**
     * 加入或创建通话（按需创建模式）
     * 若房间无活跃通话则自动创建，否则直接加入。
     * 不广播 participant-join，需前端 WebRTC 就绪后调用 /{callId}/ready。
     */
    @Operation(summary = "加入或创建通话（按需创建）")
    @PostMapping("/room/{roomId}/join")
    public R<CallVO> joinOrCreateCall(
            @Parameter(description = "房间ID") @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        return R.ok(voiceCallService.joinOrCreateCall(roomId, userId));
    }

    /**
     * WebRTC 就绪通知
     * 前端本地音频流和 PeerConnection 建立完成后调用，触发 participant-join 广播。
     */
    @Operation(summary = "通知WebRTC就绪")
    @PostMapping("/{callId}/ready")
    public R<Boolean> notifyReady(
            @Parameter(description = "通话ID") @PathVariable Long callId) {
        Long userId = UserContextHolder.getCurrentUserId();
        voiceCallService.notifyReady(callId, userId);
        return R.ok(true);
    }

    /**
     * 加入通话（通过 callId，内部调用保留）
     */
    @Operation(summary = "加入通话")
    @PostMapping("/{callId}/join")
    public R<CallVO> joinCall(
            @Parameter(description = "通话ID") @PathVariable Long callId) {
        Long userId = UserContextHolder.getCurrentUserId();
        return R.ok(voiceCallService.joinCall(callId, userId));
    }

    /**
     * 离开通话
     */
    @Operation(summary = "离开通话")
    @PostMapping("/{callId}/leave")
    public R<Boolean> leaveCall(
            @Parameter(description = "通话ID") @PathVariable Long callId) {
        Long userId = UserContextHolder.getCurrentUserId();
        voiceCallService.leaveCall(callId, userId);
        return R.ok(true);
    }

    /**
     * 结束通话
     */
    @Operation(summary = "结束通话")
    @PostMapping("/{callId}/end")
    public R<Boolean> endCall(
            @Parameter(description = "通话ID") @PathVariable Long callId) {
        Long userId = UserContextHolder.getCurrentUserId();
        voiceCallService.endCall(callId, userId);
        return R.ok(true);
    }

    /**
     * 查询通话信息
     */
    @Operation(summary = "查询通话信息")
    @GetMapping("/{callId}")
    public R<CallVO> getCall(
            @Parameter(description = "通话ID") @PathVariable Long callId) {
        return R.ok(voiceCallService.getCall(callId));
    }

    /**
     * 获取房间当前活跃通话（仅查询，不自动创建）
     */
    @Operation(summary = "获取房间活跃通话")
    @GetMapping("/room/{roomId}/active")
    public R<CallVO> getActiveCall(
            @Parameter(description = "房间ID") @PathVariable Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        return R.ok(voiceCallService.getActiveCall(roomId, userId));
    }

    /**
     * 转发 WebRTC 信令
     */
    @Operation(summary = "转发WebRTC信令")
    @PostMapping("/signaling")
    public R<Boolean> forwardSignaling(@RequestBody SignalingDto dto) {
        Long userId = UserContextHolder.getCurrentUserId();
        voiceCallService.forwardSignaling(dto, userId);
        return R.ok(true);
    }
}
