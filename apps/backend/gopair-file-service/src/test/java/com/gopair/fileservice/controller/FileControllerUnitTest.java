//package com.gopair.fileservice.controller;
//
//import com.gopair.common.core.PageResult;
//import com.gopair.common.core.R;
//import com.gopair.fileservice.domain.vo.FileVO;
//import com.gopair.fileservice.enums.FileErrorCode;
//import com.gopair.fileservice.exception.FileException;
//import com.gopair.fileservice.service.FileService;
//import com.gopair.framework.context.UserContext;
//import com.gopair.framework.context.UserContextHolder;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.mockito.junit.jupiter.MockitoSettings;
//import org.mockito.quality.Strictness;
//import org.springframework.http.ResponseEntity;
//import org.springframework.mock.web.MockMultipartFile;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
///**
// * FileController 单元测试。
// *
// * * [核心策略]
// * - 使用 @ExtendWith(MockitoExtension.class)，禁止启动 Spring 容器。
// * - Service 层全部 @Mock，Controller 通过 @InjectMocks 注入。
// * - UserContextHolder 通过静态方法设置，模拟登录用户。
// * - 验证参数解析、响应封装（R.ok()）、异常翻译是否正确。
// *
// * * [覆盖场景]
// * - 头像上传：成功路径（URL 封装）、异常路径（Service 抛 FileException）
// * - 文件上传：成功路径（FileVO 完整封装）、异常路径
// * - 文件查询：分页查询（PageResult 封装）、单个查询（FileVO 封装）
// * - 下载/预览：URL 字符串封装
// * - 文件删除：成功路径（Boolean 封装）、权限异常翻译
// * - 房间统计：RoomFileStats 记录封装
// * - 房间清理：Integer 计数封装
// *
// * @author gopair
// */
//@ExtendWith(MockitoExtension.class)
//@MockitoSettings(strictness = Strictness.LENIENT)
//@DisplayName("FileController 单元测试")
//class FileControllerUnitTest {
//
//    @Mock
//    private FileService fileService;
//
//    @InjectMocks
//    private FileController fileController;
//
//    private static final Long USER_ID = 1001L;
//    private static final String NICKNAME = "测试用户";
//    private static final Long ROOM_ID = 2001L;
//    private static final Long FILE_ID = 10L;
//
//    @BeforeEach
//    void setUp() {
//        UserContextHolder.setContext(UserContext.of(USER_ID, NICKNAME));
//    }
//
//    @AfterEach
//    void tearDown() {
//        UserContextHolder.clear();
//    }
//
//    // ==================== 头像上传测试 ====================
//
//    @Nested
//    @DisplayName("头像上传测试")
//    class UploadAvatarTests {
//
//        @Test
//        @DisplayName("头像上传成功：返回永久直链 URL")
//        void uploadAvatar_success() {
//            MockMultipartFile file = new MockMultipartFile(
//                    "file", "avatar.png", "image/png", "fake".getBytes());
//            String expectedUrl = "http://localhost:9000/gopair/avatar/1001/profile.jpg";
//
//            when(fileService.uploadAvatar(any(), eq(USER_ID))).thenReturn(expectedUrl);
//
//            R<String> result = fileController.uploadAvatar(file);
//
//            assertNotNull(result);
//            assertEquals(expectedUrl, result.getData());
//            verify(fileService, times(1)).uploadAvatar(any(), eq(USER_ID));
//        }
//
//        @Test
//        @DisplayName("头像上传失败：类型不允许，异常被正确翻译")
//        void uploadAvatar_typeNotAllowed() {
//            MockMultipartFile file = new MockMultipartFile(
//                    "file", "doc.pdf", "application/pdf", "fake".getBytes());
//
//            when(fileService.uploadAvatar(any(), eq(USER_ID)))
//                    .thenThrow(new FileException(FileErrorCode.FILE_TYPE_NOT_ALLOWED));
//
//            assertThrows(FileException.class, () -> fileController.uploadAvatar(file));
//        }
//
//        @Test
//        @DisplayName("头像上传失败：文件过大，异常被正确翻译")
//        void uploadAvatar_tooLarge() {
//            MockMultipartFile file = new MockMultipartFile(
//                    "file", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);
//
//            when(fileService.uploadAvatar(any(), eq(USER_ID)))
//                    .thenThrow(new FileException(FileErrorCode.FILE_TOO_LARGE));
//
//            assertThrows(FileException.class, () -> fileController.uploadAvatar(file));
//        }
//    }
//
//    // ==================== 文件上传测试 ====================
//
//    @Nested
//    @DisplayName("文件上传测试")
//    class UploadFileTests {
//
//        @Test
//        @DisplayName("文件上传成功：返回带 fileId 和 downloadUrl 的 FileVO")
//        void uploadFile_success() {
//            MockMultipartFile file = new MockMultipartFile(
//                    "file", "report.pdf", "application/pdf", "content".getBytes());
//
//            FileVO mockVO = createMockFileVO("report.pdf", "pdf", 1024L);
//            when(fileService.uploadFile(any(), eq(ROOM_ID), eq(USER_ID), eq(NICKNAME)))
//                    .thenReturn(mockVO);
//
//            R<FileVO> result = fileController.uploadFile(file, ROOM_ID);
//
//            assertNotNull(result);
//            assertNotNull(result.getData());
//            assertEquals(FILE_ID, result.getData().getFileId());
//            assertEquals("report.pdf", result.getData().getFileName());
//            verify(fileService, times(1))
//                    .uploadFile(any(), eq(ROOM_ID), eq(USER_ID), eq(NICKNAME));
//        }
//
//        @Test
//        @DisplayName("文件上传失败：配额超限")
//        void uploadFile_quotaExceeded() {
//            MockMultipartFile file = new MockMultipartFile(
//                    "file", "huge.pdf", "application/pdf", new byte[1024]);
//
//            when(fileService.uploadFile(any(), eq(ROOM_ID), anyLong(), anyString()))
//                    .thenThrow(new FileException(FileErrorCode.ROOM_QUOTA_EXCEEDED));
//
//            assertThrows(FileException.class,
//                    () -> fileController.uploadFile(file, ROOM_ID));
//        }
//    }
//
//    // ==================== 查询测试 ====================
//
//    @Nested
//    @DisplayName("文件查询测试")
//    class QueryTests {
//
//        @Test
//        @DisplayName("分页查询成功：返回 PageResult<FileVO>")
//        void getRoomFiles_success() {
//            PageResult<FileVO> pageResult = new PageResult<>();
//            pageResult.setRecords(List.of(createMockFileVO("a.txt", "txt", 100L)));
//            pageResult.setTotal(1L);
//            pageResult.setCurrent(1L);
//            pageResult.setSize(20L);
//
//            when(fileService.getRoomFiles(ROOM_ID, 1, 20)).thenReturn(pageResult);
//
//            R<PageResult<FileVO>> result = fileController.getRoomFiles(ROOM_ID, 1, 20);
//
//            assertNotNull(result);
//            assertNotNull(result.getData());
//            assertEquals(1L, result.getData().getTotal());
//        }
//
//        @Test
//        @DisplayName("分页查询：自定义分页参数")
//        void getRoomFiles_customPagination() {
//            PageResult<FileVO> pageResult = new PageResult<>();
//            pageResult.setRecords(List.of());
//            pageResult.setTotal(0L);
//            pageResult.setCurrent(3L);
//            pageResult.setSize(50L);
//
//            when(fileService.getRoomFiles(ROOM_ID, 3, 50)).thenReturn(pageResult);
//
//            R<PageResult<FileVO>> result = fileController.getRoomFiles(ROOM_ID, 3, 50);
//
//            assertNotNull(result);
//            verify(fileService, times(1)).getRoomFiles(ROOM_ID, 3, 50);
//        }
//
//        @Test
//        @DisplayName("获取单个文件信息成功")
//        void getFileInfo_success() {
//            FileVO mockVO = createMockFileVO("secret.pdf", "pdf", 2048L);
//            when(fileService.getFileInfo(FILE_ID)).thenReturn(mockVO);
//
//            R<FileVO> result = fileController.getFileInfo(FILE_ID);
//
//            assertNotNull(result);
//            assertEquals(FILE_ID, result.getData().getFileId());
//        }
//
//        @Test
//        @DisplayName("获取文件信息失败：文件不存在")
//        void getFileInfo_notFound() {
//            when(fileService.getFileInfo(FILE_ID))
//                    .thenThrow(new FileException(FileErrorCode.FILE_NOT_FOUND));
//
//            assertThrows(FileException.class, () -> fileController.getFileInfo(FILE_ID));
//        }
//    }
//
//    // ==================== 下载/预览测试 ====================
//
//    @Nested
//    @DisplayName("下载与预览测试")
//    class DownloadPreviewTests {
//
//        @Test
//        @DisplayName("生成下载链接成功：返回 Presigned URL")
//        void downloadFile_success() {
//            String downloadUrl = "http://localhost:9000/download?token=xxx";
//            when(fileService.generateDownloadUrl(FILE_ID)).thenReturn(downloadUrl);
//
//            R<String> result = fileController.downloadFile(FILE_ID);
//
//            assertNotNull(result);
//            assertEquals(downloadUrl, result.getData());
//        }
//
//        @Test
//        @DisplayName("生成下载链接失败：文件不存在")
//        void downloadFile_notFound() {
//            when(fileService.generateDownloadUrl(FILE_ID))
//                    .thenThrow(new FileException(FileErrorCode.FILE_NOT_FOUND));
//
//            assertThrows(FileException.class, () -> fileController.downloadFile(FILE_ID));
//        }
//
//        @Test
//        @DisplayName("生成预览链接成功：返回 Presigned URL")
//        void previewFile_success() {
//            String previewUrl = "http://localhost:9000/preview?token=yyy";
//            when(fileService.generatePreviewUrl(FILE_ID)).thenReturn(previewUrl);
//
//            var response = new org.springframework.mock.web.MockHttpServletResponse();
//            assertDoesNotThrow(() -> {
//                try {
//                    fileController.previewFile(FILE_ID, response);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            });
//
//            // previewFile 会重定向到 URL
//            verify(fileService, times(1)).generatePreviewUrl(FILE_ID);
//        }
//    }
//
//    // ==================== 删除测试 ====================
//
//    @Nested
//    @DisplayName("文件删除测试")
//    class DeleteTests {
//
//        @Test
//        @DisplayName("删除成功：返回 true")
//        void deleteFile_success() {
//            doNothing().when(fileService).deleteFile(FILE_ID, USER_ID);
//
//            R<Boolean> result = fileController.deleteFile(FILE_ID);
//
//            assertNotNull(result);
//            assertTrue(result.getData());
//            verify(fileService, times(1)).deleteFile(FILE_ID, USER_ID);
//        }
//
//        @Test
//        @DisplayName("删除失败：非上传者，权限拒绝")
//        void deleteFile_accessDenied() {
//            doThrow(new FileException(FileErrorCode.FILE_ACCESS_DENIED))
//                    .when(fileService).deleteFile(FILE_ID, USER_ID);
//
//            assertThrows(FileException.class, () -> fileController.deleteFile(FILE_ID));
//        }
//
//        @Test
//        @DisplayName("删除失败：文件不存在")
//        void deleteFile_notFound() {
//            doThrow(new FileException(FileErrorCode.FILE_NOT_FOUND))
//                    .when(fileService).deleteFile(FILE_ID, USER_ID);
//
//            assertThrows(FileException.class, () -> fileController.deleteFile(FILE_ID));
//        }
//    }
//
//    // ==================== 统计与清理测试 ====================
//
//    @Nested
//    @DisplayName("统计与清理测试")
//    class StatsCleanupTests {
//
//        @Test
//        @DisplayName("获取房间统计成功：返回 RoomFileStats 记录")
//        void getRoomFileStats_success() {
//            FileService.RoomFileStats stats = new FileService.RoomFileStats(5, 10485760L, "10 MB");
//
//            when(fileService.getRoomFileStats(ROOM_ID)).thenReturn(stats);
//
//            R<FileService.RoomFileStats> result = fileController.getRoomFileStats(ROOM_ID);
//
//            assertNotNull(result);
//            assertEquals(5, result.getData().fileCount());
//            assertEquals(10485760L, result.getData().totalSize());
//        }
//
//        @Test
//        @DisplayName("清理房间文件成功：返回清理数量")
//        void cleanupRoomFiles_success() {
//            when(fileService.cleanupRoomFiles(ROOM_ID)).thenReturn(10);
//
//            R<Integer> result = fileController.cleanupRoomFiles(ROOM_ID);
//
//            assertNotNull(result);
//            assertEquals(10, result.getData());
//        }
//
//        @Test
//        @DisplayName("清理空房间：返回 0")
//        void cleanupRoomFiles_empty() {
//            when(fileService.cleanupRoomFiles(ROOM_ID)).thenReturn(0);
//
//            R<Integer> result = fileController.cleanupRoomFiles(ROOM_ID);
//
//            assertEquals(0, result.getData());
//        }
//    }
//
//    // ==================== 辅助方法 ====================
//
//    /**
//     * 创建模拟 FileVO
//     */
//    private FileVO createMockFileVO(String fileName, String fileType, Long fileSize) {
//        FileVO vo = new FileVO();
//        vo.setFileId(FILE_ID);
//        vo.setRoomId(ROOM_ID);
//        vo.setUploaderId(USER_ID);
//        vo.setUploaderNickname(NICKNAME);
//        vo.setFileName(fileName);
//        vo.setFileSize(fileSize);
//        vo.setFileSizeFormatted(FileVO.formatFileSize(fileSize));
//        vo.setFileType(fileType);
//        vo.setContentType("application/octet-stream");
//        vo.setDownloadCount(0);
//        vo.setUploadTime(LocalDateTime.now());
//        vo.setIconType(FileVO.resolveIconType(fileType));
//        vo.setPreviewable(FileVO.isPreviewable(fileType));
//        vo.setDownloadUrl("http://localhost:9000/download");
//        vo.setPreviewUrl("http://localhost:9000/preview");
//        return vo;
//    }
//}
