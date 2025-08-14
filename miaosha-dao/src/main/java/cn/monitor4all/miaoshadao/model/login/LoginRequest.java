package cn.monitor4all.miaoshadao.model.login;

import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;


@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
}
