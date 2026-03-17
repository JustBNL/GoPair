package com.gopair.fileservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.common.core.PageResult;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.fileservice.config.MinioProperties;
import com.gopair.fileservice.domain.po.RoomFile;
import com.gopair.fileservice.domain.vo.FileVO;
import com.gopair.fileservice.enums.FileErrorCode;
import com.gopair.fileservice.exception.FileException;
import com.gopair.fileservice.mapper.RoomFileMapper;
import com.gopair.fileservice.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final RoomFileMapper roomFileMapper;
    private final WebSocketMessageProducer wsProducer;
    @Value("${gopair.file.max-file-size:104857600}")
    private long maxFileSize;
    @Value("${gopair.file.max-room-size:1073741824}")
    private long maxRoomSize;
    @Value("${gopair.file.thumbnail-size:200}")
    private int thumbnailSize;
    @Value("${gopair.file.allowed-types:pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,json,xml,csv,jpg,jpeg,png,gif,bmp,webp,svg,mp4,avi,mov,mp3,wav,flac,aac,zip,rar,7z,tar,gz}")
    private String allowedTypes;
    private static final List<String> IMAGE_TYPES = List.of("jpg","jpeg","png","gif","bmp","webp");

    // ==================== upload ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO uploadFile(MultipartFile file, Long roomId, Long userId, String nickname) {
        String fn = file.getOriginalFilename();
        String ft = extractExtension(fn);
        long fs = file.getSize();
        log.info("[file-service] start op:uploadFile roomId:{} userId:{} file:{} size:{}B", roomId, userId, fn, fs);
        validateUpload(ft, fs, roomId);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ok = buildObjectKey(roomId, "original", uuid, ft);
        String tk = null;
        try {
            uploadToMinio(file.getInputStream(), ok, file.getContentType(), fs);
            if (IMAGE_TYPES.contains(ft)) {
                tk = buildObjectKey(roomId, "thumbnail", uuid + "_thumb", ft);
                byte[] tb = generateThumbnail(file.getInputStream(), ft);
                uploadToMinio(new ByteArrayInputStream(tb), tk, file.getContentType(), tb.length);
            }
            RoomFile rf = buildRoomFile(roomId, userId, nickname, fn, ok, tk, fs, ft, file.getContentType());
            roomFileMapper.insert(rf);
            publishFileEvent(roomId, rf.getFileId(), fn, "file_upload");
            log.info("[file-service] success op:uploadFile fileId:{}", rf.getFileId());
            return toVO(rf);
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file-service] failed op:uploadFile err:{}", e.getMessage(), e);
            silentDeleteFromMinio(ok);
            if (tk != null) silentDeleteFromMinio(tk);
            throw new FileException(FileErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    // ==================== query ====================
    @Override
    public PageResult<FileVO> getRoomFiles(Long roomId, int pageNum, int pageSize) {
        Page<RoomFile> page = new Page<>(pageNum, pageSize);
        Page<RoomFile> result = (Page<RoomFile>) roomFileMapper.selectPageByRoomId(page, roomId);
        List<FileVO> voList = result.getRecords().stream().map(this::toVO).toList();
        PageResult<FileVO> pr = new PageResult<>();
        pr.setRecords(voList); pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent()); pr.setSize(result.getSize()); pr.setPages(result.getPages());
        return pr;
    }

    @Override
    public FileVO getFileInfo(Long fileId) { return toVO(getFileOrThrow(fileId)); }

    @Override
    public String generateDownloadUrl(Long fileId) {
        RoomFile rf = getFileOrThrow(fileId);
        rf.setDownloadCount(rf.getDownloadCount() + 1);
        roomFileMapper.updateById(rf);
        return buildPresignedDownloadUrl(rf.getFilePath(), rf.getFileName());
    }

    @Override
    public String generatePreviewUrl(Long fileId) {
        RoomFile rf = getFileOrThrow(fileId);
        String key = rf.getThumbnailPath() != null ? rf.getThumbnailPath() : rf.getFilePath();
        return buildPresignedUrl(key);
    }

    // ==================== delete ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId, Long userId) {
        RoomFile rf = getFileOrThrow(fileId);
        log.info("[file-service] start op:deleteFile fileId:{} userId:{}", fileId, userId);
        if (!rf.getUploaderId().equals(userId)) throw new FileException(FileErrorCode.FILE_ACCESS_DENIED);
        silentDeleteFromMinio(rf.getFilePath());
        if (rf.getThumbnailPath() != null) silentDeleteFromMinio(rf.getThumbnailPath());
        roomFileMapper.deleteById(fileId);
        publishFileEvent(rf.getRoomId(), fileId, rf.getFileName(), "file_delete");
        log.info("[file-service] success op:deleteFile fileId:{}", fileId);
    }

    // ==================== stats & cleanup ====================
    @Override
    public RoomFileStats getRoomFileStats(Long roomId) {
        long count = roomFileMapper.countByRoomId(roomId);
        long totalSize = roomFileMapper.sumFileSizeByRoomId(roomId);
        return new RoomFileStats(count, totalSize, FileVO.formatFileSize(totalSize));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupRoomFiles(Long roomId) {
        log.info("[file-service] start op:cleanupRoomFiles roomId:{}", roomId);
        List<RoomFile> files = roomFileMapper.selectAllByRoomId(roomId);
        if (files.isEmpty()) return 0;
        for (RoomFile file : files) {
            silentDeleteFromMinio(file.getFilePath());
            if (file.getThumbnailPath() != null) silentDeleteFromMinio(file.getThumbnailPath());
        }
        LambdaQueryWrapper<RoomFile> w = new LambdaQueryWrapper<>();
        w.eq(RoomFile::getRoomId, roomId);
        roomFileMapper.delete(w);
        log.info("[file-service] success op:cleanupRoomFiles roomId:{} count:{}", roomId, files.size());
        return files.size();
    }

    // ==================== private helpers ====================
    private void validateUpload(String fileType, long fileSize, Long roomId) {
        if (!Arrays.asList(allowedTypes.split(",")).contains(fileType))
            throw new FileException(FileErrorCode.FILE_TYPE_NOT_ALLOWED, "Unsupported type: " + fileType);
        if (fileSize > maxFileSize)
            throw new FileException(FileErrorCode.FILE_TOO_LARGE, "Max: " + FileVO.formatFileSize(maxFileSize));
        if (roomFileMapper.sumFileSizeByRoomId(roomId) + fileSize > maxRoomSize)
            throw new FileException(FileErrorCode.ROOM_QUOTA_EXCEEDED);
    }

    private String buildObjectKey(Long roomId, String cat, String name, String ext) {
        return String.format("room/%d/%s/%s.%s", roomId, cat, name, ext);
    }

    private void uploadToMinio(InputStream in, String key, String ct, long size) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioProperties.getBucketName()).object(key)
                .stream(in, size, -1)
                .contentType(ct != null ? ct : "application/octet-stream").build());
    }

    private byte[] generateThumbnail(InputStream in, String fileType) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String fmt = ("png".equals(fileType) || "gif".equals(fileType)) ? "png" : "jpg";
        Thumbnails.of(in).size(thumbnailSize, thumbnailSize).keepAspectRatio(true).outputFormat(fmt).toOutputStream(out);
        return out.toByteArray();
    }

    private String buildPresignedUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioProperties.getBucketName()).object(key)
                    .method(Method.GET)
                    .expiry((int) minioProperties.getPresignedUrlExpireSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception e) {
            log.error("[file-service] presigned url failed key:{} err:{}", key, e.getMessage());
            throw new FileException(FileErrorCode.MINIO_OPERATION_FAILED, "Failed to generate URL");
        }
    }

    private String buildPresignedDownloadUrl(String key, String fileName) {
        try {
            String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioProperties.getBucketName()).object(key)
                    .method(Method.GET)
                    .expiry((int) minioProperties.getPresignedUrlExpireSeconds(), TimeUnit.SECONDS)
                    .extraQueryParams(java.util.Map.of(
                            "response-content-disposition",
                            "attachment; filename=\"" + encoded + "\""
                    )).build());
        } catch (Exception e) {
            log.error("[file-service] presigned download url failed key:{} err:{}", key, e.getMessage());
            throw new FileException(FileErrorCode.MINIO_OPERATION_FAILED, "Failed to generate download URL");
        }
    }

    private void silentDeleteFromMinio(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName()).object(key).build());
        } catch (Exception e) {
            log.warn("[file-service] minio delete ignored key:{} err:{}", key, e.getMessage());
        }
    }

    private RoomFile getFileOrThrow(Long fileId) {
        RoomFile rf = roomFileMapper.selectById(fileId);
        if (rf == null) throw new FileException(FileErrorCode.FILE_NOT_FOUND);
        return rf;
    }

    private RoomFile buildRoomFile(Long roomId, Long userId, String nickname, String fileName,
                                    String filePath, String thumbnailPath, long fileSize,
                                    String fileType, String contentType) {
        RoomFile f = new RoomFile();
        f.setRoomId(roomId);
        f.setUploaderId(userId);
        f.setUploaderNickname(nickname);
        f.setFileName(fileName);
        f.setFilePath(filePath);
        f.setThumbnailPath(thumbnailPath);
        f.setFileSize(fileSize);
        f.setFileType(fileType);
        f.setContentType(contentType);
        f.setDownloadCount(0);
        
        LocalDateTime now = LocalDateTime.now();
        f.setUploadTime(now);
        f.setCreateTime(now);
        f.setUpdateTime(now);
        
        return f;
    }

    private void publishFileEvent(Long roomId, Long fileId, String fileName, String eventType) {
        try {
            wsProducer.sendEventToRoom(roomId, eventType,
                    Map.of("fileId", fileId, "fileName", fileName, "roomId", roomId));
        } catch (Exception e) {
            log.warn("[file-service] ws event ignored type:{} roomId:{} err:{}", eventType, roomId, e.getMessage());
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "unknown";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private FileVO toVO(RoomFile r) {
        FileVO vo = new FileVO();
        vo.setFileId(r.getFileId());
        vo.setRoomId(r.getRoomId());
        vo.setUploaderId(r.getUploaderId());
        vo.setUploaderNickname(r.getUploaderNickname());
        vo.setFileName(r.getFileName());
        vo.setFileSize(r.getFileSize());
        vo.setFileSizeFormatted(FileVO.formatFileSize(r.getFileSize()));
        vo.setFileType(r.getFileType());
        vo.setContentType(r.getContentType());
        vo.setDownloadCount(r.getDownloadCount());
        vo.setUploadTime(r.getUploadTime());
        vo.setIconType(FileVO.resolveIconType(r.getFileType()));
        vo.setPreviewable(FileVO.isPreviewable(r.getFileType()));
        vo.setDownloadUrl(buildPresignedUrl(r.getFilePath()));
        String pk = r.getThumbnailPath() != null ? r.getThumbnailPath() : r.getFilePath();
        vo.setPreviewUrl(buildPresignedUrl(pk));
        return vo;
    }
}
