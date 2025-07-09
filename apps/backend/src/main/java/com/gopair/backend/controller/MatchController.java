package com.gopair.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.gopair.backend.dto.MatchRequest;
import com.gopair.backend.dto.MatchResponse;
import com.gopair.backend.service.MatchService;

/**
 * 处理匹配相关API端点的控制器
 */
@RestController
@RequestMapping("/api/match")
@CrossOrigin(origins = "*") // 允许来自任何源的请求
public class MatchController {

    private final MatchService matchService;

    @Autowired
    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 用密钥匹配用户的端点
     * 
     * @param request 包含密钥的匹配请求
     * @return 指示匹配是否成功的响应
     */
    @PostMapping("/pair")
    public MatchResponse pairWithKey(@RequestBody MatchRequest request) {
        return matchService.pairWithKey(request);
    }
} 