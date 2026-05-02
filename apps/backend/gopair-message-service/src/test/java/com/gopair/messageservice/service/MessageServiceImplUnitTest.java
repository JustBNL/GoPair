// package com.gopair.messageservice.service;

// import com.gopair.common.core.R;
// import com.gopair.common.service.WebSocketMessageProducer;
// import com.gopair.messageservice.config.MessageProperties;
// import com.gopair.messageservice.domain.dto.MessageQueryDto;
// import com.gopair.messageservice.domain.dto.SendMessageDto;
// import com.gopair.messageservice.domain.po.Message;
// import com.gopair.messageservice.domain.vo.MessageVO;
// import com.gopair.messageservice.enums.MessageErrorCode;
// import com.gopair.messageservice.enums.MessageType;
// import com.gopair.messageservice.exception.MessageException;
// import com.gopair.messageservice.mapper.MessageMapper;
// import com.gopair.messageservice.service.impl.MessageServiceImpl;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Nested;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.junit.jupiter.api.extension.Extensions;
// import org.mockito.ArgumentCaptor;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.context.ApplicationEventPublisher;

// import java.time.LocalDateTime;
// import java.util.Collections;
// import java.util.List;
// import java.util.Map;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;

// /**
//  * MessageServiceImpl 核心业务逻辑单元测试
//  *
//  * 使用 MockitoExtension 隔离外部依赖，不启动 Spring 容器。
//  * WebSocket 推送行为被 Mock 验证调用，不建立真实连接。
//  */
// @ExtendWith(MockitoExtension.class)
// class MessageServiceImplUnitTest {

//     @Mock
//     private MessageMapper messageMapper;

//     @Mock
//     private ApplicationEventPublisher eventPublisher;

//     @Mock
//     private WebSocketMessageProducer webSocketMessageProducer;

//     @Mock
//     private MessageProperties messageProperties;

//     @Mock
//     private UserProfileFallbackService userProfileFallbackService;

//     @Mock
//     private org.springframework.web.client.RestTemplate restTemplate;

//     private MessageServiceImpl service;

//     private static final Long ROOM_ID = 1L;
//     private static final Long SENDER_ID = 100L;
//     private static final Long OTHER_USER_ID = 200L;
//     private static final Long MESSAGE_ID = 10L;

//     @BeforeEach
//     void setUp() {
//         service = new MessageServiceImpl(
//                 messageMapper,
//                 eventPublisher,
//                 webSocketMessageProducer,
//                 messageProperties,
//                 userProfileFallbackService,
//                 restTemplate
//         );
//     }

//     // ========== sendMessage 核心逻辑测试 ==========

//     @Nested
//     @DisplayName("sendMessage 发送消息")
//     class SendMessage {

//         @Test
//         @DisplayName("成功发送文本消息：校验数据库写入 + MQ推送 + Event发布")
//         void sendTextMessage_Success() {
//             // Arrange：room-service 确认用户在房间内
//             when(restTemplate.getForObject(contains("/room/1/members/100/check"), eq(R.class)))
//                     .thenReturn(R.ok(true));
//             when(messageProperties.getMaxContentLength()).thenReturn(2000);

//             MessageVO savedVO = buildMessageVO(MESSAGE_ID, ROOM_ID, SENDER_ID, 1, "Hello", null, null);

//             // Mock insert：自动填充 messageId（因为 MyBatis-Plus 的 AUTO_INCREMENT 在 Mock 中无法工作）
//             doAnswer(invocation -> {
//                 Message m = invocation.getArgument(0);
//                 org.springframework.test.util.ReflectionTestUtils.setField(m, "messageId", MESSAGE_ID);
//                 return 1;
//             }).when(messageMapper).insert(any(Message.class));
//             when(messageMapper.selectMessageVOById(MESSAGE_ID)).thenReturn(savedVO);

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1); // TEXT
//             dto.setContent("Hello");

//             // Act
//             MessageVO result = service.sendMessage(dto, SENDER_ID);

//             // Assert
//             assertThat(result).isNotNull();
//             assertThat(result.getMessageId()).isEqualTo(MESSAGE_ID);
//             assertThat(result.getContent()).isEqualTo("Hello");

//             // 验证 messageMapper.insert 被调用
//             ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
//             verify(messageMapper).insert(msgCaptor.capture());
//             Message inserted = msgCaptor.getValue();
//             assertThat(inserted.getRoomId()).isEqualTo(ROOM_ID);
//             assertThat(inserted.getSenderId()).isEqualTo(SENDER_ID);
//             assertThat(inserted.getMessageType()).isEqualTo(1);

//             // 验证 MQ 推送
//             verify(webSocketMessageProducer).sendChatMessageToRoom(eq(ROOM_ID), any(Map.class));

//             // 验证 Spring Event 发布
//             verify(eventPublisher).publishEvent(any());
//         }

//         @Test
//         @DisplayName("用户不在房间内：抛出 USER_NOT_IN_ROOM 异常")
//         void sendMessage_UserNotInRoom_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class)))
//                     .thenReturn(R.ok(false));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1);
//             dto.setContent("Hello");

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20403); // USER_NOT_IN_ROOM
//                     });

//             verify(messageMapper, never()).insert(any(Message.class));
//         }

//         @Test
//         @DisplayName("room-service 不可用时：降级返回 false，抛出 USER_NOT_IN_ROOM")
//         void sendMessage_RoomServiceUnavailable_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class)))
//                     .thenThrow((Throwable) new org.springframework.web.client.ResourceAccessException("connection refused"));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1);
//             dto.setContent("Hello");

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20403);
//                     });
//         }

//         @Test
//         @DisplayName("文本消息内容为空：抛出 MESSAGE_CONTENT_EMPTY")
//         void sendTextMessage_EmptyContent_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1); // TEXT
//             dto.setContent("  "); // 空白内容

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20402); // MESSAGE_CONTENT_EMPTY
//                     });
//         }

//         @Test
//         @DisplayName("文本消息内容超长：抛出 MESSAGE_CONTENT_TOO_LONG")
//         void sendTextMessage_ContentTooLong_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));
//             when(messageProperties.getMaxContentLength()).thenReturn(10); // 限制 10 字符

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1);
//             dto.setContent("这是一段超过10个字符的文本内容"); // 超过10字符

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20408); // MESSAGE_CONTENT_TOO_LONG
//                     });
//         }

//         @Test
//         @DisplayName("文件消息缺少 fileUrl：抛出 FILE_URL_EMPTY")
//         void sendFileMessage_MissingFileUrl_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(2); // IMAGE
//             dto.setFileName("image.jpg");
//             // fileUrl 为空

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20405); // FILE_URL_EMPTY
//                     });
//         }

//         @Test
//         @DisplayName("文件消息缺少 fileName：抛出 FILE_NAME_EMPTY")
//         void sendFileMessage_MissingFileName_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(3); // FILE
//             dto.setFileUrl("http://example.com/file.pdf");
//             // fileName 为空

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20406); // FILE_NAME_EMPTY
//                     });
//         }

//         @Test
//         @DisplayName("Emoji 消息内容超长：抛出 MESSAGE_TYPE_INVALID")
//         void sendEmojiMessage_ContentTooLong_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));
//             when(messageProperties.getEmojiMaxLength()).thenReturn(2);

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(5); // EMOJI
//             dto.setContent("123456789"); // 超过2字符

//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20401); // MESSAGE_TYPE_INVALID
//                     });
//         }

//         @Test
//         @DisplayName("无效消息类型：抛出 IllegalArgumentException（fromValue 未被 try-catch 包裹）")
//         void sendMessage_InvalidMessageType_Throws() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(99); // 无效类型

//             // 注意：当前实现中 MessageType.fromValue(99) 直接抛出 IllegalArgumentException，
//             // 并未被 try-catch 包裹，属于设计缺陷。期望业务上应转换为 MessageException(20401)
//             assertThatThrownBy(() -> service.sendMessage(dto, SENDER_ID))
//                     .isInstanceOf(IllegalArgumentException.class)
//                     .hasMessageContaining("未知的消息类型");
//         }

//         @Test
//         @DisplayName("发送带 replyToId 的回复消息：入库 replyToId 字段正确")
//         void sendReplyMessage_Success() {
//             Long replyToId = 5L;
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(R.ok(true));
//             when(messageProperties.getMaxContentLength()).thenReturn(2000);

//             MessageVO savedVO = buildMessageVO(MESSAGE_ID, ROOM_ID, SENDER_ID, 1, "回复内容", null, null);
//             savedVO.setReplyToId(replyToId);

//             // Mock insert：自动填充 messageId
//             doAnswer(invocation -> {
//                 Message m = invocation.getArgument(0);
//                 org.springframework.test.util.ReflectionTestUtils.setField(m, "messageId", MESSAGE_ID);
//                 return 1;
//             }).when(messageMapper).insert(any(Message.class));
//             when(messageMapper.selectMessageVOById(MESSAGE_ID)).thenReturn(savedVO);

//             SendMessageDto dto = new SendMessageDto();
//             dto.setRoomId(ROOM_ID);
//             dto.setMessageType(1);
//             dto.setContent("回复内容");
//             dto.setReplyToId(replyToId);

//             service.sendMessage(dto, SENDER_ID);

//             ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
//             verify(messageMapper).insert(msgCaptor.capture());
//             assertThat(msgCaptor.getValue().getReplyToId()).isEqualTo(replyToId);
//         }
//     }

//     // ========== deleteMessage 删除消息测试 ==========

//     @Nested
//     @DisplayName("deleteMessage 删除消息")
//     class DeleteMessage {

//         @Test
//         @DisplayName("发送者删除自己的消息：成功删除")
//         void deleteOwnMessage_Success() {
//             Message message = buildMessage(MESSAGE_ID, ROOM_ID, SENDER_ID, 1);
//             when(messageMapper.selectById(MESSAGE_ID)).thenReturn(message);
//             when(messageMapper.deleteById(MESSAGE_ID)).thenReturn(1);

//             Boolean result = service.deleteMessage(MESSAGE_ID, SENDER_ID);

//             assertThat(result).isTrue();
//             verify(messageMapper).deleteById(MESSAGE_ID);
//         }

//         @Test
//         @DisplayName("非发送者删除他人消息：抛出 NO_PERMISSION_DELETE_MESSAGE")
//         void deleteOthersMessage_Throws() {
//             Message message = buildMessage(MESSAGE_ID, ROOM_ID, SENDER_ID, 1);
//             when(messageMapper.selectById(MESSAGE_ID)).thenReturn(message);

//             assertThatThrownBy(() -> service.deleteMessage(MESSAGE_ID, OTHER_USER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20404); // NO_PERMISSION_DELETE_MESSAGE
//                     });

//             verify(messageMapper, never()).deleteById(any());
//         }

//         @Test
//         @DisplayName("删除不存在的消息：因权限校验先执行，实际抛出 NO_PERMISSION_DELETE_MESSAGE")
//         void deleteNonExistentMessage_Throws() {
//             // deleteMessage 先调 checkMessagePermission → selectById 返回 null → checkMessagePermission 返回 false → 抛 NO_PERMISSION
//             when(messageMapper.selectById(999L)).thenReturn(null);

//             assertThatThrownBy(() -> service.deleteMessage(999L, SENDER_ID))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20404); // NO_PERMISSION_DELETE_MESSAGE
//                     });
//         }
//     }

//     // ========== checkMessagePermission 权限检查测试 ==========

//     @Nested
//     @DisplayName("checkMessagePermission 权限校验")
//     class CheckPermission {

//         @Test
//         @DisplayName("发送者是消息所有者：返回 true")
//         void senderIsOwner_ReturnsTrue() {
//             Message message = buildMessage(MESSAGE_ID, ROOM_ID, SENDER_ID, 1);
//             when(messageMapper.selectById(MESSAGE_ID)).thenReturn(message);

//             assertThat(service.checkMessagePermission(MESSAGE_ID, SENDER_ID)).isTrue();
//         }

//         @Test
//         @DisplayName("非发送者：返回 false")
//         void nonSender_ReturnsFalse() {
//             Message message = buildMessage(MESSAGE_ID, ROOM_ID, SENDER_ID, 1);
//             when(messageMapper.selectById(MESSAGE_ID)).thenReturn(message);

//             assertThat(service.checkMessagePermission(MESSAGE_ID, OTHER_USER_ID)).isFalse();
//         }

//         @Test
//         @DisplayName("消息不存在：返回 false")
//         void messageNotFound_ReturnsFalse() {
//             when(messageMapper.selectById(MESSAGE_ID)).thenReturn(null);

//             assertThat(service.checkMessagePermission(MESSAGE_ID, SENDER_ID)).isFalse();
//         }
//     }

//     // ========== checkUserInRoom 房间成员检查测试 ==========

//     @Nested
//     @DisplayName("checkUserInRoom 房间成员校验")
//     class CheckUserInRoom {

//         @Test
//         @DisplayName("room-service 返回 true：方法返回 true")
//         void roomServiceReturnsTrue_ReturnsTrue() {
//             when(restTemplate.getForObject(contains("/room/1/members/100/check"), eq(R.class)))
//                     .thenReturn(R.ok(true));

//             assertThat(service.checkUserInRoom(ROOM_ID, SENDER_ID)).isTrue();
//         }

//         @Test
//         @DisplayName("room-service 返回 false：方法返回 false")
//         void roomServiceReturnsFalse_ReturnsFalse() {
//             when(restTemplate.getForObject(anyString(), eq(R.class)))
//                     .thenReturn(R.ok(false));

//             assertThat(service.checkUserInRoom(ROOM_ID, SENDER_ID)).isFalse();
//         }

//         @Test
//         @DisplayName("room-service 异常：方法降级返回 false（不抛异常）")
//         void roomServiceThrows_ReturnsFalseGracefully() {
//             when(restTemplate.getForObject(anyString(), eq(R.class)))
//                     .thenThrow((Throwable) new org.springframework.web.client.ResourceAccessException("timeout"));

//             assertThat(service.checkUserInRoom(ROOM_ID, SENDER_ID)).isFalse();
//         }

//         @Test
//         @DisplayName("room-service 返回 null：方法返回 false")
//         void roomServiceReturnsNull_ReturnsFalse() {
//             when(restTemplate.getForObject(anyString(), eq(R.class))).thenReturn(null);

//             assertThat(service.checkUserInRoom(ROOM_ID, SENDER_ID)).isFalse();
//         }
//     }

//     // ========== 查询类只读方法测试 ==========

//     @Nested
//     @DisplayName("查询类只读方法")
//     class QueryMethods {

//         @Test
//         @DisplayName("getRoomMessages：调用 Mapper 分页并返回 PageResult")
//         void getRoomMessages_CallsMapperAndReturnsPageResult() {
//             MessageQueryDto queryDto = new MessageQueryDto();
//             queryDto.setRoomId(ROOM_ID);
//             queryDto.setPageNum(1);
//             queryDto.setPageSize(10);

//             MessageVO vo = buildMessageVO(MESSAGE_ID, ROOM_ID, SENDER_ID, 1, "Hi", null, null);
//             com.baomidou.mybatisplus.core.metadata.IPage<MessageVO> mockPage =
//                     new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
//             mockPage.setRecords(List.of(vo));
//             mockPage.setTotal(1);
//             mockPage.setCurrent(1);
//             mockPage.setSize(10);

//             when(messageMapper.selectMessageVOPage(any(), eq(ROOM_ID), isNull(), isNull(), isNull()))
//                     .thenReturn(mockPage);

//             var result = service.getRoomMessages(queryDto);

//             assertThat(result.getRecords()).hasSize(1);
//             assertThat(result.getTotal()).isEqualTo(1);
//             verify(userProfileFallbackService).fillMissingProfiles(anyList(), anyList());
//         }

//         @Test
//         @DisplayName("getLatestMessages：调用 Mapper 并按 createTime ASC 返回")
//         void getLatestMessages_CallsMapperOrderedAsc() {
//             MessageVO vo = buildMessageVO(MESSAGE_ID, ROOM_ID, SENDER_ID, 1, "Hi", null, null);
//             when(messageMapper.selectLatestMessages(ROOM_ID, 20))
//                     .thenReturn(List.of(vo));

//             List<MessageVO> result = service.getLatestMessages(ROOM_ID, 20);

//             assertThat(result).hasSize(1);
//             verify(messageMapper).selectLatestMessages(ROOM_ID, 20);
//         }

//         @Test
//         @DisplayName("getLatestMessages：roomId 无消息时返回空列表")
//         void getLatestMessages_NoMessages_ReturnsEmpty() {
//             when(messageMapper.selectLatestMessages(ROOM_ID, 10))
//                     .thenReturn(Collections.emptyList());

//             List<MessageVO> result = service.getLatestMessages(ROOM_ID, 10);

//             assertThat(result).isEmpty();
//         }

//         @Test
//         @DisplayName("getMessageById：消息存在时返回 MessageVO")
//         void getMessageById_Exists_ReturnsVO() {
//             MessageVO vo = buildMessageVO(MESSAGE_ID, ROOM_ID, SENDER_ID, 1, "Hello", null, null);
//             when(messageMapper.selectMessageVOById(MESSAGE_ID)).thenReturn(vo);

//             MessageVO result = service.getMessageById(MESSAGE_ID);

//             assertThat(result.getMessageId()).isEqualTo(MESSAGE_ID);
//         }

//         @Test
//         @DisplayName("getMessageById：消息不存在时抛出 MESSAGE_NOT_FOUND")
//         void getMessageById_NotFound_Throws() {
//             when(messageMapper.selectMessageVOById(999L)).thenReturn(null);

//             assertThatThrownBy(() -> service.getMessageById(999L))
//                     .isInstanceOf(MessageException.class)
//                     .satisfies(e -> {
//                         MessageException me = (MessageException) e;
//                         assertThat(me.getErrorCode().getCode()).isEqualTo(20400);
//                     });
//         }

//         @Test
//         @DisplayName("countRoomMessages：返回正确数量")
//         void countRoomMessages_ReturnsCount() {
//             when(messageMapper.countRoomMessages(ROOM_ID, null)).thenReturn(5L);

//             Long count = service.countRoomMessages(ROOM_ID, null);

//             assertThat(count).isEqualTo(5L);
//         }

//         @Test
//         @DisplayName("countRoomMessages：按类型过滤统计")
//         void countRoomMessages_FilteredByType() {
//             when(messageMapper.countRoomMessages(ROOM_ID, 1)).thenReturn(3L);

//             Long count = service.countRoomMessages(ROOM_ID, 1);

//             assertThat(count).isEqualTo(3L);
//         }
//     }

//     // ========== 辅助方法 ==========

//     private Message buildMessage(Long messageId, Long roomId, Long senderId, Integer messageType) {
//         Message m = new Message();
//         m.setMessageId(messageId);
//         m.setRoomId(roomId);
//         m.setSenderId(senderId);
//         m.setMessageType(messageType);
//         m.setContent("test");
//         m.setCreateTime(LocalDateTime.now());
//         m.setUpdateTime(LocalDateTime.now());
//         return m;
//     }

//     private MessageVO buildMessageVO(Long messageId, Long roomId, Long senderId,
//                                      Integer messageType, String content,
//                                      String senderNickname, String senderAvatar) {
//         MessageVO vo = new MessageVO();
//         vo.setMessageId(messageId);
//         vo.setRoomId(roomId);
//         vo.setSenderId(senderId);
//         vo.setMessageType(messageType);
//         vo.setContent(content);
//         vo.setSenderNickname(senderNickname);
//         vo.setSenderAvatar(senderAvatar);
//         vo.setCreateTime(LocalDateTime.now());
//         vo.setUpdateTime(LocalDateTime.now());
//         return vo;
//     }
// }
