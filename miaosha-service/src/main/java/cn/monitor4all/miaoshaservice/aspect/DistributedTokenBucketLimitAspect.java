package cn.monitor4all.miaoshaservice.aspect;

import cn.monitor4all.miaoshaservice.annotation.DistributedTokenBucketLimit;
import cn.monitor4all.miaoshaservice.config.DistributedTokenBucketConfig;
import cn.monitor4all.miaoshaservice.service.DistributedTokenBucketService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * 分布式令牌桶限流切面
 * 实现注解驱动的限流功能
 */
@Aspect
@Component
public class DistributedTokenBucketLimitAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTokenBucketLimitAspect.class);
    
    @Resource
    private DistributedTokenBucketService distributedTokenBucketService;
    
    @Resource
    private DistributedTokenBucketConfig config;
    
    // SpEL表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    @Around("@annotation(distributedTokenBucketLimit)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedTokenBucketLimit distributedTokenBucketLimit) throws Throwable {
        try {
            // 解析限流键
            String limitKey = resolveLimitKey(joinPoint, distributedTokenBucketLimit);
            
            // 获取限流配置
            int capacity = distributedTokenBucketLimit.capacity() > 0 ? 
                distributedTokenBucketLimit.capacity() : config.getDefaultCapacity();
            double rate = distributedTokenBucketLimit.rate() > 0 ? 
                distributedTokenBucketLimit.rate() : config.getDefaultRate();
            int tokens = distributedTokenBucketLimit.tokens();
            
            // 执行限流检查
            boolean allowed = executeRateLimit(limitKey, capacity, rate, tokens, distributedTokenBucketLimit);
            
            if (allowed) {
                // 限流通过，执行原方法
                LOGGER.debug("令牌桶限流通过，键: {}, 容量: {}, 速率: {}, 令牌: {}", 
                    limitKey, capacity, rate, tokens);
                return joinPoint.proceed();
            } else {
                // 限流拒绝，抛出异常或返回错误响应
                LOGGER.warn("令牌桶限流拒绝，键: {}, 容量: {}, 速率: {}, 令牌: {}", 
                    limitKey, capacity, rate, tokens);
                throw new RuntimeException(distributedTokenBucketLimit.message());
            }
            
        } catch (Exception e) {
            if (distributedTokenBucketLimit.logLimit()) {
                LOGGER.error("令牌桶限流执行异常，方法: {}, 错误: {}", 
                    joinPoint.getSignature().toShortString(), e.getMessage(), e);
            }
            throw e;
        }
    }
    
    /**
     * 解析限流键
     */
    private String resolveLimitKey(ProceedingJoinPoint joinPoint, DistributedTokenBucketLimit annotation) {
        String key = annotation.key();
        
        if (StringUtils.hasText(key)) {
            // 使用自定义key，支持SpEL表达式
            return evaluateSpEL(key, joinPoint);
        }
        
        // 根据策略生成默认key
        switch (annotation.strategy()) {
            case USER:
                return resolveUserKey(joinPoint);
            case GLOBAL:
                return "global:" + joinPoint.getSignature().toShortString();
            case CUSTOM:
                return resolveCustomKey(joinPoint);
            case INTERFACE:
            default:
                return joinPoint.getSignature().toShortString();
        }
    }
    
    /**
     * 解析用户级别的限流键
     */
    private String resolveUserKey(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if ("userId".equals(parameterNames[i]) && args[i] != null) {
                    return "user:" + args[i];
                }
            }
        }
        
        // 如果没有找到userId参数，使用默认用户key
        return "user:anonymous";
    }
    
    /**
     * 解析自定义限流键
     */
    private String resolveCustomKey(ProceedingJoinPoint joinPoint) {
        // 这里可以实现自定义的key生成逻辑
        return "custom:" + joinPoint.getSignature().toShortString();
    }
    
    /**
     * 执行限流检查
     */
    private boolean executeRateLimit(String limitKey, int capacity, double rate, int tokens, 
                                   DistributedTokenBucketLimit annotation) {
        if (annotation.blocking()) {
            // 阻塞等待模式
            return distributedTokenBucketService.tryAcquireWithTimeout(
                limitKey, capacity, rate, annotation.timeout());
        } else {
            // 非阻塞模式
            return distributedTokenBucketService.tryAcquire(limitKey, capacity, rate, tokens);
        }
    }
    
    /**
     * 评估SpEL表达式
     */
    private String evaluateSpEL(String expression, ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();
            
            EvaluationContext context = new StandardEvaluationContext();
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            
            Expression exp = expressionParser.parseExpression(expression);
            Object result = exp.getValue(context);
            return result != null ? result.toString() : expression;
            
        } catch (Exception e) {
            LOGGER.warn("SpEL表达式解析失败: {}, 使用原始表达式", expression, e);
            return expression;
        }
    }
}
