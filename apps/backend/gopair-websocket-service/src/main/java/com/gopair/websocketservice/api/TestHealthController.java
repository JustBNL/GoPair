package com.gopair.websocketservice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用控制器。
 *
 * <p>提供测试所需的辅助端点，用于验证服务器状态和 WebSocket 连接能力。</p>
 */
@RestController
public class TestHealthController {

    @GetMapping("/test/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/test/ws-connect")
    public String wsConnect(@RequestParam(required = false) String userId,
                          @RequestParam(required = false) String nickname) {
        if (userId != null && nickname != null) {
            return "WS_AUTH_OK:" + userId + ":" + nickname;
        }
        return "WS_AUTH_MISSING";
    }
}
