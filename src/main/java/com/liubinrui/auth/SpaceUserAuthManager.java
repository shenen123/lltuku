package com.liubinrui.auth;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.liubinrui.auth.model.SpaceUserAuthConfig;
import com.liubinrui.auth.model.SpaceUserRole;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpaceUserAuthManager {

    private SpaceUserAuthConfig config;

    @PostConstruct
    public void init() {
        try {
            String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
            this.config = JSONUtil.toBean(json, new TypeReference<SpaceUserAuthConfig>() {
            }.getType(), false);
            if (this.config == null || this.config.getRoles() == null) {
                throw new RuntimeException("权限配置文件缺少 roles 节点");
            }
        } catch (Exception e) {
            throw new RuntimeException("加载空间权限配置失败", e);
        }
    }

    /**
     * 将指定角色的权限写入 Sa-Token Session
     */
    public void loginSpace(Long spaceId, String roleKey) {
        // 确保这里也是 0
        Long safeSpaceId = (spaceId == null) ? 0L : spaceId;

        List<String> permissions = getPermissionsByRole(roleKey);
        String sessionKey = "space_perms:" + safeSpaceId;

        // 【添加日志】
        System.out.println(">>> [DEBUG] 正在存入权限 | SpaceID: " + safeSpaceId + " | SessionKey: [" + sessionKey + "] | Perms: " + permissions);

        StpUtil.getSession().set(sessionKey, permissions);
        StpUtil.getSession().set("space_role:" + safeSpaceId, roleKey);
    }

    /**
     * 根据角色 Key 获取权限列表
     */
    public List<String> getPermissionsByRole(String roleKey) {
        if (config == null || config.getRoles() == null)
            return new ArrayList<>();
        return config.getRoles().stream()
                .filter(r -> roleKey.equals(r.getKey()))
                .findFirst()
                .map(SpaceUserRole::getPermissions)
                .orElse(new ArrayList<>());
    }

    /**
     * 校验当前用户是否有特定权限
     */
    public boolean hasPermission(Long spaceId, String permission) {
        Long safeSpaceId = (spaceId == null) ? 0L : spaceId;
        String sessionKey = "space_perms:" + safeSpaceId;

        // 【添加日志】
        System.out.println(">>> [DEBUG] 正在校验权限 | SpaceID: " + safeSpaceId + " | SessionKey: [" + sessionKey + "] | 期望权限: " + permission);

        Object obj = StpUtil.getSession().get(sessionKey);

        if (obj == null) {
            System.out.println(">>> [ERROR] Session 中找不到 Key: " + sessionKey);
            // 打印当前 Session 里所有的 Key，看看到底存了什么
            System.out.println(">>> [DEBUG] 当前 Session 所有 Keys: " + StpUtil.getSession().keys());
            return false;
        }

        if (!(obj instanceof List)) return false;
        List<String> perms = (List<String>) obj;
        boolean result = perms.contains(permission);
        System.out.println(">>> [RESULT] 校验结果: " + result);
        return result;
    }

    /**
     * 如果没有权限则抛出异常
     */
    public void checkPermission(Long spaceId, String permission) {
        if (!hasPermission(spaceId, permission)) {
            throw new NotPermissionException(permission);
        }
    }
}
