package com.liubinrui.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     */
    String mustRole() default "";
    // 新增：空间角色（如 "admin", "editor"）
    String minSpaceRole() default "";

    // 新增：指定 spaceId 在方法参数中的位置（或字段名）
    String spaceIdParam() default "spaceId"; // 默认参数名是 spaceId
}

