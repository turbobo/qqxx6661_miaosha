package cn.monitor4all.miaoshaservice.service;


import cn.monitor4all.miaoshadao.model.login.LoginRequest;
import cn.monitor4all.miaoshadao.model.login.LoginResponse;
import cn.monitor4all.miaoshadao.model.login.LoginUser;

public interface LoginUserService {
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 根据token获取用户信息
     */
    LoginUser getUserByToken(String token);
    
    /**
     * 验证token是否有效
     */
    boolean validateToken(String token);
    
    /**
     * 退出登录，移除token
     */
    void logout(String token);
}
