package com.gopair.userservice.service;

import com.gopair.userservice.base.BaseIntegrationTest;
import com.gopair.userservice.base.TestMailConfig;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.service.StubEmailServiceImpl;
import com.gopair.userservice.domain.dto.auth.ForgotPasswordRequest;
import com.gopair.userservice.domain.dto.auth.LoginRequest;
import com.gopair.userservice.domain.dto.auth.RegisterRequest;
import com.gopair.userservice.domain.dto.auth.SendCodeRequest;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.domain.vo.auth.LoginResponse;
import com.gopair.userservice.domain.vo.auth.RegisterResponse;
import com.gopair.userservice.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserService 全链路集成测试。
 *
 * * [智能合并后的两条测试流]
 *
 * * 主干流 A - 用户全生命周期（注册 → 登录 → 更新含改密 → 注销）
 *   - 涉及 MySQL 写（register/update/cancel）+ MySQL 读（login/getUserById）
 *   - 无 Redis 操作（@example.com 跳过验证码）
 *   - 无 WebSocket
 *
 * * 分支流 B - 密码重置链路（发送验证码 → 重置密码 → 新密码登录）
 *   - 涉及 Redis 写（verify:limit + verify:code）/ Redis 读+删（verifyCode 消费）
 *   - 涉及 MySQL 读（forgotPassword 查询用户）+ MySQL 写（密码更新）
 *   - 无 WebSocket
 *
 * * [脏数据清理]
 * - MySQL：由 @Transactional 注解自动回滚
 * - Redis：测试结束后通过 redisTemplate.delete() 清理所有 verify:limit:*:和新产生的 key
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.context.annotation.Import({TestMailConfig.class, StubEmailServiceImpl.class})
@Slf4j
class UserServiceLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 跟踪测试过程中写入的 Redis key，便于 @AfterEach 清理 */
    private final List<String> createdRedisKeys = new ArrayList<>();

    // ==================== 主干流 A：用户全生命周期 ====================

    @Nested
    @DisplayName("主干流 A - 用户全生命周期测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UserLifecycleFlowATest {

        @Test
        @Order(1)
        @DisplayName("Step 1: 用户注册（@example.com 跳过验证码）")
        void step1_register_success() {
            RegisterRequest request = buildRegisterRequest("alice", "alice_" + ts() + "@example.com", "P@ss123456");
            RegisterResponse response = userService.register(request);

            log.info("==== [step1_register] 注册响应 ====");
            log.info("userId={}, nickname={}, email={}", response.getUserId(), response.getNickname(), response.getEmail());
            log.info("实体 JSON: {}", toJson(response));

            assertThat(response.getUserId()).isNotNull();
            assertThat(response.getNickname()).isEqualTo(request.getNickname());
            assertThat(response.getEmail()).isEqualTo(request.getEmail());
            assertThat(response.getMessage()).isEqualTo("注册成功");

            User dbUser = userMapper.selectById(response.getUserId());
            log.info("==== [step1_register] MySQL 状态校验 ====");
            log.info("DB userId={}, nickname={}, email={}, status={}, createTime={}",
                    dbUser.getUserId(), dbUser.getNickname(), dbUser.getEmail(),
                    dbUser.getStatus(), dbUser.getCreateTime());
            assertThat(dbUser).isNotNull();
            assertThat(dbUser.getStatus()).isEqualTo('0');
            assertThat(dbUser.getPassword()).isNotEqualTo("P@ss123456");
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: 用户登录（验证 Token 生成 + NORMAL 状态）")
        void step2_login_success() {
            RegisterRequest registerReq = buildRegisterRequest("bob", "bob_" + ts() + "@example.com", "OriginalPwd1");
            userService.register(registerReq);

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail(registerReq.getEmail());
            loginRequest.setPassword(registerReq.getPassword());
            LoginResponse loginResponse = userService.login(loginRequest);

            log.info("==== [step2_login] 登录响应 ====");
            log.info("userId={}, nickname={}, token={}, email={}",
                    loginResponse.getUserId(), loginResponse.getNickname(),
                    loginResponse.getToken(), loginResponse.getEmail());
            log.info("响应 JSON: {}", toJson(loginResponse));

            assertThat(loginResponse.getUserId()).isNotNull();
            assertThat(loginResponse.getNickname()).isEqualTo(registerReq.getNickname());
            assertThat(loginResponse.getToken()).isNotBlank();
            assertThat(loginResponse.getToken()).contains(".");
            assertThat(loginResponse.getEmail()).isEqualTo(registerReq.getEmail());

            User dbUser = userMapper.selectById(loginResponse.getUserId());
            log.info("==== [step2_login] MySQL 状态校验 ====");
            log.info("DB status={}, nickname={}", dbUser.getStatus(), dbUser.getNickname());
            assertThat(dbUser.getStatus()).isEqualTo('0');

            Long userId = loginResponse.getUserId();
            UserVO vo = userService.getUserById(userId);
            log.info("==== [step2_login] getUserById 校验 ====");
            log.info("VO: userId={}, nickname={}, status={}", vo.getUserId(), vo.getNickname(), vo.getStatus());
            assertThat(vo.getUserId()).isEqualTo(userId);
            assertThat(vo.getNickname()).isEqualTo(registerReq.getNickname());
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: 更新用户信息（含修改密码 - 三步校验）")
        void step3_updateUser_withPasswordChange_success() {
            RegisterRequest registerReq = buildRegisterRequest("charlie", "charlie_" + ts() + "@example.com", "OldP@ss123");
            userService.register(registerReq);

            LoginResponse loginResp = userService.login(buildLoginRequest(registerReq.getEmail(), registerReq.getPassword()));
            Long userId = loginResp.getUserId();
            String newNickname = "charlie_updated_" + ts();
            String newEmail = "charlie_new_" + ts() + "@example.com";
            String newPwd = "NewP@ss456";

            UserDto updateDto = new UserDto();
            updateDto.setUserId(userId);
            updateDto.setNickname(newNickname);
            updateDto.setEmail(newEmail);
            updateDto.setPassword(newPwd);
            updateDto.setCurrentPassword("OldP@ss123");

            boolean updated = userService.updateUser(updateDto);

            log.info("==== [step3_updateUser] 更新结果 ====");
            log.info("updateUser 返回={}", updated);

            assertThat(updated).isTrue();

            UserVO dbUserVO = userService.getUserById(userId);
            log.info("==== [step3_updateUser] MySQL 状态校验 ====");
            log.info("DB userId={}, nickname={}, email={}, status={}",
                    dbUserVO.getUserId(), dbUserVO.getNickname(), dbUserVO.getEmail(), dbUserVO.getStatus());
            assertThat(dbUserVO.getNickname()).isEqualTo(newNickname);
            assertThat(dbUserVO.getEmail()).isEqualTo(newEmail);

            LoginResponse afterUpdateLogin = userService.login(buildLoginRequest(newEmail, newPwd));
            log.info("==== [step3_updateUser] 新密码登录验证 ====");
            log.info("新密码登录成功: userId={}, token={}", afterUpdateLogin.getUserId(), afterUpdateLogin.getToken());
            assertThat(afterUpdateLogin.getUserId()).isEqualTo(userId);
            assertThat(afterUpdateLogin.getToken()).isNotBlank();

            assertThatThrownBy(() -> userService.login(buildLoginRequest(newEmail, "OldP@ss123")))
                    .hasMessageContaining("密码错误");
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: 注销账号（软删除 + 邮箱追加标记释放）")
        void step4_cancelAccount_success() {
            RegisterRequest registerReq = buildRegisterRequest("dave", "dave_" + ts() + "@example.com", "P@ss123456");
            userService.register(registerReq);

            LoginResponse loginResp = userService.login(buildLoginRequest(registerReq.getEmail(), registerReq.getPassword()));
            Long userId = loginResp.getUserId();
            String originalEmail = registerReq.getEmail();

            userService.cancelAccount(userId);

            User dbUser = userMapper.selectById(userId);
            log.info("==== [step4_cancelAccount] MySQL 状态校验 ====");
            log.info("DB userId={}, status={}, email={}", dbUser.getUserId(), dbUser.getStatus(), dbUser.getEmail());
            assertThat(dbUser.getStatus()).isEqualTo('2');
            assertThat(dbUser.getEmail()).startsWith(originalEmail + "#deleted_");

            // 注销后原邮箱已追加标记，login 查询时 email 不匹配，抛出"用户不存在"
            assertThatThrownBy(() -> userService.login(buildLoginRequest(originalEmail, "P@ss123456")))
                    .hasMessageContaining("不存在");

            assertThatThrownBy(() -> userService.cancelAccount(userId))
                    .hasMessageContaining("已注销");

            log.info("==== [step4_cancelAccount] 邮箱释放验证 ====");
            log.info("原邮箱={}, 注销后邮箱={}", originalEmail, dbUser.getEmail());
        }

        @Test
        @Order(5)
        @DisplayName("Step 5: 账号注销后原邮箱可重新注册")
        void step5_reRegisterAfterCancel_success() {
            String email = "eve_" + ts() + "@example.com";
            RegisterRequest req1 = buildRegisterRequest("eve_first", email, "P@ss123456");
            RegisterResponse reg1 = userService.register(req1);
            userService.cancelAccount(reg1.getUserId());

            RegisterRequest req2 = buildRegisterRequest("eve_second", email, "NewP@ss456");
            RegisterResponse reg2 = userService.register(req2);

            log.info("==== [step5_reRegister] 邮箱释放再注册 ====");
            log.info("第一次注册 userId={}, 注销后第二次注册 userId={}", reg1.getUserId(), reg2.getUserId());
            log.info("第二次注册响应: {}", toJson(reg2));
            assertThat(reg2.getUserId()).isNotEqualTo(reg1.getUserId());
            assertThat(reg2.getEmail()).isEqualTo(email);

            User dbUser = userMapper.selectById(reg2.getUserId());
            log.info("==== [step5_reRegister] MySQL 校验 ====");
            log.info("DB userId={}, nickname={}, email={}, status={}",
                    dbUser.getUserId(), dbUser.getNickname(), dbUser.getEmail(), dbUser.getStatus());
            assertThat(dbUser.getStatus()).isEqualTo('0');
        }
    }

    // ==================== 分支流 B：密码重置链路 ====================

    @Nested
    @DisplayName("分支流 B - 密码重置全链路测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PasswordResetFlowBTest {

        @Test
        @Order(1)
        @DisplayName("Step 1: 注册用户（为忘记密码测试准备账号）")
        void step1_registerForResetTest() {
            RegisterRequest req = buildRegisterRequest("forgot_test", "forgot_" + ts() + "@example.com", "OldP@ss123");
            RegisterResponse resp = userService.register(req);
            log.info("==== [flowB_step1] 忘记密码测试账号准备 ====");
            log.info("userId={}, email={}", resp.getUserId(), resp.getEmail());
            assertThat(resp.getUserId()).isNotNull();
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: 发送重置密码验证码（邮件发送失败被服务层吞掉，Redis 写入不受影响）")
        void step2_sendResetCode_success() {
            String email = "forgot_" + ts() + "@example.com";
            RegisterRequest req = buildRegisterRequest("forgot_send", email, "P@ss123");
            userService.register(req);

            SendCodeRequest codeReq = new SendCodeRequest();
            codeReq.setEmail(email);
            codeReq.setType("resetPassword");
            userService.sendVerificationCode(codeReq);

            String limitKey = "verify:limit:resetPassword:" + email;
            String codeKey = "verify:code:resetPassword:" + email;

            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            String limitVal = ops.get(limitKey);
            String codeVal = ops.get(codeKey);

            log.info("==== [flowB_step2] Redis 状态校验 ====");
            log.info("limitKey={}, limitVal={}", limitKey, limitVal);
            log.info("codeKey={}, codeVal={}（6位数字验证码）", codeKey, codeVal);

            assertThat(limitVal).isEqualTo("1");
            assertThat(codeVal).isNotNull();
            assertThat(codeVal).matches("\\d{6}");

            createdRedisKeys.add(limitKey);
            createdRedisKeys.add(codeKey);
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: 验证码错误应拒绝重置")
        void step3_wrongCode_shouldReject() {
            String email = "forgot_wrong_" + ts() + "@example.com";
            RegisterRequest req = buildRegisterRequest("forgot_wrong", email, "P@ss123");
            userService.register(req);

            ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
            forgotReq.setEmail(email);
            forgotReq.setCode("000000");
            forgotReq.setNewPassword("NewP@ss456");

            assertThatThrownBy(() -> userService.forgotPassword(forgotReq))
                    .hasMessageContaining("验证码");

            log.info("==== [flowB_step3] 错误验证码校验 ====");
            log.info("错误验证码已正确拒绝重置密码请求");
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: 正确的验证码重置密码 → 新密码登录成功")
        void step4_resetPassword_success() {
            String email = "forgot_reset_" + ts() + "@example.com";
            String originalPwd = "OriginalPwd1!";
            String newPwd = "NewResetPwd2@";

            RegisterRequest req = buildRegisterRequest("forgot_reset", email, originalPwd);
            userService.register(req);

            SendCodeRequest sendReq = new SendCodeRequest();
            sendReq.setEmail(email);
            sendReq.setType("resetPassword");
            userService.sendVerificationCode(sendReq);

            String codeKey = "verify:code:resetPassword:" + email;
            String realCode = redisTemplate.opsForValue().get(codeKey);
            createdRedisKeys.add(codeKey);
            createdRedisKeys.add("verify:limit:resetPassword:" + email);

            log.info("==== [flowB_step4] Redis 验证码消费前 ====");
            log.info("codeKey={}, realCode={}", codeKey, realCode);
            assertThat(realCode).isNotNull();

            ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
            forgotReq.setEmail(email);
            forgotReq.setCode(realCode);
            forgotReq.setNewPassword(newPwd);
            userService.forgotPassword(forgotReq);

            log.info("==== [flowB_step4] 密码重置完成 ====");
            log.info("email={}, 新密码={}", email, newPwd);

            User dbUser = userMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email)
            ).stream().findFirst().orElseThrow();
            log.info("==== [flowB_step4] MySQL 密码更新校验 ====");
            log.info("DB userId={}, email={}, status={}, passwordHash长度={}",
                    dbUser.getUserId(), dbUser.getEmail(), dbUser.getStatus(),
                    dbUser.getPassword() != null ? dbUser.getPassword().length() : 0);

            assertThat(dbUser.getPassword()).isNotEqualTo(originalPwd);

            assertThatThrownBy(() -> userService.login(buildLoginRequest(email, originalPwd)))
                    .hasMessageContaining("密码错误");

            LoginResponse newLogin = userService.login(buildLoginRequest(email, newPwd));
            log.info("==== [flowB_step4] 新密码登录验证 ====");
            log.info("新密码登录成功: userId={}, nickname={}, token={}",
                    newLogin.getUserId(), newLogin.getNickname(), newLogin.getToken());
            assertThat(newLogin.getUserId()).isNotNull();
            assertThat(newLogin.getToken()).isNotBlank();
        }
    }

    // ==================== 边界与异常测试 ====================

    @Nested
    @DisplayName("边界与异常场景测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BoundaryAndExceptionTests {

        @Test
        @Order(1)
        @DisplayName("重复注册相同昵称应被拒绝")
        void duplicateNickname_shouldReject() {
            String email1 = "dupnick_" + ts() + "@example.com";
            String email2 = "dupnick2_" + ts() + "@example.com";
            RegisterRequest req1 = buildRegisterRequest("dup_nick_user", email1, "P@ss123456");
            RegisterRequest req2 = buildRegisterRequest("dup_nick_user", email2, "P@ss123456");

            userService.register(req1);

            assertThatThrownBy(() -> userService.register(req2))
                    .hasMessageContaining("昵称");

            log.info("==== [duplicateNickname] 昵称冲突校验 ====");
            log.info("昵称 'dup_nick_user' 已被占用，重复注册已正确拒绝");
        }

        @Test
        @Order(2)
        @DisplayName("重复注册相同邮箱应被拒绝")
        void duplicateEmail_shouldReject() {
            String email = "dupemail_" + ts() + "@example.com";
            RegisterRequest req1 = buildRegisterRequest("dup_email_u1", email, "P@ss123456");
            RegisterRequest req2 = buildRegisterRequest("dup_email_u2", email, "P@ss123456");

            userService.register(req1);

            assertThatThrownBy(() -> userService.register(req2))
                    .hasMessageContaining("邮箱");

            log.info("==== [duplicateEmail] 邮箱冲突校验 ====");
            log.info("邮箱 '{}' 已被占用，重复注册已正确拒绝", email);
        }

        @Test
        @Order(3)
        @DisplayName("修改密码时新旧密码相同应被拒绝")
        void samePassword_shouldReject() {
            String email = "samepwd_" + ts() + "@example.com";
            String pwd = "SameP@ss123";
            RegisterRequest req = buildRegisterRequest("samepwd_user", email, pwd);
            userService.register(req);

            LoginResponse loginResp = userService.login(buildLoginRequest(email, pwd));

            UserDto updateDto = new UserDto();
            updateDto.setUserId(loginResp.getUserId());
            updateDto.setPassword(pwd);
            updateDto.setCurrentPassword(pwd);

            assertThatThrownBy(() -> userService.updateUser(updateDto))
                    .hasMessageContaining("相同");

            log.info("==== [samePassword] 新旧密码相同校验 ====");
            log.info("新密码与旧密码相同，已正确拒绝更新");
        }

        @Test
        @Order(4)
        @DisplayName("不存在的用户查询应抛出异常")
        void userNotFound_shouldThrow() {
            assertThatThrownBy(() -> userService.getUserById(999999L))
                    .hasMessageContaining("不存在");

            log.info("==== [userNotFound] 查询不存在用户 ====");
            log.info("查询不存在的 userId=999999L 已正确抛出异常");
        }

        @Test
        @Order(5)
        @DisplayName("批量查询用户（混合存在/不存在 ID）")
        void listUsersByIds_mixedIds_shouldReturnOnlyExisting() {
            RegisterRequest req = buildRegisterRequest("batch_user", "batch_" + ts() + "@example.com", "P@ss123456");
            RegisterResponse resp = userService.register(req);

            List<Long> ids = new ArrayList<>();
            ids.add(resp.getUserId());
            ids.add(999998L);
            ids.add(999999L);
            ids.add(null);
            List<UserVO> result = userService.listUsersByIds(ids);

            log.info("==== [listUsersByIds] 批量查询 ====");
            log.info("查询 ID 列表=[{}, 999998, 999999, null], 实际返回={} 条", resp.getUserId(), result.size());
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(resp.getUserId());
        }
    }

    // ==================== 脏数据清理 ====================

    @AfterEach
    void cleanUpRedis() {
        if (!createdRedisKeys.isEmpty()) {
            Set<String> keysToDelete = redisTemplate.keys("verify:*");
            if (keysToDelete != null && !keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("==== [脏数据清理] Redis verify:* keys 已删除 count={} ====", keysToDelete.size());
            }
            createdRedisKeys.clear();
        }
    }

    // ==================== 辅助方法 ====================

    private RegisterRequest buildRegisterRequest(String nickname, String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setNickname(nickname + "_" + ts());
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private String ts() {
        return String.valueOf(System.currentTimeMillis() % 10000000);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
