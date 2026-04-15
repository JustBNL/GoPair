package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
import com.gopair.adminservice.service.VoiceCallService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通话记录控制器
 */
@Tag(name = "通话记录")
@RestController
@RequestMapping("/admin/voice-calls")
@RequiredArgsConstructor
public class VoiceCallController {

    private final VoiceCallService voiceCallService;

    @Operation(summary = "分页查询通话记录")
    @GetMapping("/page")
    public R<Page<VoiceCall>> getVoiceCallPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer status) {
        VoiceCallService.VoiceCallPageQuery query = new VoiceCallService.VoiceCallPageQuery(pageNum, pageSize, roomId, status);
        return R.ok(voiceCallService.getVoiceCallPage(query));
    }

    @Operation(summary = "通话详情")
    @GetMapping("/{callId}")
    public R<VoiceCall> getVoiceCallById(@PathVariable Long callId) {
        VoiceCall call = voiceCallService.getVoiceCallById(callId);
        if (call == null) {
            return R.fail(404, "通话记录不存在");
        }
        return R.ok(call);
    }

    @Operation(summary = "查询通话参与者")
    @GetMapping("/{callId}/participants")
    public R<List<VoiceCallParticipant>> getParticipants(@PathVariable Long callId) {
        return R.ok(voiceCallService.getParticipants(callId));
    }
}
