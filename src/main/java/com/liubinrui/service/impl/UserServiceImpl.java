package com.liubinrui.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liubinrui.constant.CommonConstant;
import com.liubinrui.constant.UserConstant;
import com.liubinrui.enums.UserRoleEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.mapper.UserMapper;
import com.liubinrui.model.dto.space.SpaceAddRequest;
import com.liubinrui.model.dto.user.UserQueryRequest;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.LoginUserVO;
import com.liubinrui.model.vo.UserVO;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.UserService;
import com.liubinrui.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.liubinrui.constant.UserConstant.USER_LOGIN_STATE;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    @Lazy
    private SpaceService spaceService;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //检验非空
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        //检验长度
        if (userAccount.length() < 4) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        if (userPassword.length() < 8 || checkPassword.length() < 8)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        if (!userPassword.equals(checkPassword))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        //检验是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        Long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        //密码加密
        String encryPassword = getEncryptPassword(userPassword);
        //存入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean result = this.save(user);
        if (!result) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统失误");
        //为用户创建图片个人空间
        Long spaceResult = spaceService.addSpace(new SpaceAddRequest("无名", 0, 0), user);
        if (spaceResult == 0)
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建空间失败");
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        final String SALT = "liubinrui";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //检验非空
        if (StrUtil.hasBlank(userAccount, userPassword))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        //检验长度
        if (userAccount.length() < 4) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        if (userPassword.length() < 8) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        //密码加密
        String encryPassword = getEncryptPassword(userPassword);
        //检验是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        queryWrapper.eq("user_password", encryPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("error");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不存在或密码错误");
        }
        //记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        //存储到token中
        StpUtil.login(user.getId());
        return this.getLoginUserVO(user);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        //先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) return null;
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (userQueryRequest == null) {
            return queryWrapper;
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "user_role", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "user_account", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "user_name", userName);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public UserVO getUserVO(User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR,"用户不存在");
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        //先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean isAdmin(User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return user.getUserRole().equals(UserConstant.ADMIN_ROLE);
    }

    @Override
    public boolean isAdminRequest(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser.getUserRole().equals(UserConstant.ADMIN_ROLE);
    }
}
