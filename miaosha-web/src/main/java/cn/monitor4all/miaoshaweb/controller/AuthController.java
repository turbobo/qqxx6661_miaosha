package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.login.LoginRequest;
import cn.monitor4all.miaoshadao.model.login.LoginResponse;
import cn.monitor4all.miaoshadao.model.login.LoginUser;
import cn.monitor4all.miaoshaservice.service.LoginUserService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
    @Resource
    private LoginUserService loginUserService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse loginResponse = loginUserService.login(request);
            return ApiResponse.success(loginResponse);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<LoginUser> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
            // 移除Bearer前缀
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (!loginUserService.validateToken(token)) {
                return ApiResponse.error("无效的token");
            }

            LoginUser user = loginUserService.getUserByToken(token);
            return ApiResponse.success(user);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String token) {
        try {
            // 移除Bearer前缀
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            // 从token存储中移除该token
            loginUserService.logout(token);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
