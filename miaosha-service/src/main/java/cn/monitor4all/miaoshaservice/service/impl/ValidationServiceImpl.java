package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.User;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.UserMapper;
import cn.monitor4all.miaoshadao.model.Ticket;
import cn.monitor4all.miaoshaservice.service.ValidationService;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 验证服务实现类
 * 集中处理所有验证逻辑，避免循环依赖
 */
@Service
public class ValidationServiceImpl implements ValidationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationServiceImpl.class);
    
    // 抢购时间常量
    private static final LocalTime START_TIME = LocalTime.of(9, 0); // 上午9点开始
    private static final LocalTime END_TIME = LocalTime.of(23, 0);  // 晚上11点结束
    
    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private TicketCacheManager ticketCacheManager;

    @Override
    public Map<String, Object> validateTicketCount(String date) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            LOGGER.info("开始检查票数合法性，日期: {}", date);
            
            // 获取票券实体
            Ticket ticketWithCacheFallback = ticketCacheManager.getTicketWithFallback(date);
            if (ticketWithCacheFallback == null) {
                result.put("valid", false);
                result.put("message", "该日期的票券不存在");
                result.put("code", "TICKET_NOT_EXIST");
                result.put("ticketDate", date);
                return result;
            }
            
            // 检查总票数是否合法
            if (ticketWithCacheFallback.getTotal() <= 0) {
                result.put("valid", false);
                result.put("message", "总票数必须大于0");
                result.put("code", "INVALID_TOTAL_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketWithCacheFallback.getTotal());
                return result;
            }
            
            // 检查剩余票数是否合法
            if (ticketWithCacheFallback.getRemaining() < 0) {
                result.put("valid", false);
                result.put("message", "剩余票数不能为负数");
                result.put("code", "INVALID_REMAINING_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketWithCacheFallback.getTotal());
                result.put("remainingCount", ticketWithCacheFallback.getRemaining());
                return result;
            }
            
            // 检查剩余票数是否超过总票数
            if (ticketWithCacheFallback.getRemaining() > ticketWithCacheFallback.getTotal()) {
                result.put("valid", false);
                result.put("message", "剩余票数不能超过总票数");
                result.put("code", "REMAINING_EXCEEDS_TOTAL");
                result.put("ticketDate", date);
                result.put("totalCount", ticketWithCacheFallback.getTotal());
                result.put("remainingCount", ticketWithCacheFallback.getRemaining());
                return result;
            }
            
            // 检查已售票数是否合法
            int soldCount = ticketWithCacheFallback.getTotal() - ticketWithCacheFallback.getRemaining();
            if (soldCount < 0) {
                result.put("valid", false);
                result.put("message", "已售票数不能为负数");
                result.put("code", "INVALID_SOLD_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketWithCacheFallback.getTotal());
                result.put("remainingCount", ticketWithCacheFallback.getRemaining());
                result.put("soldCount", soldCount);
                return result;
            }
            
            // 票数合法性检查通过
            result.put("valid", true);
            result.put("message", "票数合法性检查通过");
            result.put("code", "SUCCESS");
            result.put("ticketDate", date);
            result.put("totalCount", ticketWithCacheFallback.getTotal());
            result.put("remainingCount", ticketWithCacheFallback.getRemaining());
            result.put("soldCount", soldCount);
            result.put("soldPercentage", soldCount * 100.0 / ticketWithCacheFallback.getTotal());
            
            LOGGER.info("票数合法性检查完成，日期: {}, 总票数: {}, 剩余票数: {}, 已售票数: {}", 
                       date, ticketWithCacheFallback.getTotal(), ticketWithCacheFallback.getRemaining(), soldCount);
            
        } catch (Exception e) {
            LOGGER.error("检查票数合法性失败，日期: {}", date, e);
            result.put("valid", false);
            result.put("message", "检查票数合法性时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("ticketDate", date);
        }
        
        return result;
    }
    
    @Override
    public void validateTicketCountWithException(String date) {
        Map<String, Object> result = validateTicketCount(date);
        if (!(boolean) result.get("valid")) {
            throw new RuntimeException("票数验证失败: " + result.get("message"));
        }
    }
    
    @Override
    public Map<String, Object> validateUser(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            LOGGER.info("开始验证用户，用户ID: {}", userId);
            
            // 基本参数验证
            if (userId == null) {
                result.put("valid", false);
                result.put("message", "用户ID不能为空");
                result.put("code", "INVALID_USER_ID");
                return result;
            }
            
            // 查询用户是否存在
            User user = userMapper.selectByPrimaryKey(userId);
            if (user == null) {
                result.put("valid", false);
                result.put("message", "用户不存在");
                result.put("code", "USER_NOT_EXIST");
                result.put("userId", userId);
                return result;
            }
            
            // 检查用户状态
//            if (user.getStatus() != null && user.getStatus() == 0) {
//                result.put("valid", false);
//                result.put("message", "用户已被禁用");
//                result.put("code", "USER_DISABLED");
//                result.put("userId", userId);
//                return result;
//            }
            
            // 用户验证通过
            result.put("valid", true);
            result.put("message", "用户验证通过");
            result.put("code", "SUCCESS");
            result.put("userId", userId);
            result.put("userInfo", user);
            
            LOGGER.info("用户验证完成，用户ID: {}, 用户名: {}", userId, user.getUserName());
            
        } catch (Exception e) {
            LOGGER.error("验证用户失败，用户ID: {}", userId, e);
            result.put("valid", false);
            result.put("message", "验证用户时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("userId", userId);
        }
        
        return result;
    }
    
    @Override
    public void validateUserWithException(Long userId) {
        Map<String, Object> result = validateUser(userId);
        if (!(boolean) result.get("valid")) {
            throw new RuntimeException("用户验证失败: " + result.get("message"));
        }
    }
    
    @Override
    public Map<String, Object> validatePurchaseTime(String date) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            LOGGER.info("开始验证抢购时间，日期: {}", date);
            
            // 基本参数验证
            if (date == null || date.isEmpty()) {
                result.put("valid", false);
                result.put("message", "日期不能为空");
                result.put("code", "INVALID_DATE");
                return result;
            }
            
            // 解析日期
            LocalDate ticketDate;
            try {
                ticketDate = LocalDate.parse(date);
            } catch (Exception e) {
                result.put("valid", false);
                result.put("message", "日期格式无效，请使用yyyy-MM-dd格式");
                result.put("code", "INVALID_DATE_FORMAT");
                return result;
            }
            
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalDate dayAfterTomorrow = today.plusDays(2);
            
            // 检查是否为过去日期
            if (ticketDate.isBefore(today)) {
                result.put("valid", false);
                result.put("message", "不能购买过去的日期");
                result.put("code", "PAST_DATE");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return result;
            }
            
            // 检查是否为今天
            if (ticketDate.equals(today)) {
                result.put("valid", true);
                result.put("message", "可以抢购今日票券");
                result.put("code", "TODAY");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "today");
                return result;
            }
            
            // 检查是否为明天
            if (ticketDate.equals(tomorrow)) {
                result.put("valid", true);
                result.put("message", "可以抢购明日票券");
                result.put("code", "TOMORROW");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "tomorrow");
                return result;
            }
            
            // 检查是否为后天
            if (ticketDate.equals(dayAfterTomorrow)) {
                result.put("valid", true);
                result.put("message", "可以抢购后日票券");
                result.put("code", "DAY_AFTER_TOMORROW");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "dayAfterTomorrow");
                return result;
            }
            
            // 检查是否为未来日期（超过3天）
            if (ticketDate.isAfter(dayAfterTomorrow)) {
                result.put("valid", false);
                result.put("message", "只能抢购最近3天的票券");
                result.put("code", "FUTURE_DATE");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("maxAllowedDate", dayAfterTomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return result;
            }
            
            // 默认情况
            result.put("valid", false);
            result.put("message", "未知的日期验证结果");
            result.put("code", "UNKNOWN");
            result.put("ticketDate", date);
            result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            
        } catch (Exception e) {
            LOGGER.error("验证抢购时间失败，日期: {}", date, e);
            result.put("valid", false);
            result.put("message", "验证抢购时间时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("ticketDate", date);
        }
        
        return result;
    }
    
    @Override
    public void validatePurchaseTimeWithException(String date) {
        Map<String, Object> result = validatePurchaseTime(date);
        if (!(boolean) result.get("valid")) {
            throw new RuntimeException("抢购时间验证失败: " + result.get("message"));
        }
    }
    
    @Override
    public Map<String, Object> validatePurchaseRequest(Long userId, String date) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            LOGGER.info("开始综合验证购买请求，用户ID: {}, 日期: {}", userId, date);
            
            // 1. 验证用户
            Map<String, Object> userValidation = validateUser(userId);
            if (!(boolean) userValidation.get("valid")) {
                return userValidation;
            }
            
            // 2. 验证票数
            Map<String, Object> ticketValidation = validateTicketCount(date);
            if (!(boolean) ticketValidation.get("valid")) {
                return ticketValidation;
            }
            
            // 3. 验证抢购时间
            Map<String, Object> timeValidation = validatePurchaseTime(date);
            if (!(boolean) timeValidation.get("valid")) {
                return timeValidation;
            }
            
            // 所有验证通过
            result.put("valid", true);
            result.put("message", "购买请求验证通过");
            result.put("code", "SUCCESS");
            result.put("userId", userId);
            result.put("date", date);
            result.put("userInfo", userValidation.get("userInfo"));
            result.put("ticketInfo", ticketValidation);
            
            LOGGER.info("购买请求验证完成，用户ID: {}, 日期: {}", userId, date);
            
        } catch (Exception e) {
            LOGGER.error("验证购买请求失败，用户ID: {}, 日期: {}", userId, date, e);
            result.put("valid", false);
            result.put("message", "验证购买请求时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("userId", userId);
            result.put("date", date);
        }
        
        return result;
    }
}
