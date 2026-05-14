package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.annotation.AdminAudit;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.domain.query.FilePageQuery;
import com.gopair.adminservice.domain.vo.FileVO;
import com.gopair.adminservice.service.FileManageService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文件管理控制器
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class FileManageController {

    private final FileManageService fileManageService;

    @Operation(summary = "分页查询文件")
    @GetMapping("/page")
    public R<IPage<FileVO>> getFilePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long uploaderId,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        FilePageQuery query = new FilePageQuery(
                pageNum, pageSize, roomId, uploaderId, fileType, keyword, startTime, endTime);
        return R.ok(fileManageService.getFilePage(query));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{fileId}")
    public R<RoomFile> getFileById(@PathVariable Long fileId) {
        RoomFile file = fileManageService.getFileById(fileId);
        if (file == null) {
            throw new com.gopair.adminservice.exception.AdminException(
                    com.gopair.adminservice.enums.AdminErrorCode.FILE_NOT_FOUND);
        }
        return R.ok(file);
    }

    @Operation(summary = "删除文件元数据")
    @PostMapping("/{fileId}/delete")
    @AdminAudit(operation = "FILE_DELETE", targetType = "FILE")
    public R<Void> deleteFile(@PathVariable Long fileId) {
        fileManageService.deleteFile(fileId);
        return R.ok();
    }
}
