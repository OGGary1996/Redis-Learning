package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    /*
    * 该用户是否被当前用户关注？
    * 注意：
    *  1. 该字段用于前端展示关注状态
    * */
    private Boolean isFollowed;
}
