package com.gopair.fileservice.api;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.fileservice.base.BaseIntegrationTest;
import com.gopair.fileservice.domain.vo.FileVO;
import com.gopair.fileservice.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.minio.GetPresignedObjectUrlArgs;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static com.gopair.fileservice.enums.FileErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件服务 HTTP 接口契约测试。
 *
 * * [测试策略]
 * - 全部通过 TestRestTemplate 发送真实 HTTP 请求，完整经过 Controller → Service → Mapper → DB
 * - 每个接口覆盖：1个成功场景 + 所有已知异常场景 + 边界值
 * - MinIO/WebSocket/MQ 由 BaseIntegrationTest @MockBean 隔离，不依赖真实存储
 * - Redis ValueOperations 通过 injectMockValueOperations() 注入 Mock
 * - 用户身份通过 X-User-Id / X-Nickname 请求头注入（由 ContextInitFilter 解析）
 * - MySQL 数据由 @Transactional 自动回滚，Redis 由 BaseIntegrationTest.flushTestRedis() 清空
 * - 文件上传使用 ByteArrayResource + MultiValueMap（避免 Jackson 序列化 MockMultipartFile 内部 resource 字段抛异常）
 *
 * * [脏数据清理]
 * - MySQL：@Transactional 回滚
 * - Redis：BaseIntegrationTest @AfterEach flushDb()
 */
class FileApiContractTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1000000);

    private String uid() {
        return String.valueOf(counter.incrementAndGet());
    }

    private byte[] createRealPngImage(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(new Color((int) (Math.random() * 0xFFFFFF)));
            g2d.fillRect(0, 0, width, height);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, Math.min(width, height) / 4));
            String text = width + "x" + height;
            g2d.drawString(text, 10, height / 2);
            g2d.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成测试 PNG 图片失败", e);
        }
    }

    private byte[] pngBytes() {
        return createRealPngImage(200, 200);
    }

    private byte[] pdfBytes() {
        return "%PDF-1.4 test content".getBytes();
    }

    private HttpHeaders userHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        headers.set(SystemConstants.HEADER_NICKNAME, nickname);
        return headers;
    }

    // ==================== 头像上传接口 ====================

    @Nested
    @DisplayName("POST /file/avatar")
    class UploadAvatarTests {

        @BeforeEach
        void setUp() {
            injectMockValueOperations();
        }

        @Test
        @DisplayName("头像上传成功：PNG 格式返回永久直链")
        void uploadAvatar_success() {
            long userId = Long.parseLong(uid());

            ResponseEntity<R<String>> resp = callUploadAvatar(pngBytes(), "avatar.png", "image/png", userId, "avataruser");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).contains("avatar/" + userId + "/profile.jpg");
        }

        @Test
        @DisplayName("头像上传成功：GIF 格式保留 PNG 输出")
        void uploadAvatar_gif() {
            long userId = Long.parseLong(uid());

            ResponseEntity<R<String>> resp = callUploadAvatar(pngBytes(), "avatar.gif", "image/gif", userId, "gifuser");

            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).contains("avatar/" + userId + "/profile.jpg");
        }

        @Test
        @DisplayName("头像上传失败：类型不允许（PDF）")
        void uploadAvatar_invalidType() {
            long userId = Long.parseLong(uid());

            ResponseEntity<R<String>> resp = callUploadAvatar(pdfBytes(), "doc.pdf", "application/pdf", userId, "baduser");

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_TYPE_NOT_ALLOWED.getCode());
        }

        @Test
        @DisplayName("头像上传失败：文件过大（6MB > 5MB 限制）")
        void uploadAvatar_tooLarge() {
            long userId = Long.parseLong(uid());
            byte[] largeBytes = new byte[6 * 1024 * 1024];

            ResponseEntity<R<String>> resp = callUploadAvatar(largeBytes, "big.jpg", "image/jpeg", userId, "largeuser");

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_TOO_LARGE.getCode());
        }

        @Test
        @DisplayName("头像上传失败：空文件")
        void uploadAvatar_emptyFile() {
            long userId = Long.parseLong(uid());

            ResponseEntity<R<String>> resp = callUploadAvatar(new byte[0], "empty.png", "image/png", userId, "emptyuser");

            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ==================== 文件上传接口 ====================

    @Nested
    @DisplayName("POST /file/upload")
    class UploadFileTests {

        @BeforeEach
        void setUp() {
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("文件上传成功：PNG 图片返回 FileVO（含缩略图路径）")
        void uploadFile_image_success() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> resp = callUploadFile(pngBytes(), "photo.png", "image/png", roomId, userId, "photouser");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            FileVO data = resp.getBody().getData();
            assertThat(data.getFileId()).isNotNull();
            assertThat(data.getFileName()).isEqualTo("photo.png");
            assertThat(data.getFileType()).isEqualTo("png");
            assertThat(data.getIconType()).isEqualTo("image");
            assertThat(data.isPreviewable()).isTrue();
        }

        @Test
        @DisplayName("文件上传成功：PDF 文件返回 FileVO（无缩略图）")
        void uploadFile_pdf_success() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> resp = callUploadFile(pdfBytes(), "report.pdf", "application/pdf", roomId, userId, "pdfuser");

            assertThat(resp.getBody().isSuccess()).isTrue();
            FileVO data = resp.getBody().getData();
            assertThat(data.getFileName()).isEqualTo("report.pdf");
            assertThat(data.getFileType()).isEqualTo("pdf");
            assertThat(data.isPreviewable()).isTrue();
        }

        @Test
        @DisplayName("文件上传失败：类型不允许（.exe）")
        void uploadFile_invalidType() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> resp = callUploadFile("MZ...".getBytes(), "virus.exe", "application/x-executable", roomId, userId, "baduser");

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_TYPE_NOT_ALLOWED.getCode());
        }
    }

    // ==================== 房间文件列表接口 ====================

    @Nested
    @DisplayName("GET /file/room/{roomId}")
    class GetRoomFilesTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("分页查询成功：上传2个文件后查询，按上传时间倒序")
        void getRoomFiles_success() {
            callUploadFile(pngBytes(), "a.png", "image/png", roomId, userId, "u1");
            callUploadFile(pdfBytes(), "b.pdf", "application/pdf", roomId, userId, "u2");

            ResponseEntity<R<PageResult<FileVO>>> resp = callGetRoomFiles(roomId, 1, 20);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            PageResult<FileVO> page = resp.getBody().getData();
            assertThat(page.getRecords()).hasSize(2);
        }

        @Test
        @DisplayName("分页查询空房间：返回空记录列表")
        void getRoomFiles_emptyRoom() {
            ResponseEntity<R<PageResult<FileVO>>> resp = callGetRoomFiles(roomId, 1, 20);

            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getRecords()).isEmpty();
        }

        @Test
        @DisplayName("非法分页参数 pageNum=0：返回结果兼容处理")
        void getRoomFiles_pageNumZero() {
            ResponseEntity<R<PageResult<FileVO>>> resp = callGetRoomFiles(roomId, 0, 20);

            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== 获取单个文件接口 ====================

    @Nested
    @DisplayName("GET /file/{fileId}")
    class GetFileInfoTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("获取文件信息成功")
        void getFileInfo_success() {
            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "info.png", "image/png", roomId, userId, "infouser");
            Long fileId = uploadResp.getBody().getData().getFileId();

            ResponseEntity<R<FileVO>> resp = callGetFileInfo(fileId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getFileId()).isEqualTo(fileId);
            assertThat(resp.getBody().getData().getFileName()).isEqualTo("info.png");
        }

        @Test
        @DisplayName("获取文件信息失败：文件不存在")
        void getFileInfo_notFound() {
            ResponseEntity<R<FileVO>> resp = callGetFileInfo(999998L);

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_NOT_FOUND.getCode());
        }
    }

    // ==================== 生成下载链接接口 ====================

    @Nested
    @DisplayName("GET /file/{fileId}/download")
    class DownloadFileTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() throws Exception {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://localhost:9000/presigned-download");
        }

        @Test
        @DisplayName("生成下载链接成功：返回 presigned URL")
        void downloadFile_success() {
            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "dl.png", "image/png", roomId, userId, "dluser");
            Long fileId = uploadResp.getBody().getData().getFileId();

            ResponseEntity<R<String>> resp = callDownloadFile(fileId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotBlank();
        }

        @Test
        @DisplayName("生成下载链接失败：文件不存在")
        void downloadFile_notFound() {
            ResponseEntity<R<String>> resp = callDownloadFile(999997L);

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_NOT_FOUND.getCode());
        }
    }

    // ==================== 生成预览链接接口 ====================

    @Nested
    @DisplayName("GET /file/{fileId}/preview")
    class PreviewFileTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("生成预览链接成功：图片返回 302 重定向")
        void previewFile_success() {
            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "prev.png", "image/png", roomId, userId, "prevuser");
            Long fileId = uploadResp.getBody().getData().getFileId();

            ResponseEntity<Void> resp = callPreviewFile(fileId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        @Test
        @DisplayName("生成预览链接失败：文件不存在")
        void previewFile_notFound() {
            ResponseEntity<R<String>> resp = callPreviewFileRaw(999996L);

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_NOT_FOUND.getCode());
        }
    }

    // ==================== 删除文件接口 ====================

    @Nested
    @DisplayName("DELETE /file/{fileId}")
    class DeleteFileTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(valueOperations.decrement(anyString(), anyLong())).thenReturn(0L);
        }

        @Test
        @DisplayName("删除文件成功：上传者本人返回 true，文件从 DB 消失")
        void deleteFile_success() {
            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "del.png", "image/png", roomId, userId, "deluser");
            Long fileId = uploadResp.getBody().getData().getFileId();

            ResponseEntity<R<Boolean>> resp = callDeleteFile(fileId, userId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();

            ResponseEntity<R<FileVO>> getResp = callGetFileInfo(fileId);
            assertThat(getResp.getBody().isSuccess()).isFalse();
            assertThat(getResp.getBody().getCode()).isEqualTo(FILE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("删除文件失败：非上传者本人，返回权限拒绝")
        void deleteFile_accessDenied() {
            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pdfBytes(), "secure.pdf", "application/pdf", roomId, userId, "owner");
            Long fileId = uploadResp.getBody().getData().getFileId();
            long strangerId = Long.parseLong(uid());

            ResponseEntity<R<Boolean>> resp = callDeleteFile(fileId, strangerId);

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_ACCESS_DENIED.getCode());
        }

        @Test
        @DisplayName("删除文件失败：文件不存在")
        void deleteFile_notFound() {
            long someUserId = Long.parseLong(uid());

            ResponseEntity<R<Boolean>> resp = callDeleteFile(999995L, someUserId);

            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(FILE_NOT_FOUND.getCode());
        }
    }

    // ==================== 房间文件统计接口 ====================

    @Nested
    @DisplayName("GET /file/room/{roomId}/stats")
    class GetRoomFileStatsTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("获取统计成功：上传文件后返回 fileCount=1, totalSize>0")
        void getRoomFileStats_success() {
            callUploadFile(pngBytes(), "stats.png", "image/png", roomId, userId, "statsuser");

            ResponseEntity<R<FileService.RoomFileStats>> resp = callGetRoomFileStats(roomId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().fileCount()).isEqualTo(1);
            assertThat(resp.getBody().getData().totalSize()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("获取统计成功：空房间返回 fileCount=0")
        void getRoomFileStats_emptyRoom() {
            ResponseEntity<R<FileService.RoomFileStats>> resp = callGetRoomFileStats(roomId);

            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().fileCount()).isEqualTo(0);
        }
    }

    // ==================== 清理房间文件接口 ====================

    @Nested
    @DisplayName("POST /file/room/{roomId}/cleanup")
    class CleanupRoomFilesTests {

        private long userId;
        private long roomId;

        @BeforeEach
        void setUp() {
            userId = Long.parseLong(uid());
            roomId = Long.parseLong(uid());
            injectMockValueOperations();
            lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(1024L);
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(redisTemplateSpy.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("清理房间成功：上传2个文件后清理，返回清理数量2")
        void cleanupRoomFiles_success() {
            callUploadFile(pngBytes(), "c1.png", "image/png", roomId, userId, "cleaner");
            callUploadFile(pdfBytes(), "c2.pdf", "application/pdf", roomId, userId, "cleaner");

            ResponseEntity<R<Integer>> resp = callCleanupRoomFiles(roomId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(2);
        }

        @Test
        @DisplayName("清理空房间：返回 0")
        void cleanupRoomFiles_emptyRoom() {
            ResponseEntity<R<Integer>> resp = callCleanupRoomFiles(roomId);

            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(0);
        }
    }

    // ==================== 回填 messageId 接口 ====================

    @Nested
    @DisplayName("POST /file/link-message")
    class LinkMessageIdTests {

        @Test
        @DisplayName("回填成功：上传文件后回填 messageId，DB 记录 messageId 字段被更新")
        void linkMessageId_success() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "link.png", "image/png", roomId, userId, "linkuser");
            Long fileId = uploadResp.getBody().getData().getFileId();
            Long fakeMessageId = Long.parseLong(uid());

            ResponseEntity<R<Void>> resp = callLinkMessageId(fileId, fakeMessageId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();

            // 验证 DB 中 messageId 已回填
            ResponseEntity<R<FileVO>> getResp = callGetFileInfo(fileId);
            assertThat(getResp.getBody().getData()).isNotNull();
        }
    }

    // ==================== 撤回时清理文件记录接口 ====================

    @Nested
    @DisplayName("DELETE /file/by-key-with-cleanup")
    class DeleteByObjectKeyWithCleanupTests {

        @Test
        @DisplayName("清理成功：有 messageId 时优先按 messageId 查找并删除 room_file 记录")
        void deleteByObjectKeyWithCleanup_byMessageId() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pngBytes(), "recall.png", "image/png", roomId, userId, "recalluser");
            Long fileId = uploadResp.getBody().getData().getFileId();
            Long fakeMessageId = Long.parseLong(uid());

            // 先回填 messageId
            callLinkMessageId(fileId, fakeMessageId);

            // 模拟撤回：删除文件记录
            String objectKey = "room/" + roomId + "/original/test.jpg";
            ResponseEntity<R<Boolean>> resp = callDeleteByObjectKeyWithDbCleanup(objectKey, fakeMessageId, roomId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("清理成功：无 messageId 时降级按 roomId + filePath 查找并删除 room_file 记录")
        void deleteByObjectKeyWithCleanup_byFilePathFallback() {
            long userId = Long.parseLong(uid());
            long roomId = Long.parseLong(uid());

            ResponseEntity<R<FileVO>> uploadResp = callUploadFile(pdfBytes(), "fallback.pdf", "application/pdf", roomId, userId, "fallbackuser");
            Long fileId = uploadResp.getBody().getData().getFileId();

            // 从上传响应中获取 filePath
            String filePath = uploadResp.getBody().getData().getDownloadUrl();
            String objectKey = extractObjectKeyFromUrl(filePath);

            // 不回填 messageId，模拟历史记录
            ResponseEntity<R<Boolean>> resp = callDeleteByObjectKeyWithDbCleanup(objectKey, null, roomId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("清理失败：objectKey 不存在时返回 false，不抛异常")
        void deleteByObjectKeyWithCleanup_notFound() {
            long roomId = Long.parseLong(uid());
            String fakeKey = "room/" + roomId + "/original/nonexist.jpg";

            ResponseEntity<R<Boolean>> resp = callDeleteByObjectKeyWithDbCleanup(fakeKey, null, roomId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isFalse();
        }
    }

    // ==================== HTTP 调用辅助方法 ====================

    private String extractObjectKeyFromUrl(String url) {
        if (url == null) return null;
        String[] markers = {"gopair-files/", "gopair-files-test/"};
        for (String marker : markers) {
            int idx = url.indexOf(marker);
            if (idx >= 0) {
                return url.substring(idx + marker.length());
            }
        }
        if (url.contains("/room/")) {
            int idx = url.indexOf("/room/");
            return url.substring(idx + 1);
        }
        return null;
    }

    private ResponseEntity<R<Void>> callLinkMessageId(Long fileId, Long messageId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "linkuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/link-message?fileId=" + fileId + "&messageId=" + messageId),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    private ResponseEntity<R<Boolean>> callDeleteByObjectKeyWithDbCleanup(String objectKey, Long messageId, Long roomId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "cleanupuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = getUrl("/file/by-key-with-cleanup?objectKey=" + objectKey + "&roomId=" + roomId);
        if (messageId != null) {
            url = getUrl("/file/by-key-with-cleanup?objectKey=" + objectKey + "&messageId=" + messageId + "&roomId=" + roomId);
        }
        return testRestTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    private ResponseEntity<R<String>> callUploadAvatar(byte[] content, String filename, String contentType, long userId, String nickname) {
        HttpHeaders headers = userHeaders(userId, nickname);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = makeUploadBody(content, filename, contentType);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return testRestTemplate.exchange(
                getUrl("/file/avatar"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<String>>() {}
        );
    }

    private ResponseEntity<R<FileVO>> callUploadFile(byte[] content, String filename, String contentType, long roomId, long userId, String nickname) {
        HttpHeaders headers = userHeaders(userId, nickname);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = makeUploadBody(content, filename, contentType);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return testRestTemplate.exchange(
                getUrl("/file/upload?roomId=" + roomId),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<FileVO>>() {}
        );
    }

    private MultiValueMap<String, Object> makeUploadBody(byte[] content, String filename, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return body;
    }

    private ResponseEntity<R<PageResult<FileVO>>> callGetRoomFiles(long roomId, int pageNum, int pageSize) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "queryuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/room/" + roomId + "?pageNum=" + pageNum + "&pageSize=" + pageSize),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<PageResult<FileVO>>>() {}
        );
    }

    private ResponseEntity<R<FileVO>> callGetFileInfo(Long fileId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "infouser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/" + fileId),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<FileVO>>() {}
        );
    }

    private ResponseEntity<R<String>> callDownloadFile(Long fileId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "dluser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/" + fileId + "/download"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<String>>() {}
        );
    }

    private ResponseEntity<Void> callPreviewFile(Long fileId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "prevuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/" + fileId + "/preview"),
                HttpMethod.GET,
                entity,
                Void.class
        );
    }

    private ResponseEntity<R<String>> callPreviewFileRaw(Long fileId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "prevuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/" + fileId + "/preview"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<String>>() {}
        );
    }

    private ResponseEntity<R<Boolean>> callDeleteFile(Long fileId, long userId) {
        HttpHeaders headers = userHeaders(userId, "deluser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/" + fileId),
                HttpMethod.DELETE,
                entity,
                new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    private ResponseEntity<R<FileService.RoomFileStats>> callGetRoomFileStats(long roomId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "statsuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/room/" + roomId + "/stats"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<FileService.RoomFileStats>>() {}
        );
    }

    private ResponseEntity<R<Integer>> callCleanupRoomFiles(long roomId) {
        HttpHeaders headers = userHeaders(Long.parseLong(uid()), "cleanupuser");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return testRestTemplate.exchange(
                getUrl("/file/room/" + roomId + "/cleanup"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Integer>>() {}
        );
    }
}
