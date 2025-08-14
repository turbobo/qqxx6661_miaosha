package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.model.login.LoginRequest;
import cn.monitor4all.miaoshadao.model.login.LoginResponse;
import cn.monitor4all.miaoshadao.model.login.LoginUser;
import cn.monitor4all.miaoshaservice.service.LoginUserService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginUserServiceImpl implements LoginUserService {
    // 模拟数据库存储用户
    private static final Map<String, LoginUser> users = new HashMap<>();
    // 模拟token存储
    private static final Map<String, LoginUser> tokenStore = new HashMap<>();
    
    // 初始化测试用户
    static {
        LoginUser user1 = new LoginUser();
        user1.setId("1");
        user1.setUsername("user1");
        user1.setPassword("123456"); // 实际项目中应存储加密后的密码
        user1.setNickname("用户一");
        users.put(user1.getUsername(), user1);

        LoginUser user2 = new LoginUser();
        user2.setId("2");
        user2.setUsername("user2");
        user2.setPassword("123456");
        user2.setNickname("用户二");
        users.put(user2.getUsername(), user2);
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {
        // 查找用户
        LoginUser user = users.get(request.getUsername());
        
        // 验证用户是否存在及密码是否正确
        if (user == null || !user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 生成token
        String token = generateToken(user);
        
        // 存储token与用户的关联
        tokenStore.put(token, user);
        
        // 构建并返回登录响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setLoginUser(user);
        
        return response;
    }
    
    @Override
    public LoginUser getUserByToken(String token) {
        return tokenStore.get(token);
    }
    
    @Override
    public boolean validateToken(String token) {
        return tokenStore.containsKey(token);
    }
    
    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenStore.remove(token);
        }
    }
    
    // 生成唯一token
    private String generateToken(LoginUser user) {
        return UUID.randomUUID().toString() + "-" + user.getId();
    }
}
