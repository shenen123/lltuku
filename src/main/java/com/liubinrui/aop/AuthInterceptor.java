package com.liubinrui.aop;

import com.liubinrui.annotation.AuthCheck;
import com.liubinrui.enums.UserRoleEnum;
import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import com.liubinrui.model.entity.User;
import com.liubinrui.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;
//    @Resource
//    private SpaceUserService spaceUserService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (userRoleEnum.getLevel() < mustRoleEnum.getLevel()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
//    @Around("@annotation(authCheck)")
//    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
//        String mustRole = authCheck.mustRole();
//        String  minnedSpaceRole= authCheck.minSpaceRole();
//        String spaceIdParamName = authCheck.spaceIdParam();
//
//        // 1. 获取 request 和登录用户
//        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
//        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
//        User loginUser = userService.getLoginUser(request);
//
//        // 2. 全局角色校验（原有逻辑）
//        if (!mustRole.isEmpty()) {
//            UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
//            if (mustRoleEnum != null) {
//                UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
//                if (userRoleEnum == null || !userRoleEnum.equals(mustRoleEnum)) {
//                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//                }
//                return joinPoint.proceed(); // 全局角色通过，直接放行
//            }
//        }
//
//        // 3. 空间角色校验（新逻辑）
//        String minSpaceRole = authCheck.minSpaceRole();
//        if (!minSpaceRole.isEmpty()) {
//            Long spaceId = extractSpaceIdFromArgs(joinPoint, "spaceId"); // 你需要实现这个方法
//            if (spaceId == null) {
//                throw new BusinessException(ErrorCode.PARAMS_ERROR, "未找到 spaceId");
//            }
//
//            String userSpaceRole = spaceUserService.getUserSpaceRole(loginUser.getId(), spaceId);
//            SpaceRoleEnum userRole = SpaceRoleEnum.getEnumByValue(userSpaceRole);
//            SpaceRoleEnum requiredMinRole = SpaceRoleEnum.getEnumByValue(minSpaceRole);
//
//            if (userRole == null || requiredMinRole == null) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
//
//            //  核心：只要用户角色等级 >= 要求的最小等级，就通过
//            if (userRole.getLevel() >= requiredMinRole.getLevel()) {
//                return joinPoint.proceed();
//            } else {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
//        }
//
//        // 4. 既不要求全局角色，也不要求空间角色 → 放行
//        return joinPoint.proceed();
//    }
//    private Long extractSpaceIdFromArgs(ProceedingJoinPoint joinPoint, String paramName) {
//        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        String[] paramNames = signature.getParameterNames();
//        Object[] args = joinPoint.getArgs();
//
//        for (int i = 0; i < paramNames.length; i++) {
//            if (paramName.equals(paramNames[i]) && args[i] instanceof Long) {
//                return (Long) args[i];
//            }
//            // 如果是包装对象，比如 SpaceRequest { Long spaceId; }
//            if (args[i] != null && paramName.equals("spaceId")) {
//                try {
//                    Field field = args[i].getClass().getDeclaredField("spaceId");
//                    field.setAccessible(true);
//                    Object value = field.get(args[i]);
//                    if (value instanceof Long) return (Long) value;
//                } catch (Exception ignored) {}
//            }
//        }
//        return null;
//    }
    /**
     * 判断用户角色是否 >= 要求角色
     * 权限顺序：VIEWER < EDITOR < ADMIN
     */
//    private boolean hasPermission(SpaceRoleEnum userRole, SpaceRoleEnum requiredRole) {
//        if (userRole == null) return false;
//        if (requiredRole == null) return true;
//
//        // 定义权限等级
//        Map<SpaceRoleEnum, Integer> levelMap = Map.of(
//                SpaceRoleEnum.VIEWER, 1,
//                SpaceRoleEnum.EDITOR, 2,
//                SpaceRoleEnum.ADMIN, 3
//        );
//
//        return levelMap.getOrDefault(userRole, 0) >= levelMap.getOrDefault(requiredRole, 0);
//    }
}
