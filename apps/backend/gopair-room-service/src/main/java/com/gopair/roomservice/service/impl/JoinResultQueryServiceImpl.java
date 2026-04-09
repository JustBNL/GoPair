package com.gopair.roomservice.service.impl;

import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.service.JoinResultQueryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JoinResultQueryServiceImpl implements JoinResultQueryService {

    private final StringRedisTemplate stringRedisTemplate;

    public JoinResultQueryServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    根据token查询房间加入状态
     */
    @Override
    public JoinStatusVO queryByToken(String joinToken) {
        String tokenKey = RoomConst.joinTokenKey(joinToken);
        String value = stringRedisTemplate.opsForValue().get(tokenKey);
        if (value == null) {
            return new JoinStatusVO(JoinStatusVO.Status.PROCESSING, null, null, "处理中");
        }
        // 格式: roomId:userId:STATUS  (e.g. "123:456:JOINED")
        // 兼容旧格式裸字符串 "PROCESSING"
        String[] parts = value.split(":", 3);
        if (parts.length == 3) {
            Long roomId = parseLong(parts[0]);
            Long userId = parseLong(parts[1]);
            String statusStr = parts[2];
            if (RoomConst.JOIN_RESULT_JOINED.equals(statusStr)) {
                return new JoinStatusVO(JoinStatusVO.Status.JOINED, roomId, userId, "加入成功");
            } else if (RoomConst.JOIN_RESULT_FAILED.equals(statusStr)) {
                return new JoinStatusVO(JoinStatusVO.Status.FAILED, roomId, userId, "加入失败");
            }
        }
        // 裸 PROCESSING 或其他未知值
        return new JoinStatusVO(JoinStatusVO.Status.PROCESSING, null, null, "处理中");
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
