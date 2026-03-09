package com.liubinrui.auth.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckSpaceAuth {
    // 指定从请求中获取 spaceId 的参数名，默认为 "spaceId"
    String idParam() default "spaceId";
}
