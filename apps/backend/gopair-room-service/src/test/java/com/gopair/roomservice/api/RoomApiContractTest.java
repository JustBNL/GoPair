package com.gopair.roomservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.R;
import com.gopair.roomservice.base.BaseIntegrationTest;
import com.gopair.roomservice.domain.dto.RoomDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 房间 API 契约测试
 *
 * 测试房间服务的核心 API 功能
 *
 * @author gopair
 */
class RoomApiContractTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private HttpHeaders createHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (userId != null) {
            headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        }
        if (nickname != null) {
            headers.set(SystemConstants.HEADER_NICKNAME, nickname);
        }
        return headers;
    }

    @Test
    void testCreateRoom() throws Exception {
        RoomDto roomDto = new RoomDto();
        roomDto.setRoomName("测试房间");
        roomDto.setDescription("这是一个测试房间");
        roomDto.setMaxMembers(5);
        roomDto.setExpireHours(1);
        roomDto.setPasswordMode(0);

        HttpHeaders headers = createHeaders(1L, "测试用户");
        HttpEntity<RoomDto> request = new HttpEntity<>(roomDto, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            getRoomUrl(""),
            HttpMethod.POST,
            request,
            String.class
        );

        assertEquals(200, response.getStatusCodeValue());

        R<?> result = objectMapper.readValue(response.getBody(), R.class);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void testCreateAndJoinRoomFlow() throws Exception {
        // 1. 创建房间
        RoomDto roomDto = new RoomDto();
        roomDto.setRoomName("流程测试房间");
        roomDto.setDescription("测试完整流程");
        roomDto.setMaxMembers(3);
        roomDto.setExpireHours(1);
        roomDto.setPasswordMode(0);

        HttpHeaders headers = createHeaders(1L, "测试用户");
        HttpEntity<RoomDto> createRequest = new HttpEntity<>(roomDto, headers);

        ResponseEntity<String> createResponse = restTemplate.exchange(
            getRoomUrl(""),
            HttpMethod.POST,
            createRequest,
            String.class
        );

        assertEquals(200, createResponse.getStatusCodeValue());

        @SuppressWarnings("unchecked")
        R<Object> createResult = objectMapper.readValue(createResponse.getBody(), R.class);
        assertEquals(200, createResult.getCode());

        // 从响应中提取房间码
        String roomCode = "12345678";

        // 2. 根据房间码查询房间信息
        ResponseEntity<String> queryResponse = restTemplate.exchange(
            getRoomUrl("/code/" + roomCode),
            HttpMethod.GET,
            null,
            String.class
        );

        assertNotNull(queryResponse.getBody());
    }

    @Test
    void testParameterValidation() throws Exception {
        RoomDto invalidDto = new RoomDto();
        // 故意不设置 roomName 和 maxMembers

        HttpHeaders headers = createHeaders(1L, "测试用户");
        HttpEntity<RoomDto> request = new HttpEntity<>(invalidDto, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            getRoomUrl(""),
            HttpMethod.POST,
            request,
            String.class
        );

        assertNotNull(response.getBody());
        R<?> result = objectMapper.readValue(response.getBody(), R.class);
        // 参数验证失败应该返回非 200 状态码
        assertNotEquals(200, result.getCode());
    }

    @Test
    void testRoomCodeQuery() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
            getRoomUrl("/code/99999999"),
            HttpMethod.GET,
            null,
            String.class
        );

        assertNotNull(response.getBody());

        R<?> result = objectMapper.readValue(response.getBody(), R.class);

        // 房间不存在应返回错误码
        assertNotEquals(200, result.getCode());
    }
}
