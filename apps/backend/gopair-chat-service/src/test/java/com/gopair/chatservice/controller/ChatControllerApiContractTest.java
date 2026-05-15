package com.gopair.chatservice.controller;

import com.gopair.chatservice.base.BaseIntegrationTest;
import com.gopair.chatservice.domain.dto.FriendRequestDto;
import com.gopair.chatservice.domain.dto.FriendStatusVO;
import com.gopair.chatservice.domain.dto.SendPrivateMessageDto;
import com.gopair.chatservice.domain.vo.FriendRequestVO;
import com.gopair.chatservice.domain.vo.FriendVO;
import com.gopair.chatservice.domain.vo.ConversationVO;
import com.gopair.chatservice.domain.vo.PrivateMessageVO;
import com.gopair.chatservice.domain.vo.UserSearchResultVO;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聊天服务接口契约测试。
 *
 * * [核心策略]
 * - 全集成测试：所有请求通过 TestRestTemplate 发送真实 HTTP，覆盖 Controller → Service → Mapper → DB 全链路。
 * - 用户身份注入：X-User-Id / X-Nickname 请求头由 ContextInitFilter 解析。
 * - 脏数据清理：@Transactional 保证 MySQL 回滚，@AfterEach flushDb() 清理 Redis。
 * - 外部依赖隔离：user-service 调用走 mockRestTemplate stub；WebSocket / RabbitMQ 由 @MockBean 隔离。
 * - 测试数据唯一性：所有 userId / requestId / messageId 均通过 uid() 生成，避免并发冲突。
 *
 * @author gopair
 */
@DisplayName("Chat API Contract Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatControllerApiContractTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    private String uid() {
        return String.valueOf(counter.incrementAndGet());
    }

    // ==================== Auth 辅助 ====================

    private HttpHeaders userHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Nickname", nickname);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<R<Void>> post(String path, Object body, HttpHeaders headers) {
        return (ResponseEntity<R<Void>>) (ResponseEntity<?>) restTemplate.exchange(
            getUrl(path),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<R<T>> postForEntity(String path, Object body, HttpHeaders headers,
                                                   ParameterizedTypeReference<R<T>> typeRef) {
        return (ResponseEntity<R<T>>) (ResponseEntity<?>) restTemplate.exchange(
            getUrl(path),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            typeRef
        );
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<R<T>> getForEntity(String path, HttpHeaders headers,
                                                   ParameterizedTypeReference<R<T>> typeRef) {
        return (ResponseEntity<R<T>>) (ResponseEntity<?>) restTemplate.exchange(
            getUrl(path),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            typeRef
        );
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<R<Void>> delete(String path, HttpHeaders headers) {
        return (ResponseEntity<R<Void>>) (ResponseEntity<?>) restTemplate.exchange(
            getUrl(path),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    // ==================== 好友申请测试 ====================

    @Nested
    @DisplayName("POST /chat/friend/request")
    @Order(1)
    class SendFriendRequestTests {

        @Test
        @DisplayName("成功发送好友请求")
        void sendFriendRequest_success() {
            Long userA = 10001L + Long.parseLong(uid());
            Long userB = 20001L + Long.parseLong(uid());
            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            req.setMessage("你好，加个好友吧");

            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotNull();
            assertThat(resp.getBody().getData().getStatus()).isEqualTo("pending");
            assertThat(resp.getBody().getData().getFromUserId()).isEqualTo(userA);
            assertThat(resp.getBody().getData().getToUserId()).isEqualTo(userB);
        }

        @Test
        @DisplayName("不能添加自己为好友")
        void sendFriendRequest_cannotAddSelf() {
            Long userId = 30001L + Long.parseLong(uid());
            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userId);

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/request", req, userHeaders(userId, "bob_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20602);
        }

        @Test
        @DisplayName("已是好友无法再发请求")
        void sendFriendRequest_alreadyFriends() {
            Long userA = 40001L + Long.parseLong(uid());
            Long userB = 50001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> r = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + r.getBody().getData().getRequestId(), null,
                 userHeaders(userB, "bob_" + uid()));

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/request", req, userHeaders(userA, "alice2_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20603);
        }

        @Test
        @DisplayName("重复发送好友请求返回已有申请")
        void sendFriendRequest_alreadyExists() {
            Long userA = 60001L + Long.parseLong(uid());
            Long userB = 70001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);

            post("/chat/friend/request", req, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/request", req, userHeaders(userA, "alice2_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20604);
        }

        @Test
        @DisplayName("toUserId 为 null 失败")
        void sendFriendRequest_nullToUserId() {
            Long userA = 80001L + Long.parseLong(uid());
            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(null);

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("POST /chat/friend/accept/{requestId}")
    @Order(2)
    class AcceptFriendRequestTests {

        private Long userA;
        private Long userB;
        private Long requestId;

        @BeforeEach
        void setup() {
            userA = 90001L + Long.parseLong(uid());
            userB = 110001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            requestId = resp.getBody().getData().getRequestId();
        }

        @Test
        @DisplayName("成功同意好友请求")
        void acceptFriendRequest_success() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/accept/" + requestId, null, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("非被申请人同意失败")
        void acceptFriendRequest_wrongUser() {
            Long userC = 120001L + Long.parseLong(uid());

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/accept/" + requestId, null, userHeaders(userC, "carol_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20606);
        }

        @Test
        @DisplayName("申请不存在时失败")
        void acceptFriendRequest_notFound() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/accept/999999", null, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20600);
        }

        @Test
        @DisplayName("重复同意已处理的申请失败")
        void acceptFriendRequest_alreadyProcessed() {
            post("/chat/friend/accept/" + requestId, null, userHeaders(userB, "bob_" + uid()));

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/accept/" + requestId, null, userHeaders(userB, "bob2_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20601);
        }
    }

    @Nested
    @DisplayName("POST /chat/friend/reject/{requestId}")
    @Order(3)
    class RejectFriendRequestTests {

        private Long userA;
        private Long userB;
        private Long requestId;

        @BeforeEach
        void setup() {
            userA = 130001L + Long.parseLong(uid());
            userB = 140001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            requestId = resp.getBody().getData().getRequestId();
        }

        @Test
        @DisplayName("成功拒绝好友请求")
        void rejectFriendRequest_success() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/reject/" + requestId, null, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("非被申请人拒绝失败")
        void rejectFriendRequest_wrongUser() {
            Long userC = 150001L + Long.parseLong(uid());

            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/reject/" + requestId, null, userHeaders(userC, "carol_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20606);
        }

        @Test
        @DisplayName("申请不存在时失败")
        void rejectFriendRequest_notFound() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/friend/reject/999999", null, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20600);
        }
    }

    @Nested
    @DisplayName("GET /chat/friend")
    @Order(4)
    class GetFriendsTests {

        private Long userA;
        private Long userB;

        @BeforeEach
        void setup() {
            userA = 160001L + Long.parseLong(uid());
            userB = 170001L + Long.parseLong(uid());
            mockUserProfile(userA, "alice_" + uid(), "http://example.com/alice.jpg");
            mockUserProfile(userB, "bob_" + uid(), "http://example.com/bob.jpg");

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));
        }

        @Test
        @DisplayName("成功获取好友列表")
        void getFriends_success() {
            ResponseEntity<R<List<FriendVO>>> resp = getForEntity(
                "/chat/friend", userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<List<FriendVO>>>() {}
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotEmpty();
        }

        @Test
        @DisplayName("无好友时返回空列表")
        void getFriends_empty() {
            Long soloUser = 180001L + Long.parseLong(uid());
            mockUserProfile(soloUser, "solo_" + uid(), "http://example.com/solo.jpg");

            ResponseEntity<R<List<FriendVO>>> resp = getForEntity(
                "/chat/friend", userHeaders(soloUser, "solo_" + uid()),
                new ParameterizedTypeReference<R<List<FriendVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEmpty();
        }

        @Test
        @DisplayName("关键词搜索好友")
        void getFriends_searchKeyword() {
            ResponseEntity<R<List<FriendVO>>> resp = getForEntity(
                "/chat/friend?keyword=bob", userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<List<FriendVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /chat/friend/request/incoming")
    @Order(5)
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("有收到的申请时返回列表")
        void getIncoming_hasRequests() {
            Long userA = 190001L + Long.parseLong(uid());
            Long userB = 200001L + Long.parseLong(uid());
            mockUserProfile(userA, "alice_" + uid(), "http://example.com/alice.jpg");

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            req.setMessage("请加我好友");
            post("/chat/friend/request", req, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<List<FriendRequestVO>>> resp = getForEntity(
                "/chat/friend/request/incoming", userHeaders(userB, "bob_" + uid()),
                new ParameterizedTypeReference<R<List<FriendRequestVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotEmpty();
        }

        @Test
        @DisplayName("无收到的申请时返回空列表")
        void getIncoming_empty() {
            Long lonelyUser = 210001L + Long.parseLong(uid());
            mockUserProfile(lonelyUser, "lonely_" + uid(), "http://example.com/lonely.jpg");

            ResponseEntity<R<List<FriendRequestVO>>> resp = getForEntity(
                "/chat/friend/request/incoming", userHeaders(lonelyUser, "lonely_" + uid()),
                new ParameterizedTypeReference<R<List<FriendRequestVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /chat/friend/request/outgoing")
    @Order(6)
    class GetOutgoingRequestsTests {

        @Test
        @DisplayName("有发出的申请时返回列表")
        void getOutgoing_hasRequests() {
            Long userA = 220001L + Long.parseLong(uid());
            Long userB = 230001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            req.setMessage("想加你好友");
            post("/chat/friend/request", req, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<List<FriendRequestVO>>> resp = getForEntity(
                "/chat/friend/request/outgoing", userHeaders(userA, "alice2_" + uid()),
                new ParameterizedTypeReference<R<List<FriendRequestVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotEmpty();
        }

        @Test
        @DisplayName("无发出的申请时返回空列表")
        void getOutgoing_empty() {
            Long quietUser = 240001L + Long.parseLong(uid());
            mockUserProfile(quietUser, "quiet_" + uid(), "http://example.com/quiet.jpg");

            ResponseEntity<R<List<FriendRequestVO>>> resp = getForEntity(
                "/chat/friend/request/outgoing", userHeaders(quietUser, "quiet_" + uid()),
                new ParameterizedTypeReference<R<List<FriendRequestVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /chat/friend/check/{userId}")
    @Order(7)
    class CheckFriendStatusTests {

        @Test
        @DisplayName("检查非好友关系")
        void checkFriendStatus_notFriends() {
            Long userA = 250001L + Long.parseLong(uid());
            Long userB = 260001L + Long.parseLong(uid());

            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
                "/chat/friend/check/" + userB, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendStatusVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
            assertThat(resp.getBody().getData().getIsRequestSent()).isFalse();
            assertThat(resp.getBody().getData().getIsRequestReceived()).isFalse();
        }

        @Test
        @DisplayName("检查已发出的申请状态")
        void checkFriendStatus_requestSent() {
            Long userA = 270001L + Long.parseLong(uid());
            Long userB = 280001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            post("/chat/friend/request", req, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
                "/chat/friend/check/" + userB, userHeaders(userA, "alice2_" + uid()),
                new ParameterizedTypeReference<R<FriendStatusVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
            assertThat(resp.getBody().getData().getIsRequestSent()).isTrue();
        }

        @Test
        @DisplayName("检查已收到的申请状态")
        void checkFriendStatus_requestReceived() {
            Long userA = 290001L + Long.parseLong(uid());
            Long userB = 300001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            post("/chat/friend/request", req, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
                "/chat/friend/check/" + userA, userHeaders(userB, "bob_" + uid()),
                new ParameterizedTypeReference<R<FriendStatusVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
            assertThat(resp.getBody().getData().getIsRequestReceived()).isTrue();
            assertThat(resp.getBody().getData().getRequestId()).isNotNull();
        }

        @Test
        @DisplayName("检查好友关系")
        void checkFriendStatus_isFriends() {
            Long userA = 310001L + Long.parseLong(uid());
            Long userB = 320001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> r = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + r.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));

            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
                "/chat/friend/check/" + userB, userHeaders(userA, "alice2_" + uid()),
                new ParameterizedTypeReference<R<FriendStatusVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getIsFriend()).isTrue();
            assertThat(resp.getBody().getData().getIsRequestSent()).isFalse();
            assertThat(resp.getBody().getData().getIsRequestReceived()).isFalse();
        }
    }

    @Nested
    @DisplayName("DELETE /chat/friend/{friendId}")
    @Order(8)
    class DeleteFriendTests {

        private Long userA;
        private Long userB;

        @BeforeEach
        void setup() {
            userA = 330001L + Long.parseLong(uid());
            userB = 340001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));
        }

        @Test
        @DisplayName("成功删除好友")
        void deleteFriend_success() {
            ResponseEntity<R<Void>> resp = delete(
                "/chat/friend/" + userB, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("删除后不再是好友")
        void deleteFriend_afterDelete() {
            delete("/chat/friend/" + userB, userHeaders(userA, "alice_" + uid()));

            ResponseEntity<R<FriendStatusVO>> resp = getForEntity(
                "/chat/friend/check/" + userB, userHeaders(userA, "alice2_" + uid()),
                new ParameterizedTypeReference<R<FriendStatusVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getIsFriend()).isFalse();
        }

        @Test
        @DisplayName("删除非好友关系失败")
        void deleteFriend_notFriends() {
            Long userC = 350001L + Long.parseLong(uid());

            ResponseEntity<R<Void>> resp = delete(
                "/chat/friend/" + userC, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20607);
        }
    }

    // ==================== 私聊消息测试 ====================

    @Nested
    @DisplayName("POST /chat/message/send")
    @Order(9)
    class SendPrivateMessageTests {

        private Long userA;
        private Long userB;

        @BeforeEach
        void setup() {
            userA = 360001L + Long.parseLong(uid());
            userB = 370001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));
        }

        @Test
        @DisplayName("成功发送文本私聊消息")
        void sendMessage_success() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("你好 Bob！");

            ResponseEntity<R<PrivateMessageVO>> resp = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getMessageType()).isEqualTo(1);
            assertThat(resp.getBody().getData().getIsOwn()).isTrue();
            assertThat(resp.getBody().getData().getContent()).isEqualTo("你好 Bob！");
        }

        @Test
        @DisplayName("非好友无法发送私聊消息")
        void sendMessage_notFriends() {
            Long stranger = 380001L + Long.parseLong(uid());
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(stranger);
            dto.setMessageType(1);
            dto.setContent("Hello");

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20614);
        }

        @Test
        @DisplayName("文本消息内容为空失败")
        void sendMessage_emptyContent() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("");

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20617);
        }

        @Test
        @DisplayName("文本消息内容过长失败")
        void sendMessage_contentTooLong() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("A".repeat(2001));

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20613);
        }

        @Test
        @DisplayName("图片消息缺少 fileUrl 失败")
        void sendMessage_imageWithoutFileUrl() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(2);
            dto.setContent("这是一张图片");

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20618);
        }

        @Test
        @DisplayName("图片消息成功（有 fileUrl）")
        void sendMessage_imageSuccess() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(2);
            dto.setContent("看这张图");
            dto.setFileUrl("http://example.com/image.jpg");
            dto.setFileName("image.jpg");
            dto.setFileSize(102400L);

            ResponseEntity<R<PrivateMessageVO>> resp = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getMessageType()).isEqualTo(2);
            assertThat(resp.getBody().getData().getFileUrl()).isEqualTo("http://example.com/image.jpg");
        }

        @Test
        @DisplayName("文件消息成功")
        void sendMessage_fileSuccess() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(3);
            dto.setContent("发个文件");
            dto.setFileUrl("http://example.com/doc.pdf");
            dto.setFileName("doc.pdf");
            dto.setFileSize(204800L);

            ResponseEntity<R<PrivateMessageVO>> resp = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getMessageType()).isEqualTo(3);
        }

        @Test
        @DisplayName("消息类型非法")
        void sendMessage_invalidMessageType() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(99);
            dto.setContent("测试");

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20616);
        }

        @Test
        @DisplayName("receiverId 为 null 失败")
        void sendMessage_nullReceiverId() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(null);
            dto.setMessageType(1);
            dto.setContent("test");

            ResponseEntity<R<Void>> resp = post(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("GET /chat/conversation")
    @Order(10)
    class GetConversationsTests {

        private Long userA;
        private Long userB;

        @BeforeEach
        void setup() {
            userA = 390001L + Long.parseLong(uid());
            userB = 400001L + Long.parseLong(uid());

            mockUserProfile(userA, "alice_" + uid(), "http://example.com/alice.jpg");
            mockUserProfile(userB, "bob_" + uid(), "http://example.com/bob.jpg");

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));

            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("你好 Bob！");
            post("/chat/message/send", dto, userHeaders(userA, "alice_" + uid()));
        }

        @Test
        @DisplayName("获取会话列表（有条会话）")
        void getConversations_withMessages() {
            ResponseEntity<R<List<ConversationVO>>> resp = getForEntity(
                "/chat/conversation", userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<List<ConversationVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotEmpty();
        }

        @Test
        @DisplayName("获取会话列表（无会话时返回空列表）")
        void getConversations_empty() {
            Long user = 410001L + Long.parseLong(uid());
            mockUserProfile(user, "lonely_" + uid(), "http://example.com/lonely.jpg");

            ResponseEntity<R<List<ConversationVO>>> resp = getForEntity(
                "/chat/conversation", userHeaders(user, "lonely_" + uid()),
                new ParameterizedTypeReference<R<List<ConversationVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /chat/conversation/{conversationId}/message")
    @Order(11)
    class GetMessagesTests {

        private Long userA;
        private Long userB;
        private Long conversationId;

        @BeforeEach
        void setup() {
            userA = 420001L + Long.parseLong(uid());
            userB = 430001L + Long.parseLong(uid());
            conversationId = Math.min(userA, userB) * 1_000_000_0000L + Math.max(userA, userB);

            mockUserProfile(userA, "alice_" + uid(), "http://example.com/alice.jpg");
            mockUserProfile(userB, "bob_" + uid(), "http://example.com/bob.jpg");

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));

            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("你好 Bob！");
            post("/chat/message/send", dto, userHeaders(userA, "alice_" + uid()));
        }

        @Test
        @DisplayName("首次加载最新消息")
        void getMessages_firstLoad() {
            ResponseEntity<R<PageResult<PrivateMessageVO>>> resp = getForEntity(
                "/chat/conversation/" + conversationId + "/message",
                userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PageResult<PrivateMessageVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getRecords()).isNotEmpty();
        }

        @Test
        @DisplayName("游标翻页加载历史消息")
        void getMessages_cursorPagination() {
            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("消息1");
            ResponseEntity<R<PrivateMessageVO>> r1 = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );
            dto.setContent("消息2");
            postForEntity("/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {});

            Long latestMsgId = r1.getBody().getData().getMessageId();

            ResponseEntity<R<PageResult<PrivateMessageVO>>> resp = getForEntity(
                "/chat/conversation/" + conversationId + "/message?beforeMessageId=" + latestMsgId + "&pageSize=1",
                userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PageResult<PrivateMessageVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("空会话返回空列表")
        void getMessages_emptyConversation() {
            Long userC = 440001L + Long.parseLong(uid());
            Long userD = 450001L + Long.parseLong(uid());
            Long emptyConvId = Math.min(userC, userD) * 1_000_000_0000L + Math.max(userC, userD);

            ResponseEntity<R<PageResult<PrivateMessageVO>>> resp = getForEntity(
                "/chat/conversation/" + emptyConvId + "/message",
                userHeaders(userC, "carol_" + uid()),
                new ParameterizedTypeReference<R<PageResult<PrivateMessageVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getRecords()).isEmpty();
        }

        @Test
        @DisplayName("pageSize 超过上限自动裁剪")
        void getMessages_pageSizeClamped() {
            ResponseEntity<R<PageResult<PrivateMessageVO>>> resp = getForEntity(
                "/chat/conversation/" + conversationId + "/message?pageSize=100",
                userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PageResult<PrivateMessageVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("DELETE /chat/message/{messageId}")
    @Order(12)
    class DeleteMessageTests {

        private Long userA;
        private Long userB;
        private Long messageId;

        @BeforeEach
        void setup() {
            userA = 460001L + Long.parseLong(uid());
            userB = 470001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));

            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("要删除的消息");
            ResponseEntity<R<PrivateMessageVO>> r = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );
            messageId = r.getBody().getData().getMessageId();
        }

        @Test
        @DisplayName("发送者成功删除消息")
        void deleteMessage_success() {
            ResponseEntity<R<Void>> resp = delete(
                "/chat/message/" + messageId, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("非发送者无法删除消息")
        void deleteMessage_notOwner() {
            ResponseEntity<R<Void>> resp = delete(
                "/chat/message/" + messageId, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20609);
        }

        @Test
        @DisplayName("消息不存在删除失败")
        void deleteMessage_notFound() {
            ResponseEntity<R<Void>> resp = delete(
                "/chat/message/999999", userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20608);
        }
    }

    @Nested
    @DisplayName("POST /chat/message/{messageId}/recall")
    @Order(13)
    class RecallMessageTests {

        private Long userA;
        private Long userB;
        private Long messageId;

        @BeforeEach
        void setup() {
            userA = 480001L + Long.parseLong(uid());
            userB = 490001L + Long.parseLong(uid());

            FriendRequestDto req = new FriendRequestDto();
            req.setToUserId(userB);
            ResponseEntity<R<FriendRequestVO>> resp = postForEntity(
                "/chat/friend/request", req, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<FriendRequestVO>>() {}
            );
            post("/chat/friend/accept/" + resp.getBody().getData().getRequestId(),
                 null, userHeaders(userB, "bob_" + uid()));

            SendPrivateMessageDto dto = new SendPrivateMessageDto();
            dto.setReceiverId(userB);
            dto.setMessageType(1);
            dto.setContent("要撤回的消息");
            ResponseEntity<R<PrivateMessageVO>> r = postForEntity(
                "/chat/message/send", dto, userHeaders(userA, "alice_" + uid()),
                new ParameterizedTypeReference<R<PrivateMessageVO>>() {}
            );
            messageId = r.getBody().getData().getMessageId();
        }

        @Test
        @DisplayName("发送者成功撤回消息")
        void recallMessage_success() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/message/" + messageId + "/recall", null, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("非发送者无法撤回消息")
        void recallMessage_notOwner() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/message/" + messageId + "/recall", null, userHeaders(userB, "bob_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20610);
        }

        @Test
        @DisplayName("消息不存在撤回失败")
        void recallMessage_notFound() {
            ResponseEntity<R<Void>> resp = post(
                "/chat/message/999999/recall", null, userHeaders(userA, "alice_" + uid())
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(20608);
        }
    }

    // ==================== 额外接口测试 ====================

    @Nested
    @DisplayName("GET /chat/friend/user/{userId}")
    @Order(14)
    class GetUserProfileTests {

        @Test
        @DisplayName("获取用户公开资料（mock 返回）")
        void getUserProfile_success() {
            Long userId = 500001L + Long.parseLong(uid());
            mockUserProfile(userId, "target_" + uid(), "http://example.com/target.jpg");

            ResponseEntity<R<Map>> resp = getForEntity(
                "/chat/friend/user/" + userId,
                userHeaders(600001L + Long.parseLong(uid()), "viewer_" + uid()),
                new ParameterizedTypeReference<R<Map>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotNull();
        }

        @Test
        @DisplayName("获取不存在的用户资料返回 fallback")
        void getUserProfile_fallback() {
            Long ghostUser = 510001L + Long.parseLong(uid());

            ResponseEntity<R<Map>> resp = getForEntity(
                "/chat/friend/user/" + ghostUser,
                userHeaders(520001L + Long.parseLong(uid()), "viewer_" + uid()),
                new ParameterizedTypeReference<R<Map>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /chat/friend/search")
    @Order(15)
    class SearchUsersTests {

        @Test
        @DisplayName("搜索用户成功")
        void searchUsers_success() {
            Long userId = 530001L + Long.parseLong(uid());
            String keyword = "alice";

            ResponseEntity<R<PageResult<UserSearchResultVO>>> resp = getForEntity(
                "/chat/friend/search?keyword=" + keyword + "&pageNum=1&pageSize=10",
                userHeaders(userId, "searcher_" + uid()),
                new ParameterizedTypeReference<R<PageResult<UserSearchResultVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("搜索空关键词返回空结果")
        void searchUsers_emptyKeyword() {
            Long userId = 540001L + Long.parseLong(uid());

            ResponseEntity<R<PageResult<UserSearchResultVO>>> resp = getForEntity(
                "/chat/friend/search?keyword=&pageNum=1&pageSize=10",
                userHeaders(userId, "searcher2_" + uid()),
                new ParameterizedTypeReference<R<PageResult<UserSearchResultVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("分页参数测试")
        void searchUsers_pagination() {
            Long userId = 550001L + Long.parseLong(uid());

            ResponseEntity<R<PageResult<UserSearchResultVO>>> resp = getForEntity(
                "/chat/friend/search?keyword=test&pageNum=2&pageSize=5",
                userHeaders(userId, "searcher3_" + uid()),
                new ParameterizedTypeReference<R<PageResult<UserSearchResultVO>>>() {}
            );

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }
}
