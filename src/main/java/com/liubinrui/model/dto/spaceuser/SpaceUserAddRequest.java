package com.liubinrui.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SpaceUserAddRequest implements Serializable {

    private Long spaceId;

    private List<Long> userIds;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}

