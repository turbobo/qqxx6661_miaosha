package cn.monitor4all.miaoshaweb.config;

import cn.monitor4all.miaoshaservice.interceptor.AuthInterceptor;
import javax.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Resource
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        return;
        // 拦截所有需要认证的请求
//        registry.addInterceptor((HandlerInterceptor) authInterceptor)
////                .addPathPatterns("/api/tickets/**", "/index.html", "/")  // 拦截票券相关API和主页
//                .excludePathPatterns("/api/auth/**", "/login.html");     // 排除认证相关接口和登录页面
    }
    
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 添加默认页面映射
        registry.addViewController("/").setViewName("index.html");
    }
}