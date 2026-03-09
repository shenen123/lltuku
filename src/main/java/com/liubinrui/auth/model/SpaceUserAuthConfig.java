package com.liubinrui.auth.model;

import lombok.Data;

import java.util.List;
@Data
public class SpaceUserAuthConfig {
    /**
     *
     */
    private List<PermissionMeta> permissions;
    /**
     *
     */
    private List<SpaceUserRole> roles;

}
