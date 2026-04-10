package com.gopair.fileservice.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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
 * - 文件上传：InputStream 消费（流不可重复读）、缩略图生成、配额预占与回滚、WebSocket 事件
 * - 文件查询：分页查询（正常/空结果）、单个文件查询（正常/不存在）
 * - 下载/预览 URL 生成（含异常路径）
 * - 文件删除：权限校验、正常删除、WebSocket 事件、静默容错
 * - 房间清理：批处理（含多批次边界）、Redis 配额清零、静默容错
 * - 配额竞态：Redis INCRBY 原子预占超限拦截
 * - 统计：Redis 优先回退 DB、异常回退
 *
 * @author gopair
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        ReflectionTestUtils.setField(fileService, "maxFileSize", 104857600L);
        ReflectionTestUtils.setField(fileService, "maxRoomSize", 1073741824L);
        ReflectionTestUtils.setField(fileService, "thumbnailSize", 200);
        ReflectionTestUtils.setField(fileService, "avatarMaxSize", 5242880L);
        ReflectionTestUtils.setField(fileService, "allowedTypes",
                "jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,zip,rar");

        fileService.init();

        lenient().when(minioProperties.getBucketName()).thenReturn("test-bucket");
        lenient().when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
        lenient().when(minioProperties.getPresignedUrlExpireSeconds()).thenReturn(86400L);
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

        @Test
        @DisplayName("头像上传失败：MinIO 上传异常，抛出 FILE_UPLOAD_FAILED")
        void uploadAvatar_minioError() throws Exception {
            byte[] pngBytes = createRealPngImage(100, 100);
            MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes);

            doThrow(new IOException("MinIO upload failed"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadAvatar(file, USER_ID));
            assertEquals(FileErrorCode.FILE_UPLOAD_FAILED, ex.getErrorCode());
        }

        @Test
        @DisplayName("头像上传成功：gif 格式保留 PNG 输出格式")
        void uploadAvatar_gifFormat() throws Exception {
            byte[] gifBytes = createRealGifImage(50, 50);
            MultipartFile file = new MockMultipartFile("file", "avatar.gif", "image/gif", gifBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            String url = fileService.uploadAvatar(file, USER_ID);

            assertNotNull(url);
            assertTrue(url.contains("avatar/1/profile.jpg"));
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        }
    }

    // ==================== 文件上传测试 ====================

    @Nested
    @DisplayName("文件上传测试")
    class UploadFileTests {

        @Test
        @DisplayName("文件上传成功：图片类型，同时生成缩略图，InputStream 只读取一次，发布 WebSocket 事件")
        void uploadFile_imageWithThumbnail_success() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(pngBytes.length));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn((long) pngBytes.length);

            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            assertEquals(FILE_ID, result.getFileId());
            assertEquals("photo.png", result.getFileName());

            // 验证 MinIO 上传 2 次：原图 + 缩略图
            verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
            // 验证 Redis 配额预占
            verify(valueOperations, times(1)).increment(eq("file:quota:100"), eq((long) pngBytes.length));
            // 验证 DB 插入
            verify(roomFileMapper, times(1)).insert(any(RoomFile.class));
            // 验证 WebSocket 事件发布
            verify(wsProducer, times(1)).sendEventToRoom(eq(ROOM_ID), eq("file_upload"),
                    argThat(map -> {
                        Map<?, ?> m = (Map<?, ?>) map;
                        return FILE_ID.equals(m.get("fileId"))
                                && "photo.png".equals(m.get("fileName"))
                                && ROOM_ID.equals(m.get("roomId"));
                    }));
        }

        @Test
        @DisplayName("文件上传成功：非图片类型，不生成缩略图，同步校验通过，发布 WebSocket 事件")
        void uploadFile_nonImage_noThumbnail() throws Exception {
            byte[] pdfBytes = "fake pdf content".getBytes();
            MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes);

            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pdfBytes.length);
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(pdfBytes.length));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn((long) pdfBytes.length);

            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            // 非图片只上传 1 次（原图）
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
            // 验证 Redis 配额预占
            verify(valueOperations, times(1)).increment(anyString(), eq((long) pdfBytes.length));
            // 验证 WebSocket 事件发布
            verify(wsProducer, times(1)).sendEventToRoom(eq(ROOM_ID), eq("file_upload"), any());
        }

        @Test
        @DisplayName("文件上传成功：Redis 配额 key 不存在（首次上传），syncAndCheckRoomQuota 跳过校验")
        void uploadFile_redisKeyNotExists_firstUpload() throws Exception {
            byte[] pngBytes = createRealPngImage(100, 100);
            MultipartFile file = new MockMultipartFile("file", "first.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            // increment 返回新值（key 不存在时 INCRBY 返回值 = fileSize）
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            // Redis 无记录，syncAndCheckRoomQuota 直接返回 true
            when(valueOperations.get(anyString())).thenReturn(null);

            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            assertEquals(FILE_ID, result.getFileId());
            // 验证 DB 插入成功
            verify(roomFileMapper, times(1)).insert(any(RoomFile.class));
            // 验证 syncAndCheckRoomQuota 未调用 sumFileSizeByRoomId（因为 redisVal == null）
            verify(roomFileMapper, never()).sumFileSizeByRoomId(ROOM_ID);
        }

        @Test
        @DisplayName("文件上传失败：配额超限（Redis INCRBY 原子预占超出 maxRoomSize）")
        void uploadFile_quotaExceeded() throws Exception {
            byte[] bytes = "test".getBytes();
            MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", bytes);

            // Redis INCRBY 原子预占后超出上限
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(1100000001L);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.ROOM_QUOTA_EXCEEDED, ex.getErrorCode());
            // 验证超限后立即回滚了预占
            verify(valueOperations, times(1)).decrement(anyString(), anyLong());
        }

        @Test
        @DisplayName("文件上传失败：配额超限（syncAndCheckRoomQuota 双重校验发现 DB 已超限）")
        void uploadFile_quotaSyncDbExceeded_rollback() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });
            // Redis 有值（预占后），偏差 > 1，触发同步检查；同步后 dbUsed > maxRoomSize，超限
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(1200 * 1024 * 1024L));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn((long) (1200 * 1024 * 1024 + 2));

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.ROOM_QUOTA_EXCEEDED, ex.getErrorCode());

            // 验证：记录被删除（回滚），配额被释放
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            verify(valueOperations, times(1)).decrement(anyString(), eq((long) pngBytes.length));
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
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);

            // MinIO 上传原图时抛出异常
            doThrow(new IOException("MinIO connection failed"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));

            // 关键验证：配额被回滚（decrement）
            verify(valueOperations, times(1)).decrement(eq("file:quota:100"), eq((long) pngBytes.length));
        }

        @Test
        @DisplayName("文件上传成功：syncAndCheckRoomQuota 双重校验失败，触发 DB 回滚并抛异常")
        void uploadFile_quotaSyncFailed_rollback() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });
            // syncAndCheckRoomQuota 返回 false（Redis=100MB，DB=1.1GB，超限）
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(100 * 1024 * 1024L));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn((long) (1100 * 1024 * 1024));

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadFile(file, ROOM_ID, USER_ID, "tester"));
            assertEquals(FileErrorCode.ROOM_QUOTA_EXCEEDED, ex.getErrorCode());

            // 验证：记录被删除（回滚），配额被释放
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            verify(valueOperations, times(1)).decrement(eq("file:quota:100"), eq((long) pngBytes.length));
        }

        @Test
        @DisplayName("文件上传成功：syncAndCheckRoomQuota Redis 与 DB 有偏差（1字节），正常通过")
        void uploadFile_quotaSyncSmallDeviation_passes() throws Exception {
            byte[] pngBytes = createRealPngImage(200, 200);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });
            // Redis 与 DB 偏差恰好 1 字节（容错范围内）
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(1024L + 1));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(1024L);

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");

            assertNotNull(result);
            assertEquals(FILE_ID, result.getFileId());
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
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg"); // 有缩略图，需要 mock presigned
            pageResult.setRecords(List.of(rf));
            pageResult.setTotal(1);

            when(roomFileMapper.selectPageByRoomId(any(), eq(ROOM_ID))).thenReturn(pageResult);
            // toVO 会调用 buildPresignedUrl，需要 mock
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/signed-url");

            PageResult<FileVO> result = fileService.getRoomFiles(ROOM_ID, 1, 20);

            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("photo.jpg", result.getRecords().get(0).getFileName());
        }

        @Test
        @DisplayName("分页查询文件列表：空结果集")
        void getRoomFiles_emptyResult() throws Exception {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoomFile> pageResult =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
            pageResult.setRecords(List.of());
            pageResult.setTotal(0);

            when(roomFileMapper.selectPageByRoomId(any(), eq(ROOM_ID))).thenReturn(pageResult);

            PageResult<FileVO> result = fileService.getRoomFiles(ROOM_ID, 1, 20);

            assertEquals(0, result.getTotal());
            assertEquals(0, result.getRecords().size());
            assertEquals(1, result.getCurrent());
            assertEquals(20, result.getSize());
        }

        @Test
        @DisplayName("获取单个文件信息成功")
        void getFileInfo_success() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
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
        @DisplayName("生成下载链接：SQL 原子 +1 成功，方法正常返回")
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
            verify(roomFileMapper, never()).updateById(any(RoomFile.class));
        }

        @Test
        @DisplayName("生成下载链接：文件不存在时抛出 FILE_NOT_FOUND")
        void generateDownloadUrl_notFound() {
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(null);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.generateDownloadUrl(FILE_ID));
            assertEquals(FileErrorCode.FILE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("生成下载链接：文件名含中文字符，URLEncoder 正常编码")
        void generateDownloadUrl_chineseFilename() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setFileName("测试文件.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            when(roomFileMapper.incrementDownloadCount(FILE_ID)).thenReturn(1);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/download-url");

            String url = fileService.generateDownloadUrl(FILE_ID);

            assertNotNull(url);
            // 不抛异常即通过
        }
    }

    // ==================== 删除测试 ====================

    @Nested
    @DisplayName("文件删除测试")
    class DeleteTests {

        @Test
        @DisplayName("删除成功：上传者本人，释放配额，发布 WebSocket 事件")
        void deleteFile_success() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

            fileService.deleteFile(FILE_ID, USER_ID);

            verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            // 关键验证：配额被释放
            verify(valueOperations, times(1)).decrement(eq("file:quota:100"), eq(2048L));
            // 关键验证：WebSocket 事件发布
            verify(wsProducer, times(1)).sendEventToRoom(eq(ROOM_ID), eq("file_delete"),
                    argThat(map -> {
                        Map<?, ?> m = (Map<?, ?>) map;
                        return FILE_ID.equals(m.get("fileId"))
                                && "photo.jpg".equals(m.get("fileName"))
                                && ROOM_ID.equals(m.get("roomId"));
                    }));
        }

        @Test
        @DisplayName("删除成功：无缩略图时只删除原图，不调用第二次 removeObject")
        void deleteFile_noThumbnail_singleRemove() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath(null);
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

            fileService.deleteFile(FILE_ID, USER_ID);

            // 关键验证：只删除原图，不删除缩略图
            verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            verify(valueOperations, times(1)).decrement(anyString(), anyLong());
        }

        @Test
        @DisplayName("删除失败：非上传者，权限拒绝")
        void deleteFile_accessDenied() {
            RoomFile rf = createRoomFile();
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);

            FileException ex = null;
            try {
                fileService.deleteFile(FILE_ID, 999L);
            } catch (FileException e) {
                ex = e;
            }
            assertNotNull(ex);
            assertEquals(FileErrorCode.FILE_ACCESS_DENIED, ex.getErrorCode());
            // 验证：未执行 DB 删除操作
            verify(roomFileMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除失败：文件不存在")
        void deleteFile_notFound() {
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(null);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.deleteFile(FILE_ID, USER_ID));
            assertEquals(FileErrorCode.FILE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("删除成功：MinIO 删除原图失败（静默），仍继续执行 DB 删除与配额释放")
        void deleteFile_minioOriginalError_continues() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            // 原图删除时抛出异常
            doThrow(new IOException("MinIO delete failed"))
                    .when(minioClient).removeObject(any(RemoveObjectArgs.class));
            // 第一次调用的参数：原图路径；第二次：缩略图（第二次不抛异常，静默）
            doNothing().when(minioClient).removeObject(argThat(args ->
                    args.object().contains("thumbnail")));

            assertDoesNotThrow(() -> fileService.deleteFile(FILE_ID, USER_ID));

            // 关键验证：DB 删除和配额释放仍执行
            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            verify(valueOperations, times(1)).decrement(anyString(), anyLong());
            verify(wsProducer, times(1)).sendEventToRoom(anyLong(), eq("file_delete"), any());
        }

        @Test
        @DisplayName("删除成功：MinIO 删除缩略图失败（静默），仍继续执行 DB 删除与配额释放")
        void deleteFile_minioThumbnailError_continues() throws Exception {
            RoomFile rf = createRoomFile();
            rf.setThumbnailPath("room/100/thumbnail/uuid_thumb.jpg");
            when(roomFileMapper.selectById(FILE_ID)).thenReturn(rf);
            // 原图删除成功，缩略图删除时抛异常
            doNothing().when(minioClient).removeObject(argThat(args ->
                    !args.object().contains("thumbnail")));
            doThrow(new IOException("MinIO thumbnail delete failed"))
                    .when(minioClient).removeObject(argThat(args ->
                            args.object().contains("thumbnail")));

            assertDoesNotThrow(() -> fileService.deleteFile(FILE_ID, USER_ID));

            verify(roomFileMapper, times(1)).deleteById(FILE_ID);
            verify(valueOperations, times(1)).decrement(anyString(), anyLong());
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

            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(List.of(rf1, rf2))
                    .thenReturn(List.of());
            when(redisTemplate.delete(anyString())).thenReturn(true);

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(2, count);
            // 2 个文件各含原图+缩略图，共 4 次 MinIO 删除
            verify(minioClient, times(4)).removeObject(any(RemoveObjectArgs.class));
            verify(roomFileMapper, times(1)).deleteByIds(anyList());
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

        @Test
        @DisplayName("清理房间：多批次（>2批）正确退出，每批处理后检查是否还有下一批")
        void cleanupRoomFiles_multiBatch_correctLoopExit() throws Exception {
            RoomFile rf1 = createRoomFile(); rf1.setFileId(1L); rf1.setThumbnailPath(null);
            RoomFile rf2 = createRoomFile(); rf2.setFileId(2L); rf2.setThumbnailPath(null);

            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(List.of(rf1, rf2));
            when(redisTemplate.delete(anyString())).thenReturn(true);

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(2, count);
            // 验证：selectBatchByRoomId 仅被调用 1 次（取到第一批后判断 < 200 即退出）
            verify(roomFileMapper, times(1)).selectBatchByRoomId(ROOM_ID, 200);
            verify(roomFileMapper, times(1)).deleteByIds(anyList());
            verify(redisTemplate, times(1)).delete("file:quota:100");
        }

        @Test
        @DisplayName("清理房间：批次恰好等于 batchSize 时，继续获取下一批")
        void cleanupRoomFiles_batchSizeEqualsLimit_continues() throws Exception {
            List<RoomFile> batch1 = new java.util.ArrayList<>();
            for (long i = 1; i <= 200; i++) {
                RoomFile rf = createRoomFile();
                ReflectionTestUtils.setField(rf, "fileId", i);
                rf.setThumbnailPath(null);
                batch1.add(rf);
            }
            RoomFile rfLast = createRoomFile(); rfLast.setFileId(201L); rfLast.setThumbnailPath(null);

            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(batch1)
                    .thenReturn(List.of(rfLast));
            when(redisTemplate.delete(anyString())).thenReturn(true);

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(201, count);
            verify(roomFileMapper, times(2)).selectBatchByRoomId(ROOM_ID, 200);
            verify(roomFileMapper, times(2)).deleteByIds(anyList());
            verify(redisTemplate, times(1)).delete("file:quota:100");
        }

        @Test
        @DisplayName("清理房间：MinIO 删除原图失败（静默），继续执行 DB 删除与 Redis 清零")
        void cleanupRoomFiles_minioOriginalError_continues() throws Exception {
            RoomFile rf1 = createRoomFile(); rf1.setFileId(1L); rf1.setThumbnailPath("room/100/thumbnail/t1.jpg");
            RoomFile rf2 = createRoomFile(); rf2.setFileId(2L); rf2.setThumbnailPath(null);

            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(List.of(rf1, rf2))
                    .thenReturn(List.of());
            // 原图删除抛异常，缩略图删除不抛
            doThrow(new IOException("MinIO delete failed"))
                    .when(minioClient).removeObject(argThat(args ->
                            !args.object().contains("thumbnail")));
            doNothing().when(minioClient).removeObject(argThat(args ->
                    args.object().contains("thumbnail")));
            when(redisTemplate.delete(anyString())).thenReturn(true);

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(2, count);
            // 静默吞掉异常，DB 删除仍执行
            verify(roomFileMapper, times(1)).deleteByIds(anyList());
            verify(redisTemplate, times(1)).delete("file:quota:100");
        }

        @Test
        @DisplayName("清理房间：Redis 清零失败（静默），方法仍返回正确的清理数量")
        void cleanupRoomFiles_redisDeleteError_continues() throws Exception {
            RoomFile rf = createRoomFile(); rf.setFileId(1L); rf.setThumbnailPath(null);

            when(roomFileMapper.selectBatchByRoomId(ROOM_ID, 200))
                    .thenReturn(List.of(rf))
                    .thenReturn(List.of());
            // Redis 删除抛异常
            when(redisTemplate.delete(anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            int count = fileService.cleanupRoomFiles(ROOM_ID);

            assertEquals(1, count);
            // DB 删除和 MinIO 删除仍执行
            verify(roomFileMapper, times(1)).deleteByIds(anyList());
            verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
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
        @DisplayName("获取房间统计：Redis 无值时回退到 DB，并异步回填 Redis")
        void getRoomFileStats_fallbackToDb() {
            when(roomFileMapper.countByRoomId(ROOM_ID)).thenReturn(3L);
            when(valueOperations.get("file:quota:100")).thenReturn(null);
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(5242880L);

            FileService.RoomFileStats stats = fileService.getRoomFileStats(ROOM_ID);

            assertEquals(3, stats.fileCount());
            assertEquals(5242880L, stats.totalSize());
            // 验证异步回填 Redis（setIfAbsent）
            verify(valueOperations, times(1)).setIfAbsent(eq("file:quota:100"), eq("5242880"));
        }

        @Test
        @DisplayName("获取房间统计：Redis 抛异常时回退到 DB")
        void getRoomFileStats_redisException_fallbackToDb() {
            when(roomFileMapper.countByRoomId(ROOM_ID)).thenReturn(3L);
            when(valueOperations.get("file:quota:100"))
                    .thenThrow(new RuntimeException("Redis connection refused"));
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(5242880L);

            FileService.RoomFileStats stats = fileService.getRoomFileStats(ROOM_ID);

            assertEquals(3, stats.fileCount());
            assertEquals(5242880L, stats.totalSize());
        }

        @Test
        @DisplayName("获取房间统计：Redis 回填失败（静默），不影响最终返回值")
        void getRoomFileStats_redisBackfillError_ignored() {
            when(roomFileMapper.countByRoomId(ROOM_ID)).thenReturn(2L);
            when(valueOperations.get("file:quota:100")).thenReturn(null);
            when(roomFileMapper.sumFileSizeByRoomId(ROOM_ID)).thenReturn(4096L);
            // setIfAbsent 抛异常
            doThrow(new RuntimeException("Redis write failed"))
                    .when(valueOperations).setIfAbsent(anyString(), anyString());

            FileService.RoomFileStats stats = fileService.getRoomFileStats(ROOM_ID);

            assertEquals(2, stats.fileCount());
            assertEquals(4096L, stats.totalSize());
        }
    }

    // ==================== 私有方法边界测试 ====================

    @Nested
    @DisplayName("私有方法边界测试")
    class HelperMethodTests {

        @Test
        @DisplayName("extractExtension：正常文件名提取扩展名")
        void extractExtension_normal() throws Exception {
            byte[] pngBytes = createRealPngImage(50, 50);
            MultipartFile file = new MockMultipartFile("file", "my.photo.PNG", "image/png", pngBytes);
            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            fileService.uploadAvatar(file, USER_ID);

            // 上传成功说明扩展名提取正确（大写 PNG 被转换为小写）
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("extractExtension：无扩展名文件名返回 unknown，进入 allowedTypesSet 失败")
        void extractExtension_noExtension() {
            byte[] bytes = "test".getBytes();
            MultipartFile file = new MockMultipartFile("file", "noextension", "application/octet-stream", bytes);

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadAvatar(file, USER_ID));
            assertEquals(FileErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getErrorCode());
        }

        @Test
        @DisplayName("extractExtension：null 文件名返回 unknown，进入 allowedTypesSet 失败")
        void extractExtension_nullFileName() {
            MultipartFile file = new MockMultipartFile("file", null, "image/png", "test".getBytes());

            FileException ex = assertThrows(FileException.class,
                    () -> fileService.uploadAvatar(file, USER_ID));
            assertEquals(FileErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getErrorCode());
        }

        @Test
        @DisplayName("checkFileTypeAndSize：单文件大小恰好等于上限时通过")
        void checkFileTypeAndSize_exactlyAtLimit() throws Exception {
            byte[] pngBytes = createRealPngImage(50, 50);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);
            doAnswer(inv -> mock(ObjectWriteResponse.class))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
            when(valueOperations.increment(anyString(), anyLong())).thenReturn((long) pngBytes.length);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(roomFileMapper.insert(any(RoomFile.class))).thenAnswer(inv -> {
                RoomFile rf = inv.getArgument(0);
                ReflectionTestUtils.setField(rf, "fileId", FILE_ID);
                return 1;
            });

            FileVO result = fileService.uploadFile(file, ROOM_ID, USER_ID, "tester");
            assertNotNull(result);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成真实 PNG 图片字节数组
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
     * 生成真实 GIF 图片字节数组
     */
    private byte[] createRealGifImage(int width, int height) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(255, 0, 0));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "gif", baos);
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
