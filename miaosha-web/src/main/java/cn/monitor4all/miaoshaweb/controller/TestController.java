package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 测试控制器 - 用于验证拦截器配置
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin
public class TestController {
    
    /**
     * 公开测试接口 - 不需要登录
     */
    @GetMapping("/public")
    public ApiResponse<String> publicTest() {
        return ApiResponse.success("公开接口访问成功，无需登录");
    }
    
    /**
     * 受保护测试接口 - 需要登录
     */
    @GetMapping("/protected")
    public ApiResponse<String> protectedTest() {
        return ApiResponse.success("受保护接口访问成功，已登录");
    }
}