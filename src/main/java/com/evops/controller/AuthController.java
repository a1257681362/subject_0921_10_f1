package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.common.BusinessException;
import com.evops.common.ErrorCode;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/** 考务账户登录/登出/当前经办。失败返回可读状态。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static class LoginRequest {
        @NotBlank(message = "不能为空")
        private String username;
        @NotBlank(message = "不能为空")
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(new UsernamePasswordToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("username", subject.getPrincipal());
        result.put("authenticated", true);
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        SecurityUtils.getSubject().logout();
        return ApiResponse.ok(null);
    }

    @GetMapping("/current")
    public ApiResponse<Map<String, Object>> current() {
        Subject subject = SecurityUtils.getSubject();
        Map<String, Object> result = new HashMap<>();
        result.put("username", subject.getPrincipal());
        result.put("authenticated", subject.isAuthenticated());
        return ApiResponse.ok(result);
    }
}
