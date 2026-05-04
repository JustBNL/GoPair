package com.gopair.fileservice.controller;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.fileservice.domain.vo.AvatarVO;
import com.gopair.fileservice.domain.vo.FileVO;
import com.gopair.fileservice.service.FileService;
import com.gopair.framework.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "文件管理", description = "房间文件上传、下载、预览及管理接口")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传用户头像", description = "上传头像图片到MinIO，返回压缩图和原图的永久直链URL，压缩图自动缩略为200x200")
    @PostMapping("/avatar")
    public R<AvatarVO> uploadAvatar(
            @Parameter(description = "头像图片文件（jpg/jpeg/png/gif/webp，≤5MB）", required = true)
            @RequestPart("file") MultipartFile file) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("[文件服务] 上传头像 userId:{}", userId);
        AvatarVO vo = fileService.uploadAvatar(file, userId);
        return R.ok(vo);
    }

    @Operation(summary = "上传私有文件", description = "上传文件到MinIO私有路径，返回永久直链URL，不记录DB元数据。适用于私聊文件/图片场景")
    @PostMapping("/private-upload")
    public R<FileVO> uploadPrivateFile(
            @Parameter(description = "文件（支持图片和文档，≤100MB）", required = true)
            @RequestPart("file") MultipartFile file) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("[文件服务] 上传私有文件 userId:{}", userId);
        FileVO vo = fileService.uploadPrivateFile(file, userId);
        return R.ok(vo);
    }

    @Operation(summary = "下载头像原图", description = "生成头像原图的下载Presigned URL，文件名固定为avatar_original.jpg")
    @GetMapping("/avatar/download")
    public R<String> downloadAvatar() {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("[文件服务] 下载头像原图 userId:{}", userId);
        String url = fileService.generateAvatarDownloadUrl(userId);
        return R.ok(url);
    }

    @Operation(summary = "下载指定用户的头像原图", description = "生成指定用户头像原图的下载Presigned URL")
    @GetMapping("/avatar/{userId}/download")
    public R<String> downloadUserAvatar(@PathVariable Long userId) {
        log.info("[文件服务] 下载指定用户头像原图 userId:{}", userId);
        String url = fileService.generateAvatarDownloadUrl(userId);
        return R.ok(url);
    }

    @Operation(summary = "上传文件", description = "上传文件到房间，图片类型自动生成缩略图")
    @PostMapping("/upload")
    public R<FileVO> uploadFile(
            @Parameter(description = "文件", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "房间ID", required = true)
            @RequestParam("roomId") Long roomId) {
        Long userId = UserContextHolder.getCurrentUserId();
        String nickname = UserContextHolder.getCurrentNickname();
        log.info("[文件服务] 上传文件 roomId:{} userId:{}", roomId, userId);
        FileVO fileVO = fileService.uploadFile(file, roomId, userId, nickname);
        return R.ok(fileVO);
    }

    @Operation(summary = "获取房间文件列表")
    @GetMapping("/room/{roomId}")
    public R<PageResult<FileVO>> getRoomFiles(
            @Parameter(description = "房间ID") @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "搜索关键字（模糊匹配文件名）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "文件类型分类（image/document/video/audio/archive/other）")
            @RequestParam(required = false) String fileType,
            @Parameter(description = "排序字段（uploadTime/fileSize/fileName）")
            @RequestParam(defaultValue = "uploadTime") String sortField,
            @Parameter(description = "排序方向（asc/desc）")
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return R.ok(fileService.getRoomFiles(
            roomId, pageNum, pageSize, keyword, fileType, sortField, sortOrder));
    }

    @Operation(summary = "获取文件信息")
    @GetMapping("/{fileId}")
    public R<FileVO> getFileInfo(
            @Parameter(description = "文件ID") @PathVariable Long fileId) {
        return R.ok(fileService.getFileInfo(fileId));
    }

    @Operation(summary = "下载文件", description = "生成下载Presigned URL并返回")
    @GetMapping("/{fileId}/download")
    public R<String> downloadFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId) {
        String url = fileService.generateDownloadUrl(fileId);
        log.info("[文件服务] 生成下载链接 fileId:{}", fileId);
        return R.ok(url);
    }

    @Operation(summary = "预览文件", description = "图片返回缩略图URL，其他类型返回原文件URL，302重定向")
    @GetMapping("/{fileId}/preview")
    public void previewFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId,
            HttpServletResponse response) throws Exception {
        String url = fileService.generatePreviewUrl(fileId);
        response.sendRedirect(url);
    }

    @Operation(summary = "删除文件", description = "仅上传者本人可删除")
    @DeleteMapping("/{fileId}")
    public R<Boolean> deleteFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("[文件服务] 删除文件 fileId:{} userId:{}", fileId, userId);
        fileService.deleteFile(fileId, userId);
        return R.ok(true);
    }

    @Operation(summary = "获取房间文件统计")
    @GetMapping("/room/{roomId}/stats")
    public R<FileService.RoomFileStats> getRoomFileStats(
            @Parameter(description = "房间ID") @PathVariable Long roomId) {
        return R.ok(fileService.getRoomFileStats(roomId));
    }

    @Operation(summary = "清理房间文件", description = "删除房间内所有文件，供房间服务在关闭房间时调用")
    @PostMapping("/room/{roomId}/cleanup")
    public R<Integer> cleanupRoomFiles(
            @Parameter(description = "房间ID") @PathVariable Long roomId) {
        log.info("[文件服务] 清理房间文件 roomId:{}", roomId);
        int count = fileService.cleanupRoomFiles(roomId);
        log.info("[文件服务] 清理完成 roomId:{} count:{}", roomId, count);
        return R.ok(count);
    }

    @Operation(summary = "根据ObjectKey删除MinIO对象", description = "供其他服务（如消息服务）撤回文件类消息时调用，不操作数据库")
    @DeleteMapping("/by-key")
    public R<Boolean> deleteByObjectKey(
            @Parameter(description = "MinIO对象Key", required = true)
            @RequestParam("objectKey") String objectKey) {
        log.info("[文件服务] 按ObjectKey删除 objectKey:{}", objectKey);
        fileService.deleteByObjectKey(objectKey);
        return R.ok(true);
    }
}
