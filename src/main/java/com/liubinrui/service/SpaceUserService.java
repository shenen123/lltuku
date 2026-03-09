package com.liubinrui.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liubinrui.model.dto.spaceuser.SpaceUserAddRequest;
import com.liubinrui.model.dto.spaceuser.SpaceUserQueryRequest;
import com.liubinrui.model.entity.SpaceUser;
import com.liubinrui.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface SpaceUserService extends IService<SpaceUser> {

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
    /**
     * 获取用户在指定空间的角色
     * @return 角色值，如 "admin"、"editor"，无权限返回 null
     */
    //String getUserSpaceRole(Long userId, Long spaceId);

    /**
     * 批量添加成员
     * @param spaceUserAddRequest
     * @return
     */
    Boolean addSpaceUsers(SpaceUserAddRequest spaceUserAddRequest);
}
