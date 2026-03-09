package com.liubinrui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liubinrui.model.entity.SpaceUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SpaceUserMapper extends BaseMapper<SpaceUser> {
    @Select("SELECT space_role FROM space_user WHERE space_id = #{spaceId} AND user_id = #{userId}")
    String selectRoleKey(@Param("spaceId") Long spaceId, @Param("userId") Long userId);
}




