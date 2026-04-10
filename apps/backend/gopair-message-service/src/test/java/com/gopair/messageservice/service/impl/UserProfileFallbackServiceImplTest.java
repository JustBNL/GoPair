package com.gopair.messageservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.messageservice.domain.dto.UserPublicProfileDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.mapper.UserPublicMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserProfileFallbackServiceImpl 单元测试
 *
 * * [核心策略]
 * - 外部依赖（UserPublicMapper / RestTemplate / ObjectMapper）通过 Mockito Mock 隔离。
 * - 重点验证降级链路：共享表 → HTTP 批量 → HTTP 单个 的三级降级策略是否按预期执行。
 *
 * * [覆盖范围]
 * - 空列表快速返回
 * - 所有资料均存在无需补全
 * - 三级降级链路：共享表补全 / HTTP 批量降级 / 单个兜底拉取
 * - replyToIds 补全回复者资料
 * - 全链路降级失败时保持原值（降级安全）
 *
 * @author gopair
 */
@ExtendWith(MockitoExtension.class)
class UserProfileFallbackServiceImplTest {

    @Mock
    private UserPublicMapper userPublicMapper;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserProfileFallbackServiceImpl userProfileFallbackService;

    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    // ==================== K1 - 空列表不处理 ====================

    @Nested
    @DisplayName("K1 - 空列表快速返回")
    class EmptyListTests {

        @Test
        @DisplayName("messageList 为 null，直接返回不抛异常")
        void fillMissingProfiles_NullList_Returns() {
            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(null, null));
            verifyNoInteractions(userPublicMapper);
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("messageList 为空列表，直接返回不抛异常")
        void fillMissingProfiles_EmptyList_Returns() {
            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(List.of(), null));
            verifyNoInteractions(userPublicMapper);
            verifyNoInteractions(restTemplate);
        }
    }

    // ==================== K2 - 所有资料均存在无需补全 ====================

    @Nested
    @DisplayName("K2 - 所有资料均存在无需补全")
    class NoMissingProfilesTests {

        @Test
        @DisplayName("发送者昵称和头像均已存在，跳过所有降级路径")
        void fillMissingProfiles_AllPresent_SkipsFallback() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname("用户A");
            msg.setSenderAvatar("http://avatar/a.jpg");

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            verifyNoInteractions(userPublicMapper);
            verifyNoInteractions(restTemplate);
            assertEquals("用户A", msg.getSenderNickname());
        }

        @Test
        @DisplayName("发送者昵称为空，头像存在，仍需补全")
        void fillMissingProfiles_NicknameMissing_TriggersFallback() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);
            msg.setSenderAvatar("http://avatar/a.jpg");

            when(userPublicMapper.selectByUserIds(anyList()))
                    .thenReturn(List.of(buildProfile(USER_A_ID, "昵称A", "http://avatar/a.jpg")));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            verify(userPublicMapper).selectByUserIds(anyList());
            assertEquals("昵称A", msg.getSenderNickname());
        }
    }

    // ==================== K3 - 从共享表补全成功 ====================

    @Nested
    @DisplayName("K3 - 从共享表补全成功")
    class SharedTableFallbackTests {

        @Test
        @DisplayName("共享表返回数据，昵称和头像被正确回填")
        void fillMissingProfiles_FromSharedTable_Success() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);
            msg.setSenderAvatar(null);

            when(userPublicMapper.selectByUserIds(anyList()))
                    .thenReturn(List.of(buildProfile(USER_A_ID, "昵称A", "http://avatar/a.jpg")));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            assertEquals("昵称A", msg.getSenderNickname());
            assertEquals("http://avatar/a.jpg", msg.getSenderAvatar());
        }

        @Test
        @DisplayName("共享表部分命中，昵称已被共享表填充")
        void fillMissingProfiles_SharedTablePartial_HttpFallback() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);
            msg.setSenderAvatar(null);

            UserPublicProfileDto sharedProfile = new UserPublicProfileDto();
            sharedProfile.setUserId(USER_A_ID);
            sharedProfile.setNickname("昵称A-共享表");
            sharedProfile.setAvatar(null);
            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of(sharedProfile));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            assertEquals("昵称A-共享表", msg.getSenderNickname());
            assertNull(msg.getSenderAvatar());
        }

        @Test
        @DisplayName("共享表抛出异常，降级至 HTTP 批量补全")
        void fillMissingProfiles_SharedTableFails_HttpFallback() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenThrow(new RuntimeException("DB connection failed"));

            String httpResponse = "{\"data\":[{\"userId\":1,\"nickname\":\"昵称A-HTTP\",\"avatar\":\"http://avatar/a.jpg\"}]}";
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(httpResponse);
            when(objectMapper.readTree(httpResponse)).thenReturn(new ObjectMapper().readTree(httpResponse));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(String.class));
        }
    }

    // ==================== K4 - HTTP 降级补全成功 ====================

    @Nested
    @DisplayName("K4 - HTTP 批量降级补全成功")
    class HttpBatchFallbackTests {

        @Test
        @DisplayName("共享表无数据，HTTP 批量接口返回数据，昵称被回填")
        void fillMissingProfiles_HttpBatch_Success() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);
            msg.setSenderAvatar(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());

            String httpResponse = "{\"data\":[{\"userId\":1,\"nickname\":\"昵称A-HTTP\",\"avatar\":\"http://avatar/a.jpg\"}]}";
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(httpResponse);

            JsonNode root = new ObjectMapper().readTree(httpResponse);
            when(objectMapper.readTree(httpResponse)).thenReturn(root);

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            assertEquals("昵称A-HTTP", msg.getSenderNickname());
            assertEquals("http://avatar/a.jpg", msg.getSenderAvatar());
        }

        @Test
        @DisplayName("HTTP 批量接口返回 null，不抛异常，原值不变")
        void fillMissingProfiles_HttpBatchReturnsNull_NoException() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(null);

            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(List.of(msg), null));
        }

        @Test
        @DisplayName("HTTP 批量接口返回空数组，不抛异常，原值不变")
        void fillMissingProfiles_HttpBatchReturnsEmpty_NoException() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());

            String emptyResponse = "{\"data\":[]}";
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(emptyResponse);
            when(objectMapper.readTree(emptyResponse)).thenReturn(new ObjectMapper().readTree(emptyResponse));

            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(List.of(msg), null));
        }
    }

    // ==================== K5 - 单个兜底拉取 ====================

    @Nested
    @DisplayName("K5 - 单个兜底拉取")
    class SingleFallbackTests {

        @Test
        @DisplayName("前两级降级均无数据，触发单个兜底拉取（budget 内）")
        void fillMissingProfiles_SingleFallback_WithinBudget() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());

            String emptyBatch = "{\"data\":[]}";
            when(restTemplate.getForObject(contains("by-ids"), eq(String.class))).thenReturn(emptyBatch);
            when(objectMapper.readTree(emptyBatch)).thenReturn(new ObjectMapper().readTree(emptyBatch));

            String singleResponse = "{\"data\":{\"userId\":1,\"nickname\":\"昵称A-单个\",\"avatar\":\"http://avatar/a.jpg\"}}";
            when(restTemplate.getForObject(contains("/1"), eq(String.class))).thenReturn(singleResponse);
            when(objectMapper.readTree(singleResponse)).thenReturn(new ObjectMapper().readTree(singleResponse));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), null);

            verify(restTemplate, atLeastOnce()).getForObject(contains("/1"), eq(String.class));
        }
    }

    // ==================== K6 - replyToIds 补全回复者资料 ====================

    @Nested
    @DisplayName("K6 - replyToIds 补全回复者资料")
    class ReplyToIdsTests {

        @Test
        @DisplayName("replyToIds 非空时，被回复消息发送者资料被补全")
        void fillMissingProfiles_ReplyToIds_ProfileFilled() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setMessageId(10L);
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname("用户A");
            msg.setReplyToId(50L);
            msg.setReplyToSenderId(USER_B_ID);
            msg.setReplyToSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());

            String batchResponse = "{\"data\":[]}";
            when(restTemplate.getForObject(contains("by-ids"), eq(String.class))).thenReturn(batchResponse);
            when(objectMapper.readTree(batchResponse)).thenReturn(new ObjectMapper().readTree(batchResponse));

            String singleResponse = "{\"data\":{\"userId\":2,\"nickname\":\"用户B\",\"avatar\":\"http://avatar/b.jpg\"}}";
            when(restTemplate.getForObject(contains("/2"), eq(String.class))).thenReturn(singleResponse);
            when(objectMapper.readTree(singleResponse)).thenReturn(new ObjectMapper().readTree(singleResponse));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), List.of(50L));

            verify(userPublicMapper).selectByUserIds(argThat(list ->
                    list.contains(USER_B_ID)));
        }

        @Test
        @DisplayName("replyToIds 为空列表，不影响正常补全")
        void fillMissingProfiles_EmptyReplyToIds_StillWorks() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList()))
                    .thenReturn(List.of(buildProfile(USER_A_ID, "昵称A", null)));

            userProfileFallbackService.fillMissingProfiles(List.of(msg), List.of());

            assertEquals("昵称A", msg.getSenderNickname());
        }
    }

    // ==================== K7 - 全链路降级失败保持原值 ====================

    @Nested
    @DisplayName("K7 - 全链路降级失败保持原值（降级安全）")
    class AllFallbackFailedTests {

        @Test
        @DisplayName("所有降级路径均失败，不抛异常，原 VO 字段保持不变")
        void fillMissingProfiles_AllFallbackFails_NoException() throws Exception {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);
            msg.setSenderAvatar(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());

            String emptyResponse = "{\"data\":[]}";
            when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(emptyResponse);
            when(objectMapper.readTree(emptyResponse)).thenReturn(new ObjectMapper().readTree(emptyResponse));

            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(List.of(msg), null));
            assertNull(msg.getSenderNickname());
            assertNull(msg.getSenderAvatar());
        }

        @Test
        @DisplayName("RestTemplate 抛出异常，不向外传播，原 VO 字段不变")
        void fillMissingProfiles_RestTemplateThrows_NoExceptionBubbles() {
            MessageVO msg = new MessageVO();
            msg.setSenderId(USER_A_ID);
            msg.setSenderNickname(null);

            when(userPublicMapper.selectByUserIds(anyList())).thenReturn(List.of());
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenThrow(new RuntimeException("network error"));

            assertDoesNotThrow(() -> userProfileFallbackService.fillMissingProfiles(List.of(msg), null));
            assertNull(msg.getSenderNickname());
        }
    }

    // ==================== 辅助方法 ====================

    private UserPublicProfileDto buildProfile(Long userId, String nickname, String avatar) {
        UserPublicProfileDto dto = new UserPublicProfileDto();
        dto.setUserId(userId);
        dto.setNickname(nickname);
        dto.setAvatar(avatar);
        return dto;
    }
}
