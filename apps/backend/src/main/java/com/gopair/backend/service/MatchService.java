package com.gopair.backend.service;

import org.springframework.stereotype.Service;
import com.gopair.backend.dto.MatchRequest;
import com.gopair.backend.dto.MatchResponse;

/**
 * 处理匹配逻辑的服务
 */
@Service
public class MatchService {
    
    /**
     * 根据提供的密钥匹配用户
     * 
     * @param request 包含密钥的匹配请求
     * @return 指示匹配是否成功的响应
     */
    public MatchResponse pairWithKey(MatchRequest request) {
        // 对于这个初始实现，我们只是检查密钥是否为"123456"
        if ("123456".equals(request.getKey())) {
            return MatchResponse.builder()
                    .success(true)
                    .message("匹配成功")
                    .matchedUserId("user-" + System.currentTimeMillis()) // 生成一个临时用户ID
                    .build();
        } else {
            return MatchResponse.builder()
                    .success(false)
                    .message("提供的密钥无效")
                    .build();
        }
    }
} 