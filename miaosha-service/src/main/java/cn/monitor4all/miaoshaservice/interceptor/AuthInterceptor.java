package cn.monitor4all.miaoshaservice.interceptor;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.login.LoginUser;
import cn.monitor4all.miaoshaservice.service.LoginUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 登录拦截器
 * 校验用户登录状态，未登录用户将被重定向到登录页面
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Resource
    private LoginUserService loginUserService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        logger.info("AuthInterceptor拦截请求: {} {}", method, requestURI);
        
        // 获取Authorization头中的token
        String authHeader = request.getHeader("Authorization");
        String token = null;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        // 校验token是否存在且有效
        if (token == null || token.isEmpty() || !loginUserService.validateToken(token)) {
            logger.warn("用户访问受保护资源但未登录或token无效: {} {}", method, requestURI);
            return handleUnauthorized(request, response);
        }
        
        // 验证通过，获取用户信息并设置到request属性中，方便Controller使用
        try {
            LoginUser loginUser = loginUserService.getUserByToken(token);
            if (loginUser != null) {
                request.setAttribute("currentUser", loginUser);
                request.setAttribute("currentUserId", loginUser.getId());
                logger.debug("用户{}成功通过认证", loginUser.getUsername());
            }
        } catch (Exception e) {
            logger.error("获取用户信息失败", e);
            return handleUnauthorized(request, response);
        }
        
        return true;
    }
    
    /**
     * 处理未授权访问
     */
    private boolean handleUnauthorized(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String requestURI = request.getRequestURI();
        String acceptHeader = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        
        // 判断是否为AJAX请求或API请求
        boolean isAjaxRequest = "XMLHttpRequest".equals(xRequestedWith);
        boolean isApiRequest = requestURI.startsWith("/api/");
        boolean expectsJson = acceptHeader != null && acceptHeader.contains("application/json");
        
        if (isAjaxRequest || isApiRequest || expectsJson) {
            // 对于AJAX/API请求，返回JSON错误响应
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            
            PrintWriter out = null;
            try {
                out = response.getWriter();
                ApiResponse<Void> apiResponse = ApiResponse.error("用户未登录，请先登录");
                out.write(objectMapper.writeValueAsString(apiResponse));
                out.flush();
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        } else {
            // 对于页面请求，重定向到登录页面
            String loginUrl = "/login.html";
            
            // 如果不是GET请求，设置状态码为401而不是重定向
            if (!"GET".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<script>window.location.href='" + loginUrl + "';</script>");
            } else {
                // GET请求直接重定向
                response.sendRedirect(loginUrl);
            }
        }
        
        return false;
    }
}