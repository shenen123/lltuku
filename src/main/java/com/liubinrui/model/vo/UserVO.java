package com.liubinrui.model.vo;

import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.model.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserVO implements Serializable {
    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userRole;

    private Date createTime;

    private static final long serialVersionUID = 1L;


    public static UserVO objToVo(User user) {
        ThrowUtils.throwIf(user==null, ErrorCode.NOT_FOUND_ERROR);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        return userVO;
    }
}

