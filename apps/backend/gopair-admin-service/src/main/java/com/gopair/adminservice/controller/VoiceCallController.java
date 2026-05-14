package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
import com.gopair.adminservice.domain.query.VoiceCallPageQuery;
import com.gopair.adminservice.domain.vo.VoiceCallVO;
import com.gopair.adminservice.enums.AdminErrorCode;
import com.gopair.adminservice.exception.AdminException;
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
    public R<IPage<VoiceCallVO>> getVoiceCallPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long initiatorId,
            @RequestParam(required = false) Integer callType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        VoiceCallPageQuery query = new VoiceCallPageQuery(
                pageNum, pageSize, roomId, initiatorId, callType, status, keyword, startTime, endTime);
        return R.ok(voiceCallService.getVoiceCallPage(query));
    }

    @Operation(summary = "通话详情")
    @GetMapping("/{callId}")
    public R<VoiceCall> getVoiceCallById(@PathVariable Long callId) {
        VoiceCall call = voiceCallService.getVoiceCallById(callId);
        if (call == null) {
            throw new AdminException(AdminErrorCode.VOICE_CALL_NOT_FOUND);
        }
        return R.ok(call);
    }

    @Operation(summary = "查询通话参与者")
    @GetMapping("/{callId}/participants")
    public R<List<VoiceCallParticipant>> getParticipants(@PathVariable Long callId) {
        return R.ok(voiceCallService.getParticipants(callId));
    }
}
