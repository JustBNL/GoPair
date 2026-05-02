//package com.gopair.chatservice.controller;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.gopair.chatservice.base.BaseIntegrationTest;
//import com.gopair.chatservice.domain.dto.FriendRequestDto;
//import com.gopair.chatservice.domain.dto.FriendStatusVO;
//import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
//import com.gopair.chatservice.domain.vo.FriendRequestVO;
//import com.gopair.chatservice.domain.vo.FriendVO;
//import com.gopair.chatservice.domain.vo.ConversationVO;
//import com.gopair.chatservice.domain.vo.PrivateMessageVO;
//import com.gopair.common.core.R;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.web.client.TestRestTemplate;
//import org.springframework.http.*;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//
//import java.util.concurrent.atomic.AtomicLong;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * 聊天服务接口契约测试。
// */
//@DisplayName("Chat API Contract Tests")
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//class ChatControllerApiContractTest extends BaseIntegrationTest {
//
//    @Autowired
//    private TestRestTemplate restTemplate;
//
//    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1_000_000);
//
//    private String uid() {
//        return String.valueOf(counter.incrementAndGet());
//    }
//
//    // ==================== Auth 辅助 ====================
//
//    private HttpHeaders userHeaders(Long userId, String nickname) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("X-User-Id", String.valueOf(userId));
//        headers.set("X-Nickname", nickname);
//        return headers;
//    }
//
//    private ResponseEntity<R<Void>> post(String path, Object body, HttpHeaders headers) {
//        return restTemplate.exchange(
//            getUrl(path),
//            HttpMethod.POST,
//            new HttpEntity<>(body, headers),
//            new TypeReference<R<Void>>() {}
//        );
//    }
//
//    private <T> ResponseEntity<R<T>> postForEntity(String path, Object body, HttpHeaders headers, Class<T> clazz) {
//        return restTemplate.exchange(
//            getUrl(path),
//            HttpMethod.POST,
//            new HttpEntity<>(body, headers),
//            new TypeReference<R<T>>() {}
//        );
//    }
//
//    private <T> ResponseEntity<R<T>> getForEntity(String path, HttpHeaders headers, Class<T> clazz) {
//        return restTemplate.exchange(
//            getUrl(path),
//            HttpMethod.GET,
//            new HttpEntity<>(headers),
//            new TypeReference<R<T>>() {}
//        );
//    }
//
//    private ResponseEntity<R<Void>> delete(String path, HttpHeaders headers) {
//        return restTemplate.exchange(
//            getUrl(path),
//            HttpMethod.DELETE,
//            new HttpEntity<>(headers),
//            new TypeReference<R<Void>>() {}
//        );
//    }
//
//    // ==================== 好友申请测试 ====================
//
//    @Nested
//    @DisplayName("POST /chat/friend/request")
//    @Order(1)
//    class SendFriendRequestTests {
//
//        @Test
//        @DisplayName("成功发送好友请求")
//        void sendFriendRequest_success() {
//            Long userA = 10001L + Long.parseLong(uid());
//            Long userB = 20001L + Long.parseLong(uid());
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//            req.setMessage("你好，加个好友吧");
//
//            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
//                "/chat/friend/request", req, userHeaders(userA, "alice"), FriendRequestVO.class
//            );
//
//            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData()).isNotNull();
//            assertThat(resp.getBody().getData().getStatus()).isEqualTo("pending");
//        }
//
//        @Test
//        @DisplayName("不能添加自己为好友")
//        void sendFriendRequest_cannotAddSelf() {
//            Long userId = 30001L + Long.parseLong(uid());
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userId);
//
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/friend/request", req, userHeaders(userId, "bob")
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isFalse();
//            assertThat(resp.getBody().getCode()).isEqualTo(20602);
//        }
//
//        @Test
//        @DisplayName("重复发送好友请求返回已有申请")
//        void sendFriendRequest_alreadyExists() {
//            Long userA = 40001L + Long.parseLong(uid());
//            Long userB = 50001L + Long.parseLong(uid());
//
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//
//            // 第一次
//            post("/chat/friend/request", req, userHeaders(userA, "alice"));
//            // 第二次（应失败）
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/friend/request", req, userHeaders(userA, "alice")
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isFalse();
//            assertThat(resp.getBody().getCode()).isEqualTo(20604);
//        }
//    }
//
//    @Nested
//    @DisplayName("POST /chat/friend/accept/{id}")
//    @Order(2)
//    class AcceptFriendRequestTests {
//
//        private Long userA;
//        private Long userB;
//        private Long requestId;
//
//        @BeforeEach
//        void setup() {
//            userA = 60001L + Long.parseLong(uid());
//            userB = 70001L + Long.parseLong(uid());
//
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
//                "/chat/friend/request", req, userHeaders(userA, "alice"), FriendRequestVO.class
//            );
//            requestId = resp.getBody().getData().getRequestId();
//        }
//
//        @Test
//        @DisplayName("成功同意好友请求")
//        void acceptFriendRequest_success() {
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/friend/accept/" + requestId, null, userHeaders(userB, "bob")
//            );
//
//            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//        }
//
//        @Test
//        @DisplayName("非被申请人同意失败")
//        void acceptFriendRequest_wrongUser() {
//            Long userC = 80001L + Long.parseLong(uid());
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/friend/accept/" + requestId, null, userHeaders(userC, "carol")
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isFalse();
//            assertThat(resp.getBody().getCode()).isEqualTo(20606);
//        }
//    }
//
//    @Nested
//    @DisplayName("GET /chat/friend")
//    @Order(3)
//    class GetFriendsTests {
//
//        private Long userA;
//        private Long userB;
//
//        @BeforeEach
//        void setup() {
//            userA = 90001L + Long.parseLong(uid());
//            userB = 110001L + Long.parseLong(uid());
//            mockUserProfile(userA, "alice", "http://example.com/alice.jpg");
//            mockUserProfile(userB, "bob", "http://example.com/bob.jpg");
//
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
//                "/chat/friend/request", req, userHeaders(userA, "alice"), FriendRequestVO.class
//            );
//            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
//                 null, userHeaders(userB, "bob"));
//        }
//
//        @Test
//        @DisplayName("成功获取好友列表")
//        void getFriends_success() {
//            ResponseEntity<R<java.util.List<FriendVO>>> resp = getForEntity(
//                "/chat/friend", userHeaders(userA, "alice"), (Class<java.util.List<FriendVO>>)(Class<?>)java.util.List.class
//            );
//
//            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData()).isNotEmpty();
//        }
//
//        @Test
//        @DisplayName("无好友时返回空列表")
//        void getFriends_empty() {
//            Long soloUser = 120001L + Long.parseLong(uid());
//            ResponseEntity<R<java.util.List<FriendVO>>> resp = getForEntity(
//                "/chat/friend", userHeaders(soloUser, "solo"), (Class<java.util.List<FriendVO>>)(Class<?>)java.util.List.class
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData()).isEmpty();
//        }
//    }
//
//    @Nested
//    @DisplayName("GET /chat/friend/check/{userId}")
//    @Order(4)
//    class CheckFriendStatusTests {
//
//        @Test
//        @DisplayName("检查非好友关系")
//        void checkFriendStatus_notFriends() {
//            Long userA = 130001L + Long.parseLong(uid());
//            Long userB = 140001L + Long.parseLong(uid());
//
//            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
//                "/chat/friend/check/" + userB, userHeaders(userA, "alice"), FriendStatusVO.class
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
//            assertThat(resp.getBody().getData().getIsRequestSent()).isFalse();
//        }
//    }
//
//    // ==================== 私聊消息测试 ====================
//
//    @Nested
//    @DisplayName("POST /chat/message/send")
//    @Order(5)
//    class SendPrivateMessageTests {
//
//        private Long userA;
//        private Long userB;
//
//        @BeforeEach
//        void setup() {
//            userA = 150001L + Long.parseLong(uid());
//            userB = 160001L + Long.parseLong(uid());
//
//            // 建立好友关系
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
//                "/chat/friend/request", req, userHeaders(userA, "alice"), FriendRequestVO.class
//            );
//            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
//                 null, userHeaders(userB, "bob"));
//        }
//
//        @Test
//        @DisplayName("成功发送文本私聊消息")
//        void sendMessage_success() {
//            SendPrivateMessageDto dto = new SendPrivateMessageDto();
//            dto.setReceiverId(userB);
//            dto.setMessageType(1);
//            dto.setContent("你好 Bob！");
//
//            ResponseEntity<R<PrivateMessageVO>> resp = postForEntity(
//                "/chat/message/send", dto, userHeaders(userA, "alice"), PrivateMessageVO.class
//            );
//
//            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData().getMessageType()).isEqualTo(1);
//            assertThat(resp.getBody().getData().getIsOwn()).isTrue();
//        }
//
//        @Test
//        @DisplayName("非好友无法发送私聊消息")
//        void sendMessage_notFriends() {
//            Long stranger = 170001L + Long.parseLong(uid());
//            SendPrivateMessageDto dto = new SendPrivateMessageDto();
//            dto.setReceiverId(stranger);
//            dto.setMessageType(1);
//            dto.setContent("Hello");
//
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/message/send", dto, userHeaders(userA, "alice")
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isFalse();
//            assertThat(resp.getBody().getCode()).isEqualTo(20614);
//        }
//
//        @Test
//        @DisplayName("文本消息内容不能为空")
//        void sendMessage_emptyContent() {
//            SendPrivateMessageDto dto = new SendPrivateMessageDto();
//            dto.setReceiverId(userB);
//            dto.setMessageType(1);
//            dto.setContent("");
//
//            ResponseEntity<R<Void>> resp = post(
//                "/chat/message/send", dto, userHeaders(userA, "alice")
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isFalse();
//        }
//    }
//
//    @Nested
//    @DisplayName("GET /chat/conversation")
//    @Order(6)
//    class GetConversationsTests {
//
//        @Test
//        @DisplayName("获取会话列表（无会话时返回空列表）")
//        void getConversations_empty() {
//            Long user = 180001L + Long.parseLong(uid());
//            ResponseEntity<R<java.util.List<ConversationVO>>> resp = getForEntity(
//                "/chat/conversation",
//                userHeaders(user, "lonely"),
//                (Class<java.util.List<ConversationVO>>)(Class<?>)java.util.List.class
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//            assertThat(resp.getBody().getData()).isEmpty();
//        }
//    }
//
//    @Nested
//    @DisplayName("DELETE /chat/friend/{friendId}")
//    @Order(7)
//    class DeleteFriendTests {
//
//        private Long userA;
//        private Long userB;
//
//        @BeforeEach
//        void setup() {
//            userA = 190001L + Long.parseLong(uid());
//            userB = 200001L + Long.parseLong(uid());
//
//            FriendRequestDto req = new FriendRequestDto();
//            req.setToUserId(userB);
//            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
//                "/chat/friend/request", req, userHeaders(userA, "alice"), FriendRequestVO.class
//            );
//            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
//                 null, userHeaders(userB, "bob"));
//        }
//
//        @Test
//        @DisplayName("成功删除好友")
//        void deleteFriend_success() {
//            ResponseEntity<R<Void>> resp = delete(
//                "/chat/friend/" + userB, userHeaders(userA, "alice")
//            );
//
//            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().isSuccess()).isTrue();
//        }
//
//        @Test
//        @DisplayName("删除后不再是好友")
//        void deleteFriend_afterDelete() {
//            delete("/chat/friend/" + userB, userHeaders(userA, "alice"));
//
//            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
//                "/chat/friend/check/" + userB, userHeaders(userA, "alice"), FriendStatusVO.class
//            );
//
//            assertThat(resp.getBody()).isNotNull();
//            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
//        }
//    }
//}
