package cn.monitor4all.miaoshadao.model;

/**
 * 业务错误码枚举类
 * 用于统一管理系统中的业务错误码
 */
public enum ErrorCode {
    // 通用错误码 1000-1999
    SUCCESS(200, "success"),
    SYSTEM_ERROR(500, "系统错误"),
    PARAM_ERROR(1001, "参数错误"),
    ILLEGAL_REQUEST(1002, "非法请求"),
    
    // 用户相关错误 2000-2999
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_BANNED(2002, "用户被禁用"),
    USER_ACCESS_LIMIT(2003, "用户访问频率超限"),
    
    // 商品相关错误 3000-3999
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_SOLD_OUT(3002, "商品已售罄"),
    PRODUCT_NOT_IN_TIME(3003, "不在抢购时间范围内"),
    
    // 订单相关错误 4000-4999
    ORDER_CREATE_FAILED(4001, "订单创建失败"),
    ORDER_NOT_FOUND(4002, "订单不存在"),
    ORDER_ALREADY_PAID(4003, "订单已支付"),
    
    // 库存相关错误 5000-5999
    STOCK_NOT_ENOUGH(5001, "库存不足"),
    STOCK_UPDATE_FAILED(5002, "库存更新失败"),
    
    // 票券相关错误 6000-6999
    TICKET_NOT_FOUND(6001, "票券不存在"),
    TICKET_SOLD_OUT(6002, "票券已售罄"),
    TICKET_PURCHASE_LIMIT(6003, "已达购买限制"),
    TICKET_DATE_INVALID(6004, "无效的票券日期"),
    TICKET_ALREADY_PURCHASED(6005, "已购买过该日期票券"),
    
    // 限流相关错误 7000-7999
    RATE_LIMIT_EXCEEDED(7001, "请求频率超限"),
    SYSTEM_BUSY(7002, "系统繁忙，请稍后再试");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}