package cn.monitor4all.miaoshadao.utils;

public enum CacheKey {

    HASH_KEY("miaosha_v1_user_hash"),
    // 用户访问限制，1分钟内10次请求，过期时间60秒
    LIMIT_KEY("miaosha_v1_user_limit"),
    STOCK_COUNT("miaosha_v1_stock_count"),

    LOCK_USER_TICKET_DATE("miaosha_v1_user_ticket_date"),
    /**
     * 用户是否下单缓存
     *         // 为key设置8小时过期时间
     *         // 用户离开，则删除缓存
     *         // 定时任务删除
     */
    USER_HAS_ORDER("miaosha_v1_user_has_order");

    private String key;
    private CacheKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }
}
