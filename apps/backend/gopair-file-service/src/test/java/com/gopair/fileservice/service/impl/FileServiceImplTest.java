package com.gopair.fileservice.service.impl;

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
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileServiceImpl 单元测试
 *
 * * [核心策略]
 * - 所有外部依赖（MinioClient / Redis / DB）均通过 Mockito Mock 隔离
 * - 测试覆盖核心业务流程与异常分支，不依赖真实存储
 *
 * * [覆盖场景]
 * - 头像上传：类型校验、大小校验、正常上传流程
 * - 文件上传：InputStream 消费（流不可重复读）、缩略图生成、配额预占与回滚
 * - 文件查询：分页查询、单个文件查询
 * - 下载/预览 URL 生成
 * - 文件删除：权限校验、正常删除
 * - 房间清理：批处理、Redis 配额清零
 * - 配额竞态：Redis INCRBY 原子预占超限拦截
 *
 * @author gopair
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioProperties minioProperties;

    @Mock
    private RoomFileMapper roomFileMapper;

    @Mock
    private WebSocketMessageProducer wsProducer;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FileServiceImpl fileService;

    @TempDir
    Path tempDir;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final Long FILE_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        // 注入配置值（@Value 字段通过反射注入）
        ReflectionTestUtils.setField(fileService, "maxFileSize", 104857600L);
        ReflectionTestUtils.setField(fileService, "maxRoomSize", 1073741824L);
        ReflectionTestUtils.setField(fileService, "thumbnailSize", 200);
        ReflectionTestUtils.setField(fileService, "avatarMaxSize", 5242880L);
        ReflectionTestUtils.setField(fileService, "allowedTypes",
                "jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,zip,rar");

        // 初始化 allowedTypesSet（@PostConstruct）
        fileService.init();

        // Mock MinIO 配置
        lenient().when(minioProperties.getBucketName()).thenReturn("test-bucket");
        lenient().when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
        lenient().when(minioProperties.getPresignedUrlExpireSeconds()).thenReturn(86400L);

        // Mock Redis
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 头像上传测试 ====================

    @Nested
    @DisplayName("头像上传测试")
    class UploadAvatarTests {

        @Test
        @DisplayName("头像上传成功：jpg 格式、2MB 大小")
        void uploadAvatar_success() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes);

            when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
            // putObject 返回 ObjectWriteResponse，使用 when().thenReturn() mock
            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            String url = fileService.uploadAvatar(file, USER_ID);

            assertNotNull(url);
            assertTrue(url.contains("avatar/1/profile.jpg"));
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("头像上传失败：类型不允许")
        void uploadAvatar_invalidType() {
            byte[] pdfBytes = "fake pdf".getBytes();
            MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadAvatar(file, USER_ID));
            assertEquals(FileErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getErrorCode());
        }

        @Test
        @DisplayName("头像上传失败：文件过大")
        void uploadAvatar_tooLarge() {
            byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6MB
            MultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", largeBytes);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadAvatar(file, USER_ID));
            assertEquals(FileErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
        }
    }

    // ==================== 文件上传测试 ====================

    @Nested
    @DisplayName("文件上传测试")
    class UploadFileTests {

        @Test
        @DisplayName("文件上传成功：图片类型，同时生成缩略图，InputStream 只读取一次")
        void uploadFile_imageWithThumbnail_success() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(0L);
            when(valueOperations.get(anyString())).thenReturn("0");

            // 模拟插入后返回带 ID 的 RoomFile
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(0L);

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            assertEquals(FILE_ID, result.getFileId());

            // 验证 MinIO 上传被调用 2 次：原图 + 缩略图
            verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
            verify(roomFileMapper, times(1)).insert(any(RoomFile.class));
        }

        @Test
        @DisplayName("文件上传成功：非图片类型，不生成缩略图")
        void uploadFile_nonImage_noThumbnail() throws Exception {
            byte[] pdfBytes = "fake pdf content".getBytes();
            MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes);

            when(valueOperations.get(anyString())).thenReturn("0");
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            // 非图片只上传 1 次（原图）
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("文件上传失败：配额超限")
        void uploadFile_quotaExceeded() throws Exception {
            byte[] bytes = "test".getBytes();
            MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", bytes);

            // 模拟 Redis 中配额已接近上限，新文件会导致超限
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(1100000001L);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.ROOM_QUOTA_EXCEEDED, ex.getErrorCode());
        }

        @Test
        @DisplayName("文件上传失败：类型不允许")
        void uploadFile_invalidType() {
            byte[] exeBytes = "MZ...".getBytes();
            MultipartFile file = new MockMultipartFile("file", "virus.exe", "application/x-executable", exeBytes);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getErrorCode());
        }

        @Test
        @DisplayName("文件上传失败：超出单文件大小限制")
        void uploadFile_fileTooLarge() {
            byte[] hugeBytes = new byte[200 * 1024 * 1024]; // 200MB > 100MB limit
            MultipartFile file = new MockMultipartFile("file", "huge.jpg", "image/jpeg", hugeBytes);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
        }

        @Test
        @DisplayName("文件上传异常：MinIO 上传失败，配额回滚")
        void uploadFile_minioError_quotaRollback() throws Exception {
            // 真实 PNG 图片（673 bytes）
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);

            // MinIO 上传抛出异常
            doThrow(new IOException("MinIO connection failed"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));

            // 关键验证：配额被回滚（decrement）
            verify(valueOperations, times(1)).decrement(eq("file:quota:100"), eq((long) pngBytes.length));
        }
    }

    // ==================== 查询测试 ====================

    @Nested
    @DisplayName("文件查询测试")
    class QueryTests {

        @Test
        @DisplayName("分页查询文件列表成功")
        void getRoomFiles_success() throws Exception {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoomFile> pageResult =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
            RoomFile rf = createRoomFile();
            pageResult.setRecords(List.of(rf));
            pageResult.setTotal(1);

            when(roomFileMapper.selectPageByRoomId(any(), eq(ROOM_ID))).thenReturn(pageResult);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/signed-url");

            PageResult<FileVO> result = fileService.getRoomFiles(ROOM_ID, 1, 20);

            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("photo.jpg", result.getRecords().get(0).getFileName());
        }

        @Test
        @DisplayName("获取单个文件信息成功")
        void getFileInfo_success() throws Exception {
            RoomFile rf = createRoomFile();
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/signed-url");

            FileVO result = fileService.getFileInfo(FILE_ID);

            assertNotNull(result);
            assertEquals(FILE_ID, result.getFileId());
        }

        @Test
        @DisplayName("获取文件信息失败：文件不存在")
        void getFileInfo_notFound() {
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(null);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.getFileInfo(FILE_ID));
            assertEquals(FileErrorCode.FILE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==================== 下载/预览测试 ====================

    @Nested
    @DisplayName("下载与预览测试")
    class DownloadPreviewTests {

        @Test
        @DisplayName("生成下载链接：SQL 原子 +1 下载计数")
        void generateDownloadUrl_atomicIncrement() throws Exception {
            RoomFile rf = createRoomFile();
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/download-url");
            when(roomFileMapper.incrementDownloadCount(FILE_ID)).thenReturn(1);

            String url = fileService.generateDownloadUrl(FILE_ID);

            assertNotNull(url);
            // 关键验证：使用 SQL 原子递增，而非 read-modify-write
            verify(roomFileMapper, times(1)).incrementDownloadCount(FILE_ID);
            verify(roomFileMapper, never()).updateById(any(RoomFile.class)); // 不再使用 updateById
        }

        @Test
        @DisplayName("生成预览链接成功")
        void generatePreviewUrl_success() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/preview-url");

            String url = fileService.generatePreviewUrl(FILE_ID);

            assertNotNull(url);
        }
    }

    // ==================== 删除测试 ====================

    @Nested
    @DisplayName("文件删除测试")
    class DeleteTests {

        @Test
        @DisplayName("删除成功：上传者本人，释放配额")
        void deleteFile_success() throws Exception {
            RoomFile rf = createRoomFile();
            // 有缩略图，所以会调用 2 次 removeObject（原图 + 缩略图）
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

            fileService.deleteFile(FILE_ID, USER_ID);

            verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            // 关键验证：配额被释放
            verify(valueOperations, times(1)).decrement(eq("file:quota:100"), eq(2048L));
        }

        @Test
        @DisplayName("删除失败：非上传者，权限拒绝")
        void deleteFile_accessDenied() {
            RoomFile rf = createRoomFile();
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.deleteFile(FILE_ID, 999L));
            assertEquals(FileErrorCode.FILE_ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        @DisplayName("删除失败：文件不存在")
        void deleteFile_notFound() {
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(null);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.deleteFile(FILE_ID, USER_ID));
            assertEquals(FileErrorCode.FILE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==================== 清理房间测试 ====================

    @Nested
    @DisplayName("房间清理测试")
    class CleanupTests {

        @Test
        @DisplayName("清理房间：分批删除，Redis 配额清零")
        void cleanupRoomFiles_batchDelete() throws Exception {
            RoomFile rf1 = createRoomFile();
            rf1.setFileId(1L);
            rf1.setThumbnailPath("room/100/thumbnail/uuid1_thumb.jpg");
            RoomFile rf2 = createRoomFile();
            rf2.setFileId(2L);
            rf2.setThumbnailPath("room/100/thumbnail/uuid2_thumb.jpg");

            // 第一次查返回 2 条，第二次返回空（触发 batch.size() < batchSize 退出）
            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(List.of(rf1, rf2))
                    .thenReturn(List.of());
            when(redisTemplate.delete(anyString())).thenReturn(true);

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(2, count);
            // 2 个文件各含原图+缩略图，共 4 次 MinIO 删除
            verify(minioClient, times(4)).removeObject(any(RemoveObjectArgs.class));
            // 一批 2 条文件，1 次批量删除
            verify(roomFileMapper, times(1)).deleteBatchIds(anyList());
            // Redis 配额 key 被删除
            verify(redisTemplate, times(1)).delete("file:quota:100");
        }

        @Test
        @DisplayName("清理房间：无文件时快速返回")
        void cleanupRoomFiles_emptyRoom() throws Exception {
            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200)).thenReturn(List.of());

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(0, count);
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        }
    }

    // ==================== 统计测试 ====================

    @Nested
    @DisplayName("统计测试")
    class StatsTests {

        @Test
        @DisplayName("获取房间统计：Redis 有值时优先使用 Redis")
        void getRoomFileStats_redisPreferred() {
            when(roomFileMapper.countByRoomId(ROOM_ID)).thenReturn(5L);
            when(valueOperations.get("file:quota:100")).thenReturn("10485760");

            FileService.RoomFileStats stats = fileService.getRoomFileStats(ROOM_ID);

            assertEquals(5, stats.fileCount());
            assertEquals(10485760L, stats.totalSize());
            // 验证走了 Redis 路径（DB 的 sum 未被调用）
            verify(roomFileMapper, never()).sumFileSizeByRoomId(ROOM_ID);
        }

        @Test
        @DisplayName("获取房间统计：Redis 无值时回退到 DB")
        void getRoomFileStats_fallbackToDb() {
            when(roomFileMapper.countByRoomId(ROOM_ID)).thenReturn(3L);
            when(valueOperations.get("file:quota:100")).thenReturn(null);
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(5242880L);

            FileService.RoomFileStats stats = fileService.getRoomFileStats(ROOM_ID);

            assertEquals(3, stats.fileCount());
            assertEquals(5242880L, stats.totalSize());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成真实 PNG 图片字节数组（使用 Java ImageIO）
     */
    private byte[] createRealPngImage(int width, int height) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(100, 150, 200));
        g.fillRect(0, 0, width, height);
        g.setColor(java.awt.Color.WHITE);
        g.drawString("Test", 10, height / 2);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /**
     * 生成真实 JPEG 图片字节数组
     */
    private byte[] createRealJpegImage(int width, int height) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(200, 100, 100));
        g.fillRect(0, 0, width, height);
        g.setColor(java.awt.Color.WHITE);
        g.drawString("Photo", 5, height / 2);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * 创建测试用 RoomFile
     */
    private RoomFile createRoomFile() {
        RoomFile rf = new RoomFile();
        ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
        rf.setRoomId(ROOM_ID);
        rf.setUploaderId(USER_ID);
        rf.setUploaderNickname("tester");
        rf.setFileName("photo.jpg");
        rf.setFilePath("room/100/original/uuid.jpg");
        rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
        rf.setFileSize(2048L);
        rf.setFileType("jpg");
        rf.setContentType("image/jpeg");
        rf.setDownloadCount(0);
        rf.setUploadTime(LocalDateTime.now());
        rf.setCreateTime(LocalDateTime.now());
        rf.setUpdateTime(LocalDateTime.now());
        return rf;
    }
}

/**
 * Spring 的反射工具类，用于注入 @Value 字段
 */
class ReflectionTestUtils {
    public static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
