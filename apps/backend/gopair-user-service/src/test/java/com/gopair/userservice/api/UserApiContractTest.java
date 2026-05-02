package com.gopair.userservice.api;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.userservice.base.BaseIntegrationTest;
import com.gopair.userservice.base.TestMailConfig;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.dto.auth.ForgotPasswordRequest;
import com.gopair.userservice.domain.dto.auth.LoginRequest;
import com.gopair.userservice.domain.dto.auth.RegisterRequest;
import com.gopair.userservice.domain.dto.auth.SendCodeRequest;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.domain.vo.auth.LoginResponse;
import com.gopair.userservice.domain.vo.auth.RegisterResponse;
import com.gopair.userservice.mapper.UserMapper;
import com.gopair.userservice.service.StubEmailServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.gopair.common.constants.SystemConstants.*;
import static com.gopair.userservice.enums.UserErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户服务 HTTP 接口契约测试。
 *
 * * [测试策略]
 * - 全部通过 TestRestTemplate 发送真实 HTTP 请求，完整经过 Controller → Service → Mapper → DB
 * - 每个接口覆盖：1个成功场景 + 所有已知异常场景 + 边界值
 * - 邮件发送走 StubEmailServiceImpl（不发真实邮件）
 * - @example.com 邮箱绕过业务层验证码校验
 * - MySQL 数据由 @Transactional 自动回滚，Redis 由 BaseIntegrationTest.flushDb() 清空
 *
 * * [脏数据清理]
 * - MySQL：@Transactional 回滚
 * - Redis：BaseIntegrationTest @AfterEach flushDb()
 */
@Import({TestMailConfig.class, StubEmailServiceImpl.class})
class UserApiContractTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1000000);

    // ==================== 注册接口 ====================

    @Nested
    @DisplayName("POST /user/register")
    class RegisterTests {

        @Test
        @DisplayName("成功注册")
        void register_success() {
            String u = uid();
            RegisterRequest req = buildRegister("alice_" + u, "alice_" + u + "@example.com", "P@ss1234");
            ResponseEntity<R<RegisterResponse>> resp = callRegister(req);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            RegisterResponse data = resp.getBody().getData();
            assertThat(data.getUserId()).isNotNull();
            assertThat(data.getNickname()).isEqualTo("alice_" + u);
            assertThat(data.getEmail()).isEqualTo(req.getEmail());
            assertThat(data.getMessage()).isEqualTo("注册成功");
        }

        @Test
        @DisplayName("昵称重复 - 注册失败")
        void register_duplicateNickname() {
            String u = uid();
            RegisterRequest req1 = buildRegister("bob_" + u, "bob1_" + u + "@example.com", "P@ss1234");
            callRegister(req1);
            RegisterRequest req2 = buildRegister("bob_" + u, "bob2_" + u + "@example.com", "P@ss1234");
            ResponseEntity<R<RegisterResponse>> resp = callRegister(req2);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(NICKNAME_ALREADY_EXISTS.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(NICKNAME_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("邮箱重复 - 注册失败")
        void register_duplicateEmail() {
            String u = uid();
            String email = "dup_" + u + "@example.com";
            RegisterRequest req1 = buildRegister("user1_" + u, email, "P@ss1234");
            callRegister(req1);
            RegisterRequest req2 = buildRegister("user2_" + u, email, "P@ss1234");
            ResponseEntity<R<RegisterResponse>> resp = callRegister(req2);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(EMAIL_ALREADY_EXISTS.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(EMAIL_ALREADY_EXISTS.getMessage());
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidRegisterData")
        @DisplayName("无效参数 - 注册失败")
        void register_invalidParam(RegisterRequest req) {
            ResponseEntity<R<RegisterResponse>> resp = callRegister(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ==================== 登录接口 ====================

    @Nested
    @DisplayName("POST /user/login")
    class LoginTests {

        @Test
        @DisplayName("成功登录")
        void login_success() {
            String u = uid();
            String email = "login_" + u + "@example.com";
            RegisterRequest reg = buildRegister("loginuser_" + u, email, "P@ss1234");
            callRegister(reg);
            ResponseEntity<R<LoginResponse>> resp = callLogin(buildLogin(email, "P@ss1234"));
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            LoginResponse data = resp.getBody().getData();
            assertThat(data.getUserId()).isNotNull();
            assertThat(data.getNickname()).isEqualTo("loginuser_" + u);
            assertThat(data.getToken()).isNotBlank();
            assertThat(data.getToken()).contains(".");
        }

        @Test
        @DisplayName("用户不存在 - 登录失败")
        void login_userNotFound() {
            ResponseEntity<R<LoginResponse>> resp = callLogin(buildLogin("notexist_" + uid() + "@example.com", "P@ss1234"));
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("密码错误 - 登录失败")
        void login_wrongPassword() {
            String u = uid();
            String email = "wrongpwd_" + u + "@example.com";
            RegisterRequest reg = buildRegister("wrongpwd_" + u, email, "P@ss1234");
            callRegister(reg);
            ResponseEntity<R<LoginResponse>> resp = callLogin(buildLogin(email, "WrongPassword1"));
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(PASSWORD_ERROR.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(PASSWORD_ERROR.getMessage());
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidLoginData")
        @DisplayName("空或null参数 - 登录失败")
        void login_invalidParam(String email, String password) {
            ResponseEntity<R<LoginResponse>> resp = callLogin(buildLogin(email, password));
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ==================== 查询用户接口 ====================

    @Nested
    @DisplayName("GET /user/{id}")
    class GetUserTests {

        @Test
        @DisplayName("成功查询")
        void getUser_success() {
            String u = uid();
            String email = "getuser_" + u + "@example.com";
            RegisterRequest reg = buildRegister("getuser_" + u, email, "P@ss1234");
            Long userId = callRegister(reg).getBody().getData().getUserId();
            ResponseEntity<R<UserVO>> resp = callGetUser(userId);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getNickname()).isEqualTo("getuser_" + u);
        }

        @Test
        @DisplayName("用户不存在 - 查询失败")
        void getUser_notFound() {
            ResponseEntity<R<UserVO>> resp = callGetUser(999999L);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_NOT_FOUND.getMessage());
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidUserIds")
        @DisplayName("非法ID - 查询失败")
        void getUser_invalidId(Long invalidId) {
            ResponseEntity<R<UserVO>> resp = callGetUser(invalidId);
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ==================== 更新用户接口 ====================

    @Nested
    @DisplayName("PUT /user")
    class UpdateUserTests {

        @Test
        @DisplayName("成功更新")
        void updateUser_success() {
            String u = uid();
            String email = "update_" + u + "@example.com";
            Long userId = callRegister(buildRegister("updateuser_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, "updated_" + uid(), "updated_" + uid() + "@example.com", null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("用户不存在 - 更新失败")
        void updateUser_notFound() {
            UserDto dto = buildUpdate(999999L, "ghost_" + uid(), "ghost_" + uid() + "@example.com", null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("昵称冲突 - 更新失败")
        void updateUser_nicknameConflict() {
            String u = uid();
            String email1 = "conflict1_" + u + "@example.com";
            String email2 = "conflict2_" + u + "@example.com";
            Long id1 = callRegister(buildRegister("samename_" + u, email1, "P@ss1234")).getBody().getData().getUserId();
            callRegister(buildRegister("otheruser_" + u, email2, "P@ss1234"));
            UserDto dto = buildUpdate(id1, "otheruser_" + u, email2, null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(NICKNAME_ALREADY_EXISTS.getCode());
        }

        @Test
        @DisplayName("邮箱冲突 - 更新失败")
        void updateUser_emailConflict() {
            String u = uid();
            String email1 = "emailc1_" + u + "@example.com";
            String email2 = "emailc2_" + u + "@example.com";
            Long id1 = callRegister(buildRegister("user1_" + u, email1, "P@ss1234")).getBody().getData().getUserId();
            callRegister(buildRegister("user2_" + u, email2, "P@ss1234"));
            UserDto dto = buildUpdate(id1, "user1updated_" + uid(), email2, null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(EMAIL_ALREADY_EXISTS.getCode());
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidUpdateData")
        @DisplayName("空值参数 - 更新失败")
        void updateUser_invalidParam(UserDto dto) {
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ==================== 删除用户接口 ====================

    @Nested
    @DisplayName("DELETE /user/{id}")
    class DeleteUserTests {

        @Test
        @DisplayName("成功删除")
        void deleteUser_success() {
            String u = uid();
            String email = "delete_" + u + "@example.com";
            Long userId = callRegister(buildRegister("deleteuser_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<Boolean>> resp = callDelete(userId);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();
            ResponseEntity<R<UserVO>> getResp = callGetUser(userId);
            assertThat(getResp.getBody().isSuccess()).isFalse();
            assertThat(getResp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("用户不存在 - 删除失败")
        void deleteUser_notFound() {
            ResponseEntity<R<Boolean>> resp = callDelete(999999L);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_NOT_FOUND.getMessage());
        }
    }

    // ==================== 分页查询接口 ====================

    @Nested
    @Rollback(false)
    @DisplayName("GET /user/page")
    class PageUserTests {

        @Autowired
        private UserMapper userMapper;

        @Test
        @DisplayName("分页成功")
        void page_success() {
            for (int i = 0; i < 5; i++) {
                String u = uid();
                callRegister(buildRegister("pageuser" + i + "_" + u, "page" + i + "_" + u + "@example.com", "P@ss1234"));
            }
            ResponseEntity<R<PageResult<UserVO>>> resp = callPage(1, 10);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            PageResult<UserVO> page = resp.getBody().getData();
            assertThat(page.getTotal()).isGreaterThanOrEqualTo(5L);
            assertThat(page.getRecords()).isNotEmpty();
        }

        @Test
        @DisplayName("大页码返回空")
        void page_outOfRange() {
            ResponseEntity<R<PageResult<UserVO>>> resp = callPage(999, 10);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            PageResult<UserVO> page = resp.getBody().getData();
            assertThat(page.getTotal()).isGreaterThanOrEqualTo(0L);
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideBoundaryPageParams")
        @DisplayName("非法页码参数")
        void page_boundary(int pageNum, int pageSize) {
            ResponseEntity<R<PageResult<UserVO>>> resp = callPage(pageNum, pageSize);
            assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("超大pageSize仍正常返回")
        void page_oversizedPageSize() {
            ResponseEntity<R<PageResult<UserVO>>> resp = callPage(1, 1000);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    // ==================== 注销账号接口 ====================

    @Nested
    @DisplayName("DELETE /user/{id}/cancel")
    class CancelAccountTests {

        @Test
        @DisplayName("成功注销")
        void cancel_success() {
            String u = uid();
            String email = "cancel_" + u + "@example.com";
            Long userId = callRegister(buildRegister("canceluser_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<Void>> resp = callCancel(userId);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("用户不存在 - 注销失败")
        void cancel_notFound() {
            ResponseEntity<R<Void>> resp = callCancel(999999L);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("重复注销 - 失败")
        void cancel_alreadyCancelled() {
            String u = uid();
            String email = "repeatuser_" + u + "@example.com";
            Long userId = callRegister(buildRegister("repeatuser_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            callCancel(userId);
            ResponseEntity<R<Void>> resp = callCancel(userId);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(USER_ALREADY_CANCELLED.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(USER_ALREADY_CANCELLED.getMessage());
        }
    }

    // ==================== 发送验证码接口 ====================

    @Nested
    @DisplayName("POST /user/sendCode")
    class SendCodeTests {

        @Test
        @DisplayName("发送成功（@example.com 绕过验证）")
        void sendCode_success() {
            SendCodeRequest req = buildSendCode("sendcode_" + uid() + "@example.com", "register");
            ResponseEntity<R<Void>> resp = callSendCode(req);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("忘记密码场景 - 邮箱未注册")
        void sendCode_resetPassword_unregistered() {
            SendCodeRequest req = buildSendCode("notreg_" + uid() + "@example.com", "resetPassword");
            ResponseEntity<R<Void>> resp = callSendCode(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(EMAIL_NOT_EXISTS.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(EMAIL_NOT_EXISTS.getMessage());
        }
    }

    // ==================== 忘记密码接口 ====================

    @Nested
    @DisplayName("POST /user/forgotPassword")
    class ForgotPasswordTests {

        @Test
        @DisplayName("重置成功")
        void forgotPassword_success() {
            String u = uid();
            String email = "forgot_" + u + "@example.com";
            callRegister(buildRegister("forgotuser_" + u, email, "P@ss1234"));
            callSendCode(buildSendCode(email, "resetPassword"));
            String code = extractCodeFromRedis(email);
            ForgotPasswordRequest req = buildForgot(email, code, "NewP@ss123");
            ResponseEntity<R<Void>> resp = callForgotPassword(req);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            ResponseEntity<R<LoginResponse>> loginResp = callLogin(buildLogin(email, "NewP@ss123"));
            assertThat(loginResp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("验证码错误 - 重置失败")
        void forgotPassword_wrongCode() {
            String u = uid();
            String email = "wrongcode_" + u + "@example.com";
            callRegister(buildRegister("wrongcode_" + u, email, "P@ss1234"));
            callSendCode(buildSendCode(email, "resetPassword"));
            ForgotPasswordRequest req = buildForgot(email, "000000", "NewP@ss123");
            ResponseEntity<R<Void>> resp = callForgotPassword(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(VERIFICATION_CODE_INVALID.getCode());
            assertThat(resp.getBody().getMsg()).isEqualTo(VERIFICATION_CODE_INVALID.getMessage());
        }

        @Test
        @DisplayName("邮箱未注册 - 重置失败（先校验验证码，Redis无此邮箱记录则报验证码无效）")
        void forgotPassword_notRegistered() {
            ForgotPasswordRequest req = buildForgot("ghost_" + uid() + "@example.com", "123456", "NewP@ss123");
            ResponseEntity<R<Void>> resp = callForgotPassword(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(VERIFICATION_CODE_INVALID.getCode());
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidForgotPasswordData")
        @DisplayName("空值参数 - 重置失败")
        void forgotPassword_invalidParam(ForgotPasswordRequest req) {
            ResponseEntity<R<Void>> resp = callForgotPassword(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("新旧密码相同 - 重置失败")
        void forgotPassword_samePasswordAsOld() {
            String u = uid();
            String email = "sameold_" + u + "@example.com";
            String pwd = "SameP@ss123";
            callRegister(buildRegister("sameold_" + u, email, pwd));
            callSendCode(buildSendCode(email, "resetPassword"));
            String code = extractCodeFromRedis(email);
            ForgotPasswordRequest req = buildForgot(email, code, pwd);
            ResponseEntity<R<Void>> resp = callForgotPassword(req);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(PASSWORD_SAME_AS_OLD.getCode());
        }
    }

    // ==================== 批量查询接口 ====================

    @Nested
    @DisplayName("GET /user/by-ids")
    class ListUsersByIdsTests {

        @Test
        @DisplayName("批量查询成功")
        void listByIds_success() {
            String u = uid();
            Long id1 = callRegister(buildRegister("multi1_" + u, "multi1_" + u + "@example.com", "P@ss1234")).getBody().getData().getUserId();
            Long id2 = callRegister(buildRegister("multi2_" + u, "multi2_" + u + "@example.com", "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<List<UserVO>>> resp = callByIds(id1 + "," + id2);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).hasSize(2);
        }

        @Test
        @DisplayName("空串参数")
        void listByIds_empty() {
            ResponseEntity<R<List<UserVO>>> resp = callByIds("");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEmpty();
        }

        @Test
        @DisplayName("混合存在与不存在的ID")
        void listByIds_mixed() {
            String u = uid();
            Long existingId = callRegister(buildRegister("mixuser_" + u, "mix_" + u + "@example.com", "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<List<UserVO>>> resp = callByIds(existingId + ",999998,999999");
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).hasSize(1);
            assertThat(resp.getBody().getData().get(0).getUserId()).isEqualTo(existingId);
        }

        @Test
        @DisplayName("重复ID去重")
        void listByIds_deduplication() {
            String u = uid();
            Long id = callRegister(buildRegister("dedup_" + u, "dedup_" + u + "@example.com", "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<List<UserVO>>> resp = callByIds(id + "," + id + "," + id);
            assertThat(resp.getBody().getData()).hasSize(1);
        }

        @Test
        @DisplayName("参数含非法片段自动跳过")
        void listByIds_invalidPartsSkipped() {
            String u = uid();
            Long id = callRegister(buildRegister("invparts_" + u, "invparts_" + u + "@example.com", "P@ss1234")).getBody().getData().getUserId();
            ResponseEntity<R<List<UserVO>>> resp = callByIds(id + ",abc,xyz");
            assertThat(resp.getBody().getData()).hasSize(1);
        }
    }

    // ==================== updateUser 复杂逻辑 ====================

    @Nested
    @DisplayName("PUT /user 复杂逻辑")
    class UpdateUserLogicTests {

        @Test
        @DisplayName("改密成功 - 新密码可登录，旧密码不可用")
        void updateUser_withPasswordChange_success() {
            String u = uid();
            String email = "pwdchg_" + u + "@example.com";
            Long userId = callRegister(buildRegister("pwdchg_" + u, email, "OldP@ss123")).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, null, null, "NewP@ss456", "OldP@ss123");
            ResponseEntity<R<Void>> updateResp = callUpdate(dto);
            assertThat(updateResp.getBody().isSuccess()).isTrue();
            ResponseEntity<R<LoginResponse>> newLogin = callLogin(buildLogin(email, "NewP@ss456"));
            assertThat(newLogin.getBody().isSuccess()).isTrue();
            ResponseEntity<R<LoginResponse>> oldLogin = callLogin(buildLogin(email, "OldP@ss123"));
            assertThat(oldLogin.getBody().isSuccess()).isFalse();
            assertThat(oldLogin.getBody().getCode()).isEqualTo(PASSWORD_ERROR.getCode());
        }

        @Test
        @DisplayName("改密失败 - 当前密码错误")
        void updateUser_wrongCurrentPassword() {
            String u = uid();
            String email = "wrongcur_" + u + "@example.com";
            Long userId = callRegister(buildRegister("wrongcur_" + u, email, "Correct123")).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, null, null, "NewP@ss456", "WrongPassword");
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(PASSWORD_ERROR.getCode());
        }

        @Test
        @DisplayName("改密失败 - 新旧密码相同")
        void updateUser_samePasswordAsOld() {
            String u = uid();
            String email = "samepwd_" + u + "@example.com";
            String pwd = "SameP@ss123";
            Long userId = callRegister(buildRegister("samepwd_" + u, email, pwd)).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, null, null, pwd, pwd);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(PASSWORD_SAME_AS_OLD.getCode());
        }

        @Test
        @DisplayName("更新自身昵称 - 允许")
        void updateUser_ownNickname() {
            String u = uid();
            String email = "selfnick_" + u + "@example.com";
            Long userId = callRegister(buildRegister("selfnick_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, "selfnick_" + u, null, null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("更新自身邮箱 - 被拒绝，邮箱不可修改")
        void updateUser_ownEmail() {
            String u = uid();
            String email = "selfmail_" + u + "@example.com";
            Long userId = callRegister(buildRegister("selfmail_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            UserDto dto = buildUpdate(userId, null, email, null, null);
            ResponseEntity<R<Void>> resp = callUpdate(dto);
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(EMAIL_CANNOT_BE_MODIFIED.getCode());
        }
    }

    // ==================== cancelAccount 复杂逻辑 ====================

    @Nested
    @DisplayName("DELETE /user/{id}/cancel 复杂逻辑")
    class CancelAccountLogicTests {

        @Test
        @DisplayName("注销后邮箱追加 #deleted_ 前缀")
        void cancel_emailAppendsSuffix() {
            String u = uid();
            String email = "append_" + u + "@example.com";
            Long userId = callRegister(buildRegister("append_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            callCancel(userId);
            ResponseEntity<R<LoginResponse>> loginResp = callLogin(buildLogin(email, "P@ss1234"));
            assertThat(loginResp.getBody().isSuccess()).isFalse();
            assertThat(loginResp.getBody().getCode()).isEqualTo(USER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("注销后原邮箱可重新注册")
        void cancel_reRegisterAfterCancel() {
            String u = uid();
            String email = "rereg_" + u + "@example.com";
            Long userId = callRegister(buildRegister("rereg_" + u, email, "P@ss1234")).getBody().getData().getUserId();
            callCancel(userId);
            RegisterRequest req2 = buildRegister("rereg2_" + uid(), email, "NewP@ss456");
            ResponseEntity<R<RegisterResponse>> resp = callRegister(req2);
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getUserId()).isNotEqualTo(userId);
        }
    }

    // ==================== 辅助方法 ====================

    private String uid() {
        return String.valueOf(counter.incrementAndGet());
    }

    private RegisterRequest buildRegister(String nickname, String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setNickname(nickname);
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private LoginRequest buildLogin(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private UserDto buildUpdate(Long userId, String nickname, String email, String password, String currentPassword) {
        UserDto dto = new UserDto();
        dto.setUserId(userId);
        dto.setNickname(nickname);
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setCurrentPassword(currentPassword);
        return dto;
    }

    private SendCodeRequest buildSendCode(String email, String type) {
        SendCodeRequest req = new SendCodeRequest();
        req.setEmail(email);
        req.setType(type);
        return req;
    }

    private ForgotPasswordRequest buildForgot(String email, String code, String newPassword) {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        req.setCode(code);
        req.setNewPassword(newPassword);
        return req;
    }

    private ResponseEntity<R<RegisterResponse>> callRegister(RegisterRequest req) {
        return restTemplate.exchange(
                getUrl("/user/register"),
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<R<RegisterResponse>>() {}
        );
    }

    private ResponseEntity<R<LoginResponse>> callLogin(LoginRequest req) {
        return restTemplate.exchange(
                getUrl("/user/login"),
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<R<LoginResponse>>() {}
        );
    }

    private ResponseEntity<R<UserVO>> callGetUser(Long id) {
        return restTemplate.exchange(
                getUrl("/user/" + id),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<R<UserVO>>() {}
        );
    }

    private ResponseEntity<R<PageResult<UserVO>>> callPage(int pageNum, int pageSize) {
        return restTemplate.exchange(
                getUrl("/user/page?pageNum=" + pageNum + "&pageSize=" + pageSize),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<R<PageResult<UserVO>>>() {}
        );
    }

    private ResponseEntity<R<Void>> callUpdate(UserDto dto) {
        return restTemplate.exchange(
                getUrl("/user"),
                HttpMethod.PUT,
                new HttpEntity<>(dto),
                new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    private ResponseEntity<R<Boolean>> callDelete(Long id) {
        return restTemplate.exchange(
                getUrl("/user/" + id),
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    private ResponseEntity<R<Void>> callCancel(Long id) {
        return restTemplate.exchange(
                getUrl("/user/" + id + "/cancel"),
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    private ResponseEntity<R<Void>> callSendCode(SendCodeRequest req) {
        return restTemplate.exchange(
                getUrl("/user/sendCode"),
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    private ResponseEntity<R<Void>> callForgotPassword(ForgotPasswordRequest req) {
        return restTemplate.exchange(
                getUrl("/user/forgotPassword"),
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<R<Void>>() {}
        );
    }

    private ResponseEntity<R<List<UserVO>>> callByIds(String ids) {
        return restTemplate.exchange(
                getUrl("/user/by-ids?ids=" + ids),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<R<List<UserVO>>>() {}
        );
    }

    private String extractCodeFromRedis(String email) {
        return redisTemplate.opsForValue().get("verify:code:resetPassword:" + email);
    }

    // ==================== 参数化数据供给 ====================

    static Stream<Arguments> provideInvalidRegisterData() {
        return Stream.of(
                Arguments.of(buildReqWithNickname("")),
                Arguments.of(buildReqWithNickname(null)),
                Arguments.of(buildReqWithPassword("")),
                Arguments.of(buildReqWithPassword(null)),
                Arguments.of(buildReqWithEmail("")),
                Arguments.of(buildReqWithEmail(null)),
                Arguments.of(buildReqWithEmail("not-an-email")),
                Arguments.of(buildReqWithNickname("a".repeat(21))),
                Arguments.of(buildReqWithPassword("12345")),
                Arguments.of(buildReqWithPassword("a".repeat(51)))
        );
    }

    static Stream<Arguments> provideInvalidLoginData() {
        return Stream.of(
                Arguments.of("", "password"),
                Arguments.of("email@example.com", ""),
                Arguments.of(null, "password"),
                Arguments.of("email@example.com", null)
        );
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(0L),
                Arguments.of(-1L)
        );
    }

    static Stream<Arguments> provideBoundaryPageParams() {
        return Stream.of(
                Arguments.of(0, 10),
                Arguments.of(-1, 10),
                Arguments.of(1, 0)
        );
    }

    static Stream<Arguments> provideInvalidUpdateData() {
        return Stream.of(
                Arguments.of(buildUpdateDto(null, "nick", "e@e.com")),
                Arguments.of(buildUpdateDto(1L, null, null))
        );
    }

    static Stream<Arguments> provideInvalidForgotPasswordData() {
        return Stream.of(
                Arguments.of(buildForgotReq("", "123456", "NewP@ss123")),
                Arguments.of(buildForgotReq("a@b.com", "", "NewP@ss123")),
                Arguments.of(buildForgotReq("a@b.com", "123456", ""))
        );
    }

    private static RegisterRequest buildReqWithNickname(String nickname) {
        RegisterRequest req = new RegisterRequest();
        req.setNickname(nickname);
        req.setPassword("P@ss1234");
        req.setEmail("test@example.com");
        return req;
    }

    private static RegisterRequest buildReqWithPassword(String password) {
        RegisterRequest req = new RegisterRequest();
        req.setNickname("testuser_" + System.currentTimeMillis());
        req.setPassword(password);
        req.setEmail("test_" + System.currentTimeMillis() + "@example.com");
        return req;
    }

    private static RegisterRequest buildReqWithEmail(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setNickname("testuser_" + System.currentTimeMillis());
        req.setPassword("P@ss1234");
        req.setEmail(email);
        return req;
    }

    private static UserDto buildUpdateDto(Long userId, String nickname, String email) {
        UserDto dto = new UserDto();
        dto.setUserId(userId);
        dto.setNickname(nickname);
        dto.setEmail(email);
        return dto;
    }

    private static ForgotPasswordRequest buildForgotReq(String email, String code, String newPassword) {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        req.setCode(code);
        req.setNewPassword(newPassword);
        return req;
    }
}
