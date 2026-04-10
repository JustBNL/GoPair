package com.gopair.messageservice.controller;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.framework.context.UserContext;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.exception.GlobalExceptionHandler;
import com.gopair.messageservice.domain.dto.MessageQueryDto;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.enums.MessageErrorCode;
import com.gopair.messageservice.exception.MessageException;
import com.gopair.messageservice.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MessageController 单元测试
 *
 * * [核心策略]
 * - 使用 @WebMvcTest 切片测试，仅加载 Controller 和全局异常处理器，不启动完整 Spring 上下文。
 * - MessageService 通过 @MockBean Mock 隔离，UserContextHolder 在每个测试前手动设置上下文。
 *
 * * [覆盖范围]
 * - POST /message/send：正常发送 / 参数校验失败
 * - GET /message/room/{roomId}：分页查询成功
 * - GET /message/room/{roomId}/latest：获取最新消息
 * - GET /message/{messageId}：消息详情（存在 / 不存在）
 * - DELETE /message/{messageId}：删除成功
 * - GET /message/room/{roomId}/count：统计消息数量
 * - GET /message/health：健康检查
 *
 * @author gopair
 */
@WebMvcTest(MessageController.class)
@Import(GlobalExceptionHandler.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    private static final Long USER_ID = 100L;
    private static final Long ROOM_ID = 1L;
    private static final Long MESSAGE_ID = 1L;

    @BeforeEach
    void setUp() {
        UserContext context = new UserContext();
        context.setUserId(USER_ID);
        context.setNickname("测试用户");
        UserContextHolder.setContext(context);
    }

    private MessageVO buildMessageVO(String content) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(MESSAGE_ID);
        vo.setRoomId(ROOM_ID);
        vo.setSenderId(USER_ID);
        vo.setSenderNickname("测试用户");
        vo.setMessageType(1);
        vo.setContent(content);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    // ==================== POST /message/send ====================

    @Nested
    @DisplayName("发送消息 POST /message/send")
    class SendMessageTests {

        @Test
        @DisplayName("J1 - 发送文本消息成功，返回 R.ok(messageVO)")
        void sendMessage_Success() throws Exception {
            SendMessageDto dto = new SendMessageDto();
            dto.setRoomId(ROOM_ID);
            dto.setMessageType(1);
            dto.setContent("Hello");

            MessageVO vo = buildMessageVO("Hello");
            when(messageService.sendMessage(any(SendMessageDto.class), eq(USER_ID))).thenReturn(vo);

            mockMvc.perform(post("/message/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.data.content").value("Hello"));

            verify(messageService).sendMessage(any(SendMessageDto.class), eq(USER_ID));
        }

        @Test
        @DisplayName("J2 - 缺少必填字段（roomId 为 null），GlobalExceptionHandler 返回 HTTP 400 + code 10002（PARAM_ERROR）")
        void sendMessage_MissingRoomId_Returns400() throws Exception {
            SendMessageDto dto = new SendMessageDto();
            dto.setRoomId(null);
            dto.setMessageType(1);
            dto.setContent("Hello");

            mockMvc.perform(post("/message/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("发送消息 - 用户不在房间，返回业务错误码 20403")
        void sendMessage_UserNotInRoom_ReturnsBusinessError() throws Exception {
            SendMessageDto dto = new SendMessageDto();
            dto.setRoomId(ROOM_ID);
            dto.setMessageType(1);
            dto.setContent("Hello");

            when(messageService.sendMessage(any(), eq(USER_ID)))
                    .thenThrow(new MessageException(MessageErrorCode.USER_NOT_IN_ROOM));

            mockMvc.perform(post("/message/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20403))
                    .andExpect(jsonPath("$.msg").value("用户不在该房间内，无法发送消息"));
        }
    }

    // ==================== GET /message/room/{roomId} ====================

    @Nested
    @DisplayName("分页查询房间消息 GET /message/room/{roomId}")
    class GetRoomMessagesTests {

        @Test
        @DisplayName("J3 - 分页查询成功，返回 PageResult<MessageVO>")
        void getRoomMessages_Success() throws Exception {
            List<MessageVO> records = List.of(buildMessageVO("msg1"), buildMessageVO("msg2"));
            PageResult<MessageVO> pageResult = new PageResult<>(records, 2L, 1L, 10L);

            when(messageService.getRoomMessages(any(MessageQueryDto.class))).thenReturn(pageResult);

            mockMvc.perform(get("/message/room/{roomId}", ROOM_ID)
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records.length()").value(2))
                    .andExpect(jsonPath("$.data.total").value(2));
        }
    }

    // ==================== GET /message/room/{roomId}/latest ====================

    @Nested
    @DisplayName("获取最新消息 GET /message/room/{roomId}/latest")
    class GetLatestMessagesTests {

        @Test
        @DisplayName("J4 - 获取最新消息成功，返回 List<MessageVO>")
        void getLatestMessages_Success() throws Exception {
            List<MessageVO> messages = List.of(buildMessageVO("msg1"), buildMessageVO("msg2"));
            when(messageService.getLatestMessages(ROOM_ID, 10)).thenReturn(messages);

            mockMvc.perform(get("/message/room/{roomId}/latest", ROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("获取最新消息 - 使用自定义 limit 参数")
        void getLatestMessages_CustomLimit() throws Exception {
            when(messageService.getLatestMessages(ROOM_ID, 5)).thenReturn(List.of());

            mockMvc.perform(get("/message/room/{roomId}/latest", ROOM_ID)
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(messageService).getLatestMessages(ROOM_ID, 5);
        }
    }

    // ==================== GET /message/{messageId} ====================

    @Nested
    @DisplayName("获取消息详情 GET /message/{messageId}")
    class GetMessageByIdTests {

        @Test
        @DisplayName("J5 - 消息存在，返回详情")
        void getMessageById_Success() throws Exception {
            MessageVO vo = buildMessageVO("消息内容");
            when(messageService.getMessageById(MESSAGE_ID)).thenReturn(vo);

            mockMvc.perform(get("/message/{messageId}", MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.data.content").value("消息内容"));
        }

        @Test
        @DisplayName("J6 - 消息不存在，返回业务错误码 20400")
        void getMessageById_NotFound_ReturnsError() throws Exception {
            when(messageService.getMessageById(MESSAGE_ID))
                    .thenThrow(new MessageException(MessageErrorCode.MESSAGE_NOT_FOUND));

            mockMvc.perform(get("/message/{messageId}", MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20400))
                    .andExpect(jsonPath("$.msg").value("消息不存在"));
        }
    }

    // ==================== DELETE /message/{messageId} ====================

    @Nested
    @DisplayName("删除消息 DELETE /message/{messageId}")
    class DeleteMessageTests {

        @Test
        @DisplayName("J7 - 删除消息成功，返回 R.ok(true)")
        void deleteMessage_Success() throws Exception {
            when(messageService.deleteMessage(MESSAGE_ID, USER_ID)).thenReturn(true);

            mockMvc.perform(delete("/message/{messageId}", MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("删除消息 - 无权限，返回 20404")
        void deleteMessage_NoPermission_ReturnsError() throws Exception {
            when(messageService.deleteMessage(MESSAGE_ID, USER_ID))
                    .thenThrow(new MessageException(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE));

            mockMvc.perform(delete("/message/{messageId}", MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20404));
        }
    }

    // ==================== GET /message/room/{roomId}/count ====================

    @Nested
    @DisplayName("统计消息数量 GET /message/room/{roomId}/count")
    class CountRoomMessagesTests {

        @Test
        @DisplayName("J8 - 统计消息数量成功，返回 Long")
        void countRoomMessages_Success() throws Exception {
            when(messageService.countRoomMessages(ROOM_ID, 1)).thenReturn(10L);

            mockMvc.perform(get("/message/room/{roomId}/count", ROOM_ID)
                            .param("messageType", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(10));
        }

        @Test
        @DisplayName("统计消息数量 - 不指定消息类型")
        void countRoomMessages_WithoutType() throws Exception {
            when(messageService.countRoomMessages(ROOM_ID, null)).thenReturn(25L);

            mockMvc.perform(get("/message/room/{roomId}/count", ROOM_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(25));
        }
    }

    // ==================== GET /message/health ====================

    @Nested
    @DisplayName("健康检查 GET /message/health")
    class HealthCheckTests {

        @Test
        @DisplayName("J9 - 健康检查返回 '消息服务运行正常'")
        void health_ReturnsOk() throws Exception {
            mockMvc.perform(get("/message/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("消息服务运行正常"));
        }
    }
}
