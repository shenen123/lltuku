package com.liubinrui.auth.aspect;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import com.liubinrui.auth.SpaceUserAuthManager;
import com.liubinrui.auth.annotation.CheckSpaceAuth;
import com.liubinrui.mapper.SpaceMapper;
import com.liubinrui.mapper.SpaceUserMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;

@Aspect
@Component
public class SpaceAuthAspect {

    @Resource
    private SpaceUserAuthManager authManager;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Resource
    private SpaceMapper spaceMapper;

    private static final int SPACE_TYPE_PERSONAL = 0;
    private static final int SPACE_TYPE_TEAM = 1;
    private static final Long PUBLIC_SPACE_ID = 0L; // 统一用 0 代表公共空间

    @Around("@annotation(com.liubinrui.auth.annotation.CheckSpaceAuth)")
    public Object handleSmartAuth(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        CheckSpaceAuth annotation = signature.getMethod().getAnnotation(CheckSpaceAuth.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        // 1.获取空间 ID (全程 Long 类型),返回 null 代表未传参，即公共空间场景
        Long spaceId = extractSpaceIdFromContext(joinPoint, annotation.idParam());
        String roleKey;
        Long logicSpaceId = spaceId; // 核心业务 ID，全程 Long

        if (spaceId == null) {
            // --- 场景 A: 无 ID -> 公共空间 ---
            roleKey = StpUtil.isLogin() ? "user" : "guest";
            logicSpaceId = PUBLIC_SPACE_ID;
        } else {
            // --- 场景 B & C: 有 ID -> 查库定类型 ---
            Integer spaceType = spaceMapper.selectSpaceType(spaceId);

            if (spaceType == null) {
                throw new IllegalArgumentException("空间不存在: ID=" + spaceId);
            }

            long currentUserId = StpUtil.getLoginIdAsLong();

            if (spaceType == SPACE_TYPE_PERSONAL) {
                // 私人空间是管理者
                // 私人空间是管理者
                roleKey = "admin";
            } else if (spaceType == SPACE_TYPE_TEAM) {
                roleKey = spaceUserMapper.selectRoleKey(spaceId, currentUserId);
                if (roleKey == null) {
                    throw new NotPermissionException("您不是该团队成员，无权访问");
                }
            } else {
                throw new RuntimeException("未知的空间类型: " + spaceType);
            }
        }
        authManager.loginSpace(logicSpaceId, roleKey);
        return joinPoint.proceed();
    }

    /**
     * 通用提取器：支持 "paramName" 或 "objectName.fieldName"
     * 返回值严格为 Long
     */
    private Long extractSpaceIdFromContext(ProceedingJoinPoint joinPoint, String expression) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();

        // 辅助 Lambda：安全转 Long
        java.util.function.Function<Object, Long> toLong = (obj) -> {
            if (obj == null) return null;
            if (obj instanceof Number) {
                return ((Number) obj).longValue();
            }
            try {
                return Long.parseLong(obj.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("空间 ID 必须是数字格式，收到: " + obj);
            }
        };

        // 情况 1: 直接参数匹配
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(expression)) {
                return toLong.apply(args[i]);
            }
        }

        // 情况 2: 对象属性匹配 (如 "req.spaceId")
        if (expression != null && expression.contains(".")) {
            String[] parts = expression.split("\\.", 2);
            String objName = parts[0];
            String fieldName = parts[1];

            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(objName)) {
                    Object obj = args[i];
                    if (obj != null) {
                        try {
                            Field field = obj.getClass().getDeclaredField(fieldName);
                            field.setAccessible(true);
                            Object val = field.get(obj);
                            return toLong.apply(val);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            throw new RuntimeException("无法读取字段: " + expression, e);
                        }
                    }
                }
            }
        }

        return null;
    }
}