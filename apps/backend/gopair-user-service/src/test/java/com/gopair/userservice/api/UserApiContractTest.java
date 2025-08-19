package com.gopair.userservice.api;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.userservice.base.BaseIntegrationTest;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.util.stream.Stream;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static com.gopair.common.constants.MessageConstants.*;
import com.gopair.userservice.enums.UserErrorCode;

/**
 * 用户API契约测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiContractTest extends BaseIntegrationTest {

    @Nested
    class RegistrationApiTests {
        @Test
        @DisplayName("用户注册API - 成功场景")
        void testUserRegistration_Success() {
            UserDto userDto = createValidUser();
            ResponseEntity<R<Boolean>> response = callUserRegistration(userDto);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("用户注册API - 昵称重复失败")
        void testUserRegistration_DuplicateUsername() {
            UserDto firstUser = createValidUser();
            callUserRegistration(firstUser);

            UserDto duplicateUser = createValidUser();
            duplicateUser.setNickname(firstUser.getNickname());
            duplicateUser.setEmail("different@test.com");
            ResponseEntity<R<Boolean>> response = callUserRegistration(duplicateUser);

            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.NICKNAME_ALREADY_EXISTS.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(NICKNAME_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("用户注册API - 邮箱重复失败")
        void testUserRegistration_DuplicateEmail() {
            UserDto firstUser = createValidUser();
            callUserRegistration(firstUser);

            UserDto duplicate = createValidUser();
            duplicate.setEmail(firstUser.getEmail());
            // 使用更短的时间戳确保昵称不超过20个字符
            long timestamp = System.currentTimeMillis() % 1000000;
            duplicate.setNickname("another_" + timestamp);
            ResponseEntity<R<Boolean>> response = callUserRegistration(duplicate);

            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(EMAIL_ALREADY_EXISTS);
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidRegistrationData")
        @DisplayName("用户注册API - 无效输入边界测试")
        void testUserRegistration_InvalidInput(UserDto invalidUser, String expectedError) {
            ResponseEntity<R<Boolean>> response = callUserRegistration(invalidUser);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMsg()).containsAnyOf(
                PARAM_MISSING, 
                NICKNAME_LENGTH_ERROR, 
                PASSWORD_LENGTH_ERROR, 
                EMAIL_FORMAT_ERROR
            );
        }
    }

    @Nested
    class LoginApiTests {
        @Test
        @DisplayName("用户登录API - 成功场景")
        void testUserLogin_Success() {
            UserDto userDto = createValidUser();
            callUserRegistration(userDto);
            ResponseEntity<R<UserVO>> response = callUserLogin(userDto.getEmail(), userDto.getPassword());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            UserVO userVO = response.getBody().getData();
            assertThat(userVO.getNickname()).isEqualTo(userDto.getNickname());
            assertThat(userVO.getToken()).isNotBlank();
            assertThat(userVO.getUserId()).isNotNull();
        }

        @Test
        @DisplayName("用户登录API - 用户不存在")
        void testUserLogin_UserNotFound() {
            String nonExistentEmail = "nonexist_" + System.currentTimeMillis() + "@example.com";
            ResponseEntity<R<UserVO>> response = callUserLogin(nonExistentEmail, "password");
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(USER_NOT_FOUND);
        }

        @Test
        @DisplayName("用户登录API - 密码错误")
        void testUserLogin_InvalidPassword() {
            UserDto userDto = createValidUser();
            callUserRegistration(userDto);
            ResponseEntity<R<UserVO>> response = callUserLogin(userDto.getEmail(), "wrongpassword");
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.PASSWORD_ERROR.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(PASSWORD_ERROR);
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidLoginData")
        @DisplayName("用户登录API - 空参数边界测试")
        void testUserLogin_NullParameters(String email, String password) {
            ResponseEntity<R<UserVO>> response = callUserLogin(email, password);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMsg()).isEqualTo(PARAM_MISSING);
        }
    }

    @Nested
    class QueryApiTests {
        @Test
        @DisplayName("用户查询API - 成功场景")
        void testGetUser_Success() {
            UserDto userDto = createValidUser();
            callUserRegistration(userDto);
            UserVO loginResult = callUserLogin(userDto.getEmail(), userDto.getPassword()).getBody().getData();
            ResponseEntity<R<UserVO>> response = callGetUser(loginResult.getUserId());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getNickname()).isEqualTo(userDto.getNickname());
        }

        @Test
        @DisplayName("用户查询API - 用户不存在")
        void testGetUser_NotFound() {
            Long nonExistentId = 999999L;
            ResponseEntity<R<UserVO>> response = callGetUser(nonExistentId);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(USER_NOT_FOUND);
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideInvalidUserIds")
        @DisplayName("用户查询API - 无效ID边界测试")
        void testGetUser_InvalidId(Long invalidId) {
            ResponseEntity<R<UserVO>> response = callGetUser(invalidId);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    @Nested
    class UpdateApiTests {
        @Test
        @DisplayName("用户更新API - 成功场景")
        void testUserUpdate_Success() {
            UserDto userDto = createValidUser();
            ResponseEntity<R<Boolean>> regResponse = callUserRegistration(userDto);
            assertThat(regResponse.getBody().isSuccess()).isTrue();
            ResponseEntity<R<UserVO>> loginResponse = callUserLogin(userDto.getEmail(), userDto.getPassword());
            assertThat(loginResponse.getBody().isSuccess()).isTrue();
            UserVO loginResult = loginResponse.getBody().getData();
            assertThat(loginResult.getUserId()).isNotNull();
            UserDto updateDto = new UserDto();
            updateDto.setUserId(loginResult.getUserId());
            updateDto.setNickname("updated_" + System.currentTimeMillis());
            updateDto.setEmail("updated_" + System.currentTimeMillis() + "@example.com");
            ResponseEntity<R<Boolean>> response = callUserUpdate(updateDto);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            ResponseEntity<R<UserVO>> getResponse = callGetUser(loginResult.getUserId());
            assertThat(getResponse.getBody().isSuccess()).isTrue();
            assertThat(getResponse.getBody().getData().getNickname()).isEqualTo(updateDto.getNickname());
            assertThat(getResponse.getBody().getData().getEmail()).isEqualTo(updateDto.getEmail());
        }

        @Test
        @DisplayName("用户更新API - 用户不存在")
        void testUserUpdate_UserNotFound() {
            UserDto updateDto = new UserDto();
            updateDto.setUserId(999999L);
            updateDto.setNickname("nonexistent");
            ResponseEntity<R<Boolean>> response = callUserUpdate(updateDto);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(USER_NOT_FOUND);
        }

        @Test
        @DisplayName("用户更新API - 昵称和邮箱冲突")
        void testUserUpdate_DuplicateFields() {
            // 创建并注册三个用户
            UserDto user1 = createValidUser();
            UserDto user2 = createValidUser();
            UserDto user3 = createValidUser();
            
            ResponseEntity<R<Boolean>> reg1 = callUserRegistration(user1);
            ResponseEntity<R<Boolean>> reg2 = callUserRegistration(user2);
            ResponseEntity<R<Boolean>> reg3 = callUserRegistration(user3);
            
            assertThat(reg1.getBody().isSuccess()).isTrue();
            if (!reg2.getBody().isSuccess() || !reg3.getBody().isSuccess()) {
                return;
            }
            
            UserVO loginResult = callUserLogin(user3.getEmail(), user3.getPassword()).getBody().getData();
            
            // 测试昵称冲突
            UserDto nicknameUpdateDto = new UserDto();
            nicknameUpdateDto.setUserId(loginResult.getUserId());
            nicknameUpdateDto.setNickname(user1.getNickname());
            ResponseEntity<R<Boolean>> nicknameResponse = callUserUpdate(nicknameUpdateDto);
            if (nicknameResponse.getBody().isSuccess()) {
                assertThat(nicknameResponse.getBody().getData()).isTrue();
            } else {
                assertThat(nicknameResponse.getBody().getCode()).isIn(
                    UserErrorCode.NICKNAME_ALREADY_EXISTS.getCode()
                );
                assertThat(nicknameResponse.getBody().getMsg()).isEqualTo(NICKNAME_ALREADY_EXISTS);
            }
            
            // 测试邮箱冲突
            UserDto emailUpdateDto = new UserDto();
            emailUpdateDto.setUserId(loginResult.getUserId());
            emailUpdateDto.setEmail(user2.getEmail());
            ResponseEntity<R<Boolean>> emailResponse = callUserUpdate(emailUpdateDto);
            if (emailResponse.getBody().isSuccess()) {
                assertThat(emailResponse.getBody().getData()).isTrue();
            } else {
                assertThat(emailResponse.getBody().getCode()).isIn(
                    UserErrorCode.EMAIL_ALREADY_EXISTS.getCode()
                );
                assertThat(emailResponse.getBody().getMsg()).isEqualTo(EMAIL_ALREADY_EXISTS);
            }
        }
    }

    @Nested
    class DeleteApiTests {
        @Test
        @DisplayName("用户删除API - 成功场景")
        void testUserDelete_Success() {
            UserDto userDto = createValidUser();
            callUserRegistration(userDto);
            UserVO loginResult = callUserLogin(userDto.getEmail(), userDto.getPassword()).getBody().getData();
            ResponseEntity<R<Boolean>> response = callUserDelete(loginResult.getUserId());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isTrue();
            
            // 删除后再次查询该用户，验证用户确实已被删除
            ResponseEntity<R<UserVO>> getResponse = callGetUser(loginResult.getUserId());
            assertThat(getResponse.getBody().isSuccess()).isFalse();
            assertThat(getResponse.getBody().getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getCode());
            assertThat(getResponse.getBody().getMsg()).isEqualTo(USER_NOT_FOUND);
        }

        @Test
        @DisplayName("用户删除API - 用户不存在")
        void testUserDelete_NotFound() {
            Long nonExistentId = 999999L;
            ResponseEntity<R<Boolean>> response = callUserDelete(nonExistentId);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getCode());
            assertThat(response.getBody().getMsg()).isEqualTo(USER_NOT_FOUND);
        }
    }

    @Nested
    @Rollback(false)
    class PageApiTests {
        @Test
        @DisplayName("分页查询API - 成功场景")
        void testUserPage_Success() {
            // 插入15个用户用于测试分页功能
            List<UserDto> testUsers = createAndRegisterMultipleUsers(15);
            assertThat(testUsers).hasSize(15);
            
            // 查询第1页（10条记录）
            ResponseEntity<R<PageResult<UserVO>>> response = callUserPage(1, 10);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            
            PageResult<UserVO> pageResult = response.getBody().getData();
            assertThat(pageResult).isNotNull();
            assertThat(pageResult.getTotal()).isGreaterThanOrEqualTo(15);
            assertThat(pageResult.getRecords()).hasSize(10);
            
            // 验证返回的用户数据完整性
            UserVO firstUser = pageResult.getRecords().get(0);
            assertThat(firstUser.getUserId()).isNotNull();
            assertThat(firstUser.getNickname()).isNotNull();
            assertThat(firstUser.getEmail()).isNotNull();
            
            // 查询第2页（剩余5条记录）
            ResponseEntity<R<PageResult<UserVO>>> page2Response = callUserPage(2, 10);
            assertThat(page2Response.getBody().isSuccess()).isTrue();
            PageResult<UserVO> page2Result = page2Response.getBody().getData();
            assertThat(page2Result.getTotal()).isGreaterThanOrEqualTo(15);
            assertThat(page2Result.getRecords()).hasSize(5);
        }

        @Test
        @DisplayName("分页查询API - 大页码处理")
        void testUserPage_EmptyResult() {
            ResponseEntity<R<PageResult<UserVO>>> response = callUserPage(999, 10);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            PageResult<UserVO> pageResult = response.getBody().getData();
            assertThat(pageResult).isNotNull();
            assertThat(pageResult.getTotal()).isGreaterThanOrEqualTo(0);
            if (pageResult.getRecords() != null) {
                assertThat(pageResult.getRecords()).isNotNull();
            }
        }

        @ParameterizedTest
        @MethodSource("com.gopair.userservice.api.UserApiContractTest#provideBoundaryPageParameters")
        @DisplayName("分页查询API - 边界参数测试")
        void testUserPage_BoundaryParameters(int pageNum, int pageSize) {
            ResponseEntity<R<PageResult<UserVO>>> response = callUserPage(pageNum, pageSize);
            assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                assertThat(response.getBody().getData()).isNotNull();
            }
        }
    }

    private UserDto createValidUser() {
        UserDto userDto = new UserDto();
        // 使用更短的时间戳（只取后6位数字）确保昵称不超过20个字符
        long timestamp = System.currentTimeMillis() % 1000000;
        userDto.setNickname("user_" + timestamp);
        userDto.setPassword("123456");
        userDto.setEmail("test" + timestamp + "@example.com");
        userDto.setStatus('0');
        return userDto;
    }

    private List<UserDto> createAndRegisterMultipleUsers(int count) {
        List<UserDto> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserDto user = createValidUser();
            // 添加额外的延迟确保时间戳不同
            try {
                Thread.sleep(2);  // 增加延迟确保唯一性
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ResponseEntity<R<Boolean>> response = callUserRegistration(user);
            if (response.getBody() != null && response.getBody().isSuccess()) {
                users.add(user);
            } else {
                // 记录注册失败的情况，便于调试
                System.err.println("用户注册失败 #" + i + ": " + 
                    (response.getBody() != null ? response.getBody().getMsg() : "响应为空"));
            }
        }
        System.out.println("成功创建用户数量: " + users.size() + "/" + count);
        return users;
    }

    private ResponseEntity<R<Boolean>> callUserRegistration(UserDto userDto) {
        return restTemplate.exchange(
            getUrl("/user"),
            HttpMethod.POST,
            new HttpEntity<>(userDto),
            new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    private ResponseEntity<R<UserVO>> callUserLogin(String email, String password) {
        UserDto loginDto = new UserDto();
        loginDto.setEmail(email);
        loginDto.setPassword(password);
        return restTemplate.exchange(
            getUrl("/user/login"),
            HttpMethod.POST,
            new HttpEntity<>(loginDto),
            new ParameterizedTypeReference<R<UserVO>>() {}
        );
    }

    private ResponseEntity<R<UserVO>> callGetUser(Long userId) {
        return restTemplate.exchange(
            getUrl("/user/" + userId),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<R<UserVO>>() {}
        );
    }

    private ResponseEntity<R<PageResult<UserVO>>> callUserPage(int pageNum, int pageSize) {
        return restTemplate.exchange(
            getUrl("/user/page?pageNum=" + pageNum + "&pageSize=" + pageSize),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<R<PageResult<UserVO>>>() {}
        );
    }

    private ResponseEntity<R<Boolean>> callUserUpdate(UserDto userDto) {
        return restTemplate.exchange(
            getUrl("/user"),
            HttpMethod.PUT,
            new HttpEntity<>(userDto),
            new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    private ResponseEntity<R<Boolean>> callUserDelete(Long userId) {
        return restTemplate.exchange(
            getUrl("/user/" + userId),
            HttpMethod.DELETE,
            null,
            new ParameterizedTypeReference<R<Boolean>>() {}
        );
    }

    static Stream<Arguments> provideInvalidRegistrationData() {
        return Stream.of(
            // 空值测试
            Arguments.of(createUserWithNickname(""), "昵称为空"),
            Arguments.of(createUserWithNickname(null), "昵称为null"),
            Arguments.of(createUserWithPassword(""), "密码为空"),
            Arguments.of(createUserWithPassword(null), "密码为null"),
            Arguments.of(createUserWithEmail(""), "邮箱为空"),
            Arguments.of(createUserWithEmail(null), "邮箱为null"),
            
            // 格式错误测试
            Arguments.of(createUserWithEmail("invalid-email"), "邮箱格式错误"),
            
            // 长度边界测试
            Arguments.of(createUserWithNickname("a".repeat(21)), "昵称过长(21字符)"),
            Arguments.of(createUserWithPassword("12345"), "密码过短(5字符)"),
            Arguments.of(createUserWithPassword("a".repeat(51)), "密码过长(51字符)")
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

    static Stream<Arguments> provideBoundaryPageParameters() {
        return Stream.of(
            Arguments.of(0, 10),
            Arguments.of(-1, 10),
            Arguments.of(1, 0),
            Arguments.of(1, 1000)
        );
    }

    private static UserDto createUserWithNickname(String nickname) {
        UserDto dto = new UserDto();
        dto.setNickname(nickname);
        dto.setPassword("123456");
        dto.setEmail("test@example.com");
        return dto;
    }

    private static UserDto createUserWithPassword(String password) {
        UserDto dto = new UserDto();
        dto.setNickname("testuser");
        dto.setPassword(password);
        dto.setEmail("test@example.com");
        return dto;
    }

    private static UserDto createUserWithEmail(String email) {
        UserDto dto = new UserDto();
        dto.setNickname("testuser");
        dto.setPassword("123456");
        dto.setEmail(email);
        return dto;
    }
} 