package com.hmdp.dto;

import lombok.Data;

@Data
public class LoginFormDTO {

    private String phone;
    /**
     * 密码和验证码必须存在一个
     */
    private String code;

    private String password;
}
