package com.gopair.fileservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.fileservice.domain.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 *
 * @author gopair
 */
public interface FileService {

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
     * 分页获取房间文件列表
     *
     * @param roomId   房间ID
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @return 文件分页结果
     */
    PageResult<FileVO> getRoomFiles(Long roomId, int pageNum, int pageSize);

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
     * 房间文件统计信息内部类
     */
    record RoomFileStats(long fileCount, long totalSize, String totalSizeFormatted) {}
}
