package com.gopair.backend.controller;

import com.gopair.backend.common.constants.MessageConstants;
import com.gopair.backend.common.core.R;
import com.gopair.backend.common.util.JwtUtils;
import com.gopair.backend.controller.common.BaseController;
import com.gopair.backend.domain.dto.LoginDTO;
import com.gopair.backend.domain.dto.RegisterDTO;
import com.gopair.backend.domain.po.User;
import com.gopair.backend.domain.vo.LoginResponseVO;
import com.gopair.backend.domain.vo.UserInfoVO;
import com.gopair.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 
 * 提供用户登录等认证相关接口
 * 
 * @author gopair
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录认证相关接口")
public class AuthController extends BaseController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private UserService userService;

    /**
     * 用户登录接口
     * 
     * @param loginDTO 登录请求数据
     * @return JWT令牌响应
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户通过用户名和密码登录系统，成功后返回JWT令牌")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登录成功", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public R<LoginResponseVO> login(
            @Parameter(description = "登录信息", required = true) 
            @Validated @RequestBody LoginDTO loginDTO) {
        try {
            // 使用AuthenticationManager进行身份验证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword()));

            // 如果验证成功，将认证信息设置到SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 获取用户详情
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String username = userDetails.getUsername();

            // 生成JWT令牌
            String jwt = jwtUtils.generateToken(username);

            // 获取用户信息
            UserInfoVO userInfoVO = userService.getUserInfo(username);
            if (userInfoVO == null) {
                return error(MessageConstants.USER_NOT_FOUND);
            }
            
            // 创建登录响应对象
            LoginResponseVO response = new LoginResponseVO(jwt, userInfoVO);

            // 返回成功响应
            return success(MessageConstants.LOGIN_SUCCESS, response);
        } catch (BadCredentialsException e) {
            // 如果用户名或密码错误，返回错误响应
            return error(MessageConstants.INVALID_CREDENTIALS);
        } catch (Exception e) {
            // 其他异常，返回错误响应
            logger.error("登录异常", e);
            return error(MessageConstants.LOGIN_FAILED + ": " + e.getMessage());
        }
    }
    
    /**
     * 获取当前用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public R<UserInfoVO> getUserInfo() {
        // 获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return error(MessageConstants.UNAUTHORIZED);
        }
        
        // 获取用户名
        String username = authentication.getName();
        
        // 获取用户信息
        UserInfoVO userInfoVO = userService.getUserInfo(username);
        if (userInfoVO == null) {
            return error(MessageConstants.USER_NOT_FOUND);
        }
        
        return success(userInfoVO);
    }
    
    /**
     * 用户注册接口
     * 
     * @param registerDTO 注册请求数据
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户")
    public R<Void> register(@Validated @RequestBody RegisterDTO registerDTO) {
        // 校验两次密码是否一致
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            return error("两次输入的密码不一致");
        }
        
        // 转换为User对象
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(registerDTO.getPassword());
        user.setEmail(registerDTO.getEmail());
        user.setNickname(registerDTO.getNickname());
        user.setStatus('0'); // 默认正常状态
        
        // 注册用户
        boolean result = userService.registerUser(user);
        if (!result) {
            return error("用户名已存在");
        }
        
        return success();
    }
    
    /**
     * 退出登录
     * 
     * @return 退出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "清除当前用户的登录状态")
    public R<Void> logout() {
        // 清除SecurityContext中的认证信息
        SecurityContextHolder.clearContext();
        return success();
    }
} 