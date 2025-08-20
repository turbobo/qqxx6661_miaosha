package cn.monitor4all.miaoshadao.model;

/**
 * 业务异常类
 * 用于统一处理业务层面的异常情况
 */
public class BusinessException extends RuntimeException {
    
    private int code;
    private String message;
    
    public BusinessException() {
        super();
    }
    
    public BusinessException(String message) {
        super(message);
        this.message = message;
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
    
    public BusinessException(Throwable cause) {
        super(cause);
    }
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }
    
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
}