package cn.monitor4all.miaoshaservice.utils.redis;

public class CacheExpiredTime {
    /** 一个月过期 */
    public static final int A_MONTH_EXPIRED = 30 * 24 * 60 * 60;

    /** 1分钟 */
    public static final int ONE_MINUTE = 60;

    /** 2分钟 */
    public static final int TWO_MINUTE = ONE_MINUTE * 2;

    /** 3分钟 */
    public static final int THREE_MINUTE = ONE_MINUTE * 3;

    /** 5分钟 */
    public static final int FIVE_MINUTE = 5 * 60;

    /** 30分钟*/
    public static final int HALF_HOUR = 30 * 60;

    /** 20分钟*/
    public static final int TWENTY_MINUTE = 20 * 60;

    /** 1小时*/
    public static final int ONE_HOUR = 60 * 60;

    /** 1天 */
    public static final int ONE_DAY = 24*3600;

    /**
     * 2小时
     */
    public static final int TWO_HOUR = 2 * 60 * 60;

    /** 70分钟 */
    public static final int SEVENTY_MINUTE = 70 * 60;
}