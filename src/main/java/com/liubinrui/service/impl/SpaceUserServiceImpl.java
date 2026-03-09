package com.liubinrui.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.liubinrui.enums.SpaceRoleEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.exception.ThrowUtils;
import com.liubinrui.mapper.SpaceUserMapper;
import com.liubinrui.model.dto.spaceuser.SpaceUserAddRequest;
import com.liubinrui.model.dto.spaceuser.SpaceUserQueryRequest;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.SpaceUser;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.SpaceUserVO;
import com.liubinrui.model.vo.SpaceVO;
import com.liubinrui.model.vo.UserVO;
import com.liubinrui.service.SpaceService;
import com.liubinrui.service.SpaceUserService;
import com.liubinrui.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id) && id > 0, "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId) && spaceId > 0, "space_id", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId) && userId > 0, "user_id", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "space_role", spaceRole);
        return queryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联查询用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }
        // 关联查询空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 对象列表 => 封装对象列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        // 1. 收集需要关联查询的用户 ID 和空间 ID
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        // 2. 批量查询用户和空间
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 3. 填充 SpaceUserVO 的用户和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            // 填充用户信息
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });
        return spaceUserVOList;
    }

//    @Override
//    public String getUserSpaceRole(Long userId, Long spaceId) {
//        if (userId == null || spaceId == null) {
//            return null;
//        }
//        LambdaQueryWrapper<SpaceUser> wrapper = new LambdaQueryWrapper<>();
//        wrapper.select(SpaceUser::getSpaceRole)
//                .eq(SpaceUser::getUserId, userId)
//                .eq(SpaceUser::getSpaceId, spaceId);
//
//        SpaceUser spaceUser = spaceUserMapper.selectOne(wrapper);
//        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
//        return spaceUser.getSpaceRole();
//    }

    @Override
    @Transactional
    public Boolean addSpaceUsers(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        if (spaceUserAddRequest.getSpaceId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不能为空");
        }
        if (CollectionUtils.isEmpty(spaceUserAddRequest.getUserIds())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户列表不能为空");
        }
        if (StringUtils.isBlank(spaceUserAddRequest.getSpaceRole())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色不能为空");
        }

        // 校验角色合法性
        if (SpaceRoleEnum.getEnumByValue(spaceUserAddRequest.getSpaceRole()) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的角色: " + spaceUserAddRequest.getSpaceRole());
        }

        List<Long> userIdList = spaceUserAddRequest.getUserIds();
        // 过滤掉重复、null 的 userId
        List<Long> validUserIds = userIdList.stream().filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", spaceUserAddRequest.getSpaceId())
                .in("user_id", validUserIds);
        List<SpaceUser> spaceUserList = spaceUserMapper.selectList(queryWrapper);
        List<Long> existUserIdList = spaceUserList.stream().map(SpaceUser::getUserId).toList();

        validUserIds.stream().map(validUserId -> !existUserIdList.contains(validUserId)).toList();
        //有已经插入的就不再插入
        if (validUserIds.isEmpty()) {
            return false;
        }

        // 构建批量插入的实体列表
        List<SpaceUser> spaceUsers = validUserIds.stream()
                .map(userId -> {
                    SpaceUser su = new SpaceUser();
                    su.setSpaceId(spaceUserAddRequest.getSpaceId());
                    su.setUserId(userId);
                    su.setSpaceRole(spaceUserAddRequest.getSpaceRole());
                    return su;
                })
                .collect(Collectors.toList());
        return saveBatch(spaceUsers);
    }

}
