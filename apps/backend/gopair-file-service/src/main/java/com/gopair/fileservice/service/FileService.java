package com.gopair.fileservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.fileservice.domain.vo.AvatarVO;
import com.gopair.fileservice.domain.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 *
 * @author gopair
 */
public interface FileService {

    /**
     * 上传用户头像到MinIO avatar路径，返回压缩图和原图的永久直链URL
     * 压缩图会被缩略为 200x200 jpg 格式
     *
     * @param file   头像图片文件（仅限 jpg/jpeg/png/gif/webp，≤5MB）
     * @param userId 当前用户ID
     * @return 头像VO，含压缩图URL和原图URL
     */
    AvatarVO uploadAvatar(MultipartFile file, Long userId);

    /**
     * 上传文件到MinIO，保存元数据到DB，并推送WebSocket事件
     *
     * @param file     上传的文件
     * @param roomId   所属房间ID
     * @param userId   上传者用户ID
     * @param nickname 上传者昵称
     * @return 文件VO（含Presigned URL）
     */
    FileVO uploadFile(MultipartFile file, Long roomId, Long userId, String nickname);

    /**
     * 分页获取房间文件列表（支持搜索、类型筛选、排序）
     *
     * @param roomId    房间ID
     * @param pageNum   页码（从1开始）
     * @param pageSize  每页大小
     * @param keyword   搜索关键字（模糊匹配文件名，可为空）
     * @param fileType  文件类型分类（image/document/video/audio/archive/other，可为空）
     * @param sortField 排序字段（uploadTime/fileSize/fileName，默认为 uploadTime）
     * @param sortOrder 排序方向（asc/desc，默认为 desc）
     * @return 文件分页结果
     */
    PageResult<FileVO> getRoomFiles(Long roomId, int pageNum, int pageSize,
                                     String keyword, String fileType,
                                     String sortField, String sortOrder);

    /**
     * 获取单个文件信息（含动态生成的Presigned URL）
     *
     * @param fileId 文件ID
     * @return 文件VO
     */
    FileVO getFileInfo(Long fileId);

    /**
     * 生成文件下载Presigned URL（并增加下载计数）
     *
     * @param fileId 文件ID
     * @return 下载URL
     */
    String generateDownloadUrl(Long fileId);

    /**
     * 生成文件预览Presigned URL
     * 图片类型返回缩略图URL，其他类型返回原图URL
     *
     * @param fileId 文件ID
     * @return 预览URL
     */
    String generatePreviewUrl(Long fileId);

    /**
     * 生成头像原图的下载Presigned URL（带content-disposition）
     *
     * @param userId 用户ID
     * @return 下载URL
     */
    String generateAvatarDownloadUrl(Long userId);

    /**
     * 删除文件（MinIO + DB），并推送WebSocket事件
     * 仅上传者本人可删除
     *
     * @param fileId 文件ID
     * @param userId 操作用户ID
     */
    void deleteFile(Long fileId, Long userId);

    /**
     * 获取房间文件统计信息
     *
     * @param roomId 房间ID
     * @return 统计信息（文件数量、总大小）
     */
    RoomFileStats getRoomFileStats(Long roomId);

    /**
     * 清理房间所有文件（MinIO + DB），房间关闭时调用
     *
     * @param roomId 房间ID
     * @return 清理的文件数量
     */
    int cleanupRoomFiles(Long roomId);

    /**
     * 上传私有文件到MinIO，直接返回永久直链URL。
     * 不涉及房间上下文，不记录DB元数据，不触发房间事件。
     * 适用于私聊场景的文件/图片上传。
     *
     * @param file   上传的文件
     * @param userId 上传者用户ID
     * @return 私有文件VO（含永久URL）
     */
    FileVO uploadPrivateFile(MultipartFile file, Long userId);

    /**
     * 根据 MinIO objectKey 删除对象（不操作 DB）。
     * 供其他服务（如 message-service）在消息撤回场景下删除 OSS 文件。
     *
     * @param objectKey MinIO 对象 key，如 "room/123/original/abc123.jpg"
     */
    void deleteByObjectKey(String objectKey);

    /**
     * 房间文件统计信息内部类
     */
    record RoomFileStats(long fileCount, long totalSize, String totalSizeFormatted) {}
}
