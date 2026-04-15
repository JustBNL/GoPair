package com.gopair.adminservice.controller;

import com.gopair.adminservice.service.DashboardService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取全局统计数据")
    @GetMapping("/stats")
    public R<DashboardService.DashboardStats> getStats() {
        return R.ok(dashboardService.getStats());
    }
}
