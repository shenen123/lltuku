package com.liubinrui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liubinrui.model.entity.Space;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SpaceMapper extends BaseMapper<Space> {
    /**
     * 根据 spaceId 获取空间类型和拥有者ID
     * 返回 Map: { "type": 0/1, "owner_id": 888 }
     * 如果空间不存在，返回 null
     */
    @Select("SELECT space_type FROM space WHERE id = #{spaceId}")
    Integer selectSpaceType(@Param("spaceId") Long spaceId);
}




