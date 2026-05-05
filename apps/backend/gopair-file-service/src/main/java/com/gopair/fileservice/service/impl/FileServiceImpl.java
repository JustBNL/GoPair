package com.gopair.fileservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.common.core.PageResult;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.fileservice.config.MinioProperties;
import com.gopair.fileservice.domain.po.RoomFile;
import com.gopair.fileservice.domain.vo.AvatarVO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final StringRedisTemplate redisTemplate;
    private static final String ROOM_QUOTA_KEY_PREFIX = "file:quota:";
    private static final String AVATAR_PATH_PREFIX = "avatar/";
    private static final int QUOTA_DEVIATION_TOLERANCE_BYTES = 1;
    @Value("${gopair.file.max-file-size:104857600}")
    private long maxFileSize;
    @Value("${gopair.file.max-room-size:1073741824}")
    private long maxRoomSize;
    @Value("${gopair.file.thumbnail-size:200}")
    private int thumbnailSize;
    @Value("${gopair.file.allowed-types:pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,json,xml,csv,jpg,jpeg,png,gif,bmp,webp,svg,mp4,avi,mov,mp3,wav,flac,aac,zip,rar,7z,tar,gz}")
    private String allowedTypes;
    private static final List<String> IMAGE_TYPES = List.of("jpg","jpeg","png","gif","bmp","webp");
    private static final List<String> AVATAR_IMAGE_TYPES = List.of("jpg","jpeg","png","gif","webp");
    private Set<String> allowedTypesSet;
    @Value("${gopair.file.avatar-max-size:5242880}")
    private long avatarMaxSize;

    @jakarta.annotation.PostConstruct
    public void init() {
        allowedTypesSet = Set.of(allowedTypes.split(","));
    }

    /** @FileServiceImpl.java (66-67) avatar */
    @Override
    @LogRecord(operation = "上传头像", module = "文件管理")
    public AvatarVO uploadAvatar(MultipartFile file, Long userId) {
        String fn = file.getOriginalFilename();
        String ft = extractExtension(fn);
        long fs = file.getSize();
        log.info("[file-service] start op:uploadAvatar userId:{} file:{} size:{}B", userId, fn, fs);
        if (!AVATAR_IMAGE_TYPES.contains(ft)) {
            throw new FileException(FileErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持图片格式: jpg/jpeg/png/gif/webp");
        }
        if (fs > avatarMaxSize) {
            throw new FileException(FileErrorCode.FILE_TOO_LARGE, "头像文件不能超过 " + FileVO.formatFileSize(avatarMaxSize));
        }
        String compressedKey = AVATAR_PATH_PREFIX + userId + "/profile.jpg";
        String originalKey = AVATAR_PATH_PREFIX + userId + "/original.jpg";
        try {
            byte[] rawBytes = file.getBytes();
            // 原图：按原始格式上传
            String ct = "image/jpeg".equals(file.getContentType()) ? "image/jpeg"
                    : ("image/png".equals(file.getContentType()) ? "image/png"
                    : ("image/gif".equals(file.getContentType()) ? "image/gif" : "image/webp"));
            uploadToMinio(new ByteArrayInputStream(rawBytes), originalKey, ct, fs);
            String originalUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + "/" + originalKey;
            // 压缩图：覆盖式上传
            byte[] compressed = generateThumbnail(new ByteArrayInputStream(rawBytes), ft);
            uploadToMinio(new ByteArrayInputStream(compressed), compressedKey, "image/jpeg", compressed.length);
            String compressedUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + "/" + compressedKey;
            log.info("[file-service] success op:uploadAvatar userId:{} compressed:{} original:{}", userId, compressedUrl, originalUrl);
            return new AvatarVO(compressedUrl, originalUrl);
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file-service] failed op:uploadAvatar userId:{} err:{}", userId, e.getMessage(), e);
            throw new FileException(FileErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    /** @FileServiceImpl.java (99-100) upload */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "上传文件", module = "文件管理", includeResult = true)
    public FileVO uploadFile(MultipartFile file, Long roomId, Long userId, String nickname) {
        String fn = file.getOriginalFilename();
        String ft = extractExtension(fn);
        long fs = file.getSize();
        log.info("[file-service] start op:uploadFile roomId:{} userId:{} file:{} size:{}B", roomId, userId, fn, fs);
        checkFileTypeAndSize(ft, fs);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ok = buildObjectKey(roomId, "original", uuid, ft);
        String tk = null;
        long thumbnailBytes = 0;
        try {
            byte[] rawBytes = file.getBytes();
            if (IMAGE_TYPES.contains(ft)) {
                tk = buildObjectKey(roomId, "thumbnail", uuid + "_thumb", ft);
                byte[] tb = generateThumbnail(new ByteArrayInputStream(rawBytes), ft);
                thumbnailBytes = tb.length;
                // 原子预占配额（含原图 + 缩略图）
                reserveRoomQuota(roomId, fs + thumbnailBytes);
                uploadToMinio(new ByteArrayInputStream(rawBytes), ok, file.getContentType(), fs);
                uploadToMinio(new ByteArrayInputStream(tb), tk, file.getContentType(), thumbnailBytes);
            } else {
                // 非图片不生成缩略图，仅预占原图配额
                reserveRoomQuota(roomId, fs);
                uploadToMinio(new ByteArrayInputStream(rawBytes), ok, file.getContentType(), fs);
            }
            RoomFile rf = buildRoomFile(roomId, userId, nickname, fn, ok, tk, fs, thumbnailBytes, ft, file.getContentType());
            roomFileMapper.insert(rf);
            if (!syncAndCheckRoomQuota(roomId)) {
                roomFileMapper.deleteById(rf.getFileId());
                throw new FileException(FileErrorCode.ROOM_QUOTA_EXCEEDED);
            }
            publishFileEvent(roomId, rf.getFileId(), fn, "file_upload");
            log.info("[file-service] success op:uploadFile fileId:{}", rf.getFileId());
            return toVO(rf);
        } catch (FileException e) {
            if (thumbnailBytes > 0) releaseRoomQuota(roomId, fs + thumbnailBytes);
            else releaseRoomQuota(roomId, fs);
            throw e;
        } catch (Exception e) {
            log.error("[file-service] failed op:uploadFile err:{}", e.getMessage(), e);
            silentDeleteFromMinio(ok);
            silentDeleteFromMinio(tk);
            if (thumbnailBytes > 0) releaseRoomQuota(roomId, fs + thumbnailBytes);
            else releaseRoomQuota(roomId, fs);
            throw new FileException(FileErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    /** @FileServiceImpl.java (146-147) query */
    @Override
    public PageResult<FileVO> getRoomFiles(Long roomId, int pageNum, int pageSize,
                                           String keyword, String fileType,
                                           String sortField, String sortOrder) {
        Page<RoomFile> page = new Page<>(pageNum, pageSize);
        IPage<RoomFile> result = roomFileMapper.selectPage(
            page, roomId, keyword, fileType, sortField, sortOrder);
        List<FileVO> voList = result.getRecords().stream().map(this::toVO).toList();
        PageResult<FileVO> pr = new PageResult<>();
        pr.setRecords(voList); pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent()); pr.setSize(result.getSize()); pr.setPages(result.getPages());
        return pr;
    }

    @Override
    public FileVO getFileInfo(Long fileId) { return toVO(getFileOrThrow(fileId)); }

    @Override
    @LogRecord(operation = "生成下载链接", module = "文件管理", includeResult = true)
    public String generateDownloadUrl(Long fileId) {
        log.info("[file-service] start op:generateDownloadUrl fileId:{}", fileId);
        RoomFile rf = getFileOrThrow(fileId);
        roomFileMapper.incrementDownloadCount(fileId);
        rf.setDownloadCount(rf.getDownloadCount() + 1);
        String url = buildPresignedDownloadUrl(rf.getFilePath(), rf.getFileName());
        log.info("[file-service] success op:generateDownloadUrl fileId:{} url:{}", fileId, url);
        return url;
    }

    @Override
    @LogRecord(operation = "生成预览链接", module = "文件管理")
    public String generatePreviewUrl(Long fileId) {
        log.info("[file-service] start op:generatePreviewUrl fileId:{}", fileId);
        RoomFile rf = getFileOrThrow(fileId);
        String key = buildThumbnailObjectKey(rf.getFilePath());
        String url = buildPresignedUrl(key);
        log.info("[file-service] success op:generatePreviewUrl fileId:{}", fileId);
        return url;
    }

    @Override
    @LogRecord(operation = "生成头像下载链接", module = "文件管理", includeResult = true)
    public String generateAvatarDownloadUrl(Long userId) {
        log.info("[file-service] start op:generateAvatarDownloadUrl userId:{}", userId);
        String originalKey = AVATAR_PATH_PREFIX + userId + "/original.jpg";
        try {
            minioClient.statObject(minioProperties.getBucketName(), originalKey);
        } catch (Exception e) {
            log.warn("[file-service] avatar not found userId:{} key:{}", userId, originalKey);
            throw new FileException(FileErrorCode.FILE_NOT_FOUND, "该用户尚未上传头像");
        }
        String url = buildPresignedDownloadUrl(originalKey, "avatar_original.jpg");
        log.info("[file-service] success op:generateAvatarDownloadUrl userId:{}", userId);
        return url;
    }

    // ==================== delete ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "删除文件", module = "文件管理", includeResult = true)
    public void deleteFile(Long fileId, Long userId) {
        RoomFile rf = getFileOrThrow(fileId);
        log.info("[file-service] start op:deleteFile fileId:{} userId:{}", fileId, userId);
        if (!rf.getUploaderId().equals(userId)) throw new FileException(FileErrorCode.FILE_ACCESS_DENIED);
        silentDeleteFromMinio(rf.getFilePath());
        silentDeleteThumbnail(rf.getFilePath());
        roomFileMapper.deleteById(fileId);
        releaseRoomQuota(rf.getRoomId(), rf.getFileSize() + (rf.getThumbnailSize() != null ? rf.getThumbnailSize() : 0));
        publishFileEvent(rf.getRoomId(), fileId, rf.getFileName(), "file_delete");
        log.info("[file-service] success op:deleteFile fileId:{}", fileId);
    }

    // ==================== stats & cleanup ====================
    @Override
    public RoomFileStats getRoomFileStats(Long roomId) {
        long count = roomFileMapper.countByRoomId(roomId);
        // 优先从 Redis 获取配额，性能更优；Redis 无值时回退到 DB
        Long totalSize = null;
        try {
            String redisVal = redisTemplate.opsForValue().get(ROOM_QUOTA_KEY_PREFIX + roomId);
            if (redisVal != null) {
                totalSize = Long.parseLong(redisVal);
            }
        } catch (Exception ignored) {}
        if (totalSize == null) {
            totalSize = roomFileMapper.sumFileSizeByRoomId(roomId);
            // 异步回填 Redis（不阻塞主流程）
            try {
                redisTemplate.opsForValue().setIfAbsent(ROOM_QUOTA_KEY_PREFIX + roomId, String.valueOf(totalSize));
            } catch (Exception ignored) {}
        }
        return new RoomFileStats(count, totalSize, FileVO.formatFileSize(totalSize));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "清理房间文件", module = "文件管理", includeResult = true)
    public int cleanupRoomFiles(Long roomId) {
        log.info("[file-service] start op:cleanupRoomFiles roomId:{}", roomId);
        int totalCleaned = 0;
        int failedBatches = 0;
        int batchSize = 200;
        List<RoomFile> batch;
        do {
            batch = roomFileMapper.selectBatchByRoomId(roomId, batchSize);
            if (batch.isEmpty()) break;
            try {
                for (RoomFile file : batch) {
                    silentDeleteFromMinio(file.getFilePath());
                    silentDeleteThumbnail(file.getFilePath());
                }
                List<Long> ids = batch.stream().map(RoomFile::getFileId).toList();
                roomFileMapper.deleteByIds(ids);
                totalCleaned += batch.size();
                log.info("[file-service] cleanupRoomFiles batch done, roomId:{} total:{}", roomId, totalCleaned);
            } catch (Exception e) {
                failedBatches++;
                log.error("[file-service] cleanupRoomFiles batch failed roomId:{} batchSize:{} err:{}",
                        roomId, batch.size(), e.getMessage(), e);
            }
        } while (batch.size() == batchSize);
        try {
            redisTemplate.delete(ROOM_QUOTA_KEY_PREFIX + roomId);
        } catch (Exception e) {
            log.warn("[file-service] cleanupRoomFiles: failed to delete redis quota key roomId:{} err:{}", roomId, e.getMessage());
        }
        log.info("[file-service] success op:cleanupRoomFiles roomId:{} count:{} failedBatches:{}", roomId, totalCleaned, failedBatches);
        if (failedBatches > 0) {
            throw new IllegalStateException("cleanupRoomFiles failed to delete DB records in " + failedBatches + " batch(es). MinIO objects may be orphaned. Check error logs.");
        }
        return totalCleaned;
    }

    private static final String PRIVATE_PATH_PREFIX = "private/";

    @Override
    public void deleteByObjectKey(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            log.warn("[file-service] deleteByObjectKey: objectKey is null or empty, skip");
            return;
        }
        log.info("[file-service] start op:deleteByObjectKey key:{}", objectKey);
        silentDeleteFromMinio(objectKey);
        log.info("[file-service] success op:deleteByObjectKey key:{}", objectKey);
    }

    /** @FileServiceImpl.java (287) uploadPrivateFile */
    @Override
    @LogRecord(operation = "上传私有文件", module = "文件管理", includeResult = true)
    public FileVO uploadPrivateFile(MultipartFile file, Long userId) {
        String fn = file.getOriginalFilename();
        String ft = extractExtension(fn);
        long fs = file.getSize();
        log.info("[file-service] start op:uploadPrivateFile userId:{} file:{} size:{}B", userId, fn, fs);
        checkFileTypeAndSize(ft, fs);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String key = PRIVATE_PATH_PREFIX + userId + "/" + uuid + "." + ft;
        try {
            byte[] rawBytes = file.getBytes();
            uploadToMinio(new ByteArrayInputStream(rawBytes), key, file.getContentType(), fs);
            String url = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + "/" + key;
            log.info("[file-service] success op:uploadPrivateFile userId:{} url:{}", userId, url);
            FileVO vo = new FileVO();
            vo.setFileName(fn);
            vo.setFileSize(fs);
            vo.setFileSizeFormatted(FileVO.formatFileSize(fs));
            vo.setFileType(ft);
            vo.setContentType(file.getContentType());
            vo.setDownloadUrl(url);
            vo.setPreviewUrl(url);
            vo.setPreviewable(IMAGE_TYPES.contains(ft));
            vo.setIconType(FileVO.resolveIconType(ft));
            return vo;
        } catch (Exception e) {
            log.error("[file-service] failed op:uploadPrivateFile err:{}", e.getMessage(), e);
            silentDeleteFromMinio(key);
            throw new FileException(FileErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    // ==================== private helpers ====================
    private void checkFileTypeAndSize(String fileType, long fileSize) {
        if (!allowedTypesSet.contains(fileType))
            throw new FileException(FileErrorCode.FILE_TYPE_NOT_ALLOWED, "Unsupported type: " + fileType);
        if (fileSize > maxFileSize)
            throw new FileException(FileErrorCode.FILE_TOO_LARGE, "Max: " + FileVO.formatFileSize(maxFileSize));
    }

    /**
     * 原子预占房间配额（使用 Redis INCRBY）。
     * 预占成功后再检查是否超限，超限则立即释放。
     * 这样在预占到检查之间的窗口极短（毫秒级），即使高并发也不会出现明显超卖。
     */
    private void reserveRoomQuota(Long roomId, long fileSize) {
        String key = ROOM_QUOTA_KEY_PREFIX + roomId;
        Long used = redisTemplate.opsForValue().increment(key, fileSize);
        if (used != null && used > maxRoomSize) {
            redisTemplate.opsForValue().decrement(key, fileSize);
            throw new FileException(FileErrorCode.ROOM_QUOTA_EXCEEDED);
        }
    }

    /**
     * 释放房间配额（上传失败时调用）
     */
    private void releaseRoomQuota(Long roomId, long fileSize) {
        redisTemplate.opsForValue().decrement(ROOM_QUOTA_KEY_PREFIX + roomId, fileSize);
    }

    /**
     * 双重校验：Redis 与 DB 配额同步检查。
     * Redis 预占成功但极端情况下 DB 写入不符合预期（如事务回滚），返回 false。
     * 内部会纠正 Redis 值，使其与 DB 实际总大小一致。
     */
    private boolean syncAndCheckRoomQuota(Long roomId) {
        String key = ROOM_QUOTA_KEY_PREFIX + roomId;
        String redisVal = redisTemplate.opsForValue().get(key);
        if (redisVal == null) {
            return true; // 无 Redis 记录，以 DB 为准
        }
        long redisUsed = Long.parseLong(redisVal);
        long dbUsed = roomFileMapper.sumFileSizeByRoomId(roomId);
        // 允许 Redis 与 DB 有合理偏差（容错阈值，避免浮点误差）
        if (Math.abs(redisUsed - dbUsed) > QUOTA_DEVIATION_TOLERANCE_BYTES) {
            // 强制同步 Redis 值与 DB 实际值
            redisTemplate.opsForValue().set(key, String.valueOf(dbUsed));
            if (dbUsed > maxRoomSize) {
                return false;
            }
        }
        return true;
    }

    private String buildObjectKey(Long roomId, String cat, String name, String ext) {
        return String.format("room/%d/%s/%s.%s", roomId, cat, name, ext);
    }

    private String buildThumbnailObjectKey(String filePath) {
        if (filePath == null || !filePath.contains("/original/")) return filePath;
        return filePath.replace("/original/", "/thumbnail/").replaceFirst("(\\.[^.]+)$", "_thumb$1");
    }

    private void silentDeleteThumbnail(String filePath) {
        if (filePath == null || !filePath.contains("/original/")) return;
        String thumbnailKey = buildThumbnailObjectKey(filePath);
        silentDeleteFromMinio(thumbnailKey);
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
            log.error("[file-service] presigned url failed key:{} err:{}", key, e.getMessage(), e);
            throw new FileException(FileErrorCode.MINIO_OPERATION_FAILED, "Failed to generate URL");
        }
    }

    private String buildPresignedDownloadUrl(String key, String fileName) {
        try {
            String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("%", "%25");
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioProperties.getBucketName()).object(key)
                    .method(Method.GET)
                    .expiry((int) minioProperties.getPresignedUrlExpireSeconds(), TimeUnit.SECONDS)
                    .extraQueryParams(java.util.Map.of(
                            "response-content-disposition",
                            "attachment; filename*=UTF-8''" + encoded)).build());
        } catch (Exception e) {
            log.error("[file-service] presigned download url failed key:{} err:{}", key, e.getMessage(), e);
            throw new FileException(FileErrorCode.MINIO_OPERATION_FAILED, "Failed to generate download URL");
        }
    }

    private void silentDeleteFromMinio(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName()).object(key).build());
        } catch (Exception e) {
            log.error("[file-service] minio delete failed key:{} err:{}", key, e.getMessage(), e);
        }
    }

    private RoomFile getFileOrThrow(Long fileId) {
        RoomFile rf = roomFileMapper.selectById(fileId);
        if (rf == null) throw new FileException(FileErrorCode.FILE_NOT_FOUND);
        return rf;
    }

    private RoomFile buildRoomFile(Long roomId, Long userId, String nickname, String fileName,
                                    String filePath, String thumbnailKey, long fileSize,
                                    long thumbnailSize, String fileType, String contentType) {
        RoomFile f = new RoomFile();
        f.setRoomId(roomId);
        f.setUploaderId(userId);
        f.setUploaderNickname(nickname);
        f.setFileName(fileName);
        f.setFilePath(filePath);
        f.setThumbnailSize(thumbnailSize);
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
        vo.setThumbnailSize(r.getThumbnailSize());
        vo.setFileType(r.getFileType());
        vo.setContentType(r.getContentType());
        vo.setDownloadCount(r.getDownloadCount());
        vo.setUploadTime(r.getUploadTime());
        vo.setIconType(FileVO.resolveIconType(r.getFileType()));
        vo.setPreviewable(FileVO.isPreviewable(r.getFileType()));
        vo.setDownloadUrl(buildPresignedUrl(r.getFilePath()));
        vo.setPreviewUrl(buildPresignedUrl(buildThumbnailObjectKey(r.getFilePath())));
        return vo;
    }
}
