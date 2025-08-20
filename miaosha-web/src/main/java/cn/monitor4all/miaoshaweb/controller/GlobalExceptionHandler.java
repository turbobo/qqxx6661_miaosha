package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.BusinessException;
import cn.monitor4all.miaoshadao.model.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 用于统一处理系统中的各种异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     * @param e 业务异常
     * @return 统一返回格式
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        LOGGER.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数非法异常
     * @param e 非法参数异常
     * @return 统一返回格式
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        LOGGER.warn("参数非法异常: message={}", e.getMessage());
        return ApiResponse.error(ErrorCode.PARAM_ERROR, e.getMessage());
    }
    
    /**
     * 处理非法状态异常
     * @param e 非法状态异常
     * @return 统一返回格式
     */
    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalStateException(IllegalStateException e) {
        LOGGER.warn("状态非法异常: message={}", e.getMessage());
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR, e.getMessage());
    }
    
    /**
     * 处理其他异常
     * @param e 其他异常
     * @return 统一返回格式
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        LOGGER.error("系统异常: message={}", e.getMessage(), e);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR);
    }
}