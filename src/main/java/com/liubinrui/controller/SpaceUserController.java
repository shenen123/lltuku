package com.liubinrui.controller;

import cn.hutool.core.util.ObjectUtil;
import com.liubinrui.annotation.AuthCheck;
import com.liubinrui.auth.SpaceUserAuthManager;
import com.liubinrui.auth.annotation.CheckSpaceAuth;
import com.liubinrui.common.BaseResponse;
import com.liubinrui.common.ResultUtils;
import com.liubinrui.constant.SpaceUserConstant;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.model.dto.spaceuser.SpaceUserAddRequest;
import com.liubinrui.model.dto.spaceuser.SpaceUserDeleteRequest;
import com.liubinrui.model.dto.spaceuser.SpaceUserQueryRequest;
import com.liubinrui.model.dto.spaceuser.SpaceUserUpdateRequest;
import com.liubinrui.model.entity.SpaceUser;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.SpaceUserVO;
import com.liubinrui.service.SpaceUserService;
import com.liubinrui.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;
    @Resource
    private SpaceUserAuthManager authManager;

    @PostMapping("/add/batch")
    @CheckSpaceAuth(idParam = "spaceUserAddRequest.spaceId")
    public BaseResponse<Boolean> batchAddSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserAddRequest.getSpaceId();
        authManager.checkPermission(spaceId,"spaceUser:manage");

        Boolean result = spaceUserService.addSpaceUsers(spaceUserAddRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    @CheckSpaceAuth(idParam = "deleteRequest.spaceId")
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody SpaceUserDeleteRequest deleteRequest) {
        if (deleteRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long spaceId = deleteRequest.getSpaceId();
        authManager.checkPermission(spaceId,"spaceUser:manage");
        long id = deleteRequest.getId();
        // 判断是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/get")
    @AuthCheck(minSpaceRole = SpaceUserConstant.EDITOR_ROLE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        // 查询数据库
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    @PostMapping("/list")
    @AuthCheck(minSpaceRole = SpaceUserConstant.EDITOR_ROLE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    @PostMapping("/update")
    @CheckSpaceAuth(idParam = "spaceUserUpdateRequest.spaceId")
    public BaseResponse<Boolean> updateSpaceUser(@RequestBody SpaceUserUpdateRequest spaceUserUpdateRequest,
                                                 HttpServletRequest request) {
        if (spaceUserUpdateRequest == null || spaceUserUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long spaceId = spaceUserUpdateRequest.getSpaceId();
        authManager.checkPermission(spaceId,"spaceUser:manage");
        // 将实体类和 DTO 进行转换
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserUpdateRequest, spaceUser);
        // 数据校验
        spaceUserService.validSpaceUser(spaceUser, false);
        // 判断是否存在
        long id = spaceUserUpdateRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}

