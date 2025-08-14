package cn.monitor4all.miaoshadao.model.login;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private LoginUser loginUser;
}
