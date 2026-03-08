package com.liubinrui.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liubinrui.model.dto.space.SpaceQueryRequest;
import com.liubinrui.model.entity.Space;
import com.liubinrui.model.entity.User;
import com.liubinrui.model.vo.SpaceVO;


import javax.servlet.http.HttpServletRequest;

public interface SpaceService extends IService<Space> {

    /**
     * 校验数据
     * @param space
     * @param add 对创建的数据进行校验
     */
    void validSpace(Space space, boolean add);

    /**
     * 权限校验
     * @param user
     * @param space
     */
    void checkSpaceAuth(User user, Space space);
    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);
    
    /**
     * 获取空间封装
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 分页获取空间封装
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
}
