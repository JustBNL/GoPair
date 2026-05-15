package com.gopair.voiceservice.base;

import com.gopair.common.core.R;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * REST API 房间状态 Mock 工具。
 *
 * * [核心策略]
 * - 配合 {@code @MockBean RestTemplate restTemplate} 使用，
 *   通过 Mockito Answer 动态返回房间状态。
 * - stubAllAsActive()：默认所有房间返回 ACTIVE(0)，用于大多数测试的 @BeforeEach。
 * - stub(roomId, status)：指定特定 roomId 返回特定状态，用于测试房间状态校验。
 *
 * * [房间状态码]
 * - 0 = ACTIVE（允许创建/加入通话）
 * - 1 = CLOSED
 * - 2 = EXPIRED
 * - 3 = ARCHIVED
 * - 4 = DISABLED
 */
public class RoomStatusStubber {
    private static final Map<Long, Integer> ROOM_STATUS_MAP = new HashMap<>();

    private RoomStatusStubber() {}

    /**
     * 全局默认：所有 room-status 请求返回 ACTIVE(0)。
     * 用于大部分测试的 @BeforeEach。
     */
    public static void stubAllAsActive(RestTemplate mock) {
        reset(mock);
        ROOM_STATUS_MAP.clear();
        when(mock.getForObject(anyString(), any()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url == null) return null;
                    for (Map.Entry<Long, Integer> entry : ROOM_STATUS_MAP.entrySet()) {
                        if (url.contains("/room/" + entry.getKey() + "/status")) {
                            return R.ok(entry.getValue());
                        }
                    }
                    return R.ok(0);
                });
    }

    /**
     * 指定 roomId 返回特定状态，其余返回 ACTIVE(0)。
     * 用于测试房间状态校验（CLOSED/EXPIRED/DISABLED/ARCHIVED）。
     */
    public static void stub(RestTemplate mock, long roomId, int status) {
        reset(mock);
        ROOM_STATUS_MAP.clear();
        ROOM_STATUS_MAP.put(roomId, status);
        when(mock.getForObject(anyString(), any()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url == null) return null;
                    for (Map.Entry<Long, Integer> entry : ROOM_STATUS_MAP.entrySet()) {
                        if (url.contains("/room/" + entry.getKey() + "/status")) {
                            return R.ok(entry.getValue());
                        }
                    }
                    return R.ok(0);
                });
    }
}
