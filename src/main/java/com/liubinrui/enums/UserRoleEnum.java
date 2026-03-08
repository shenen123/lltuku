package com.liubinrui.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
    USER("用户", "user", 0),
    VIP("会员", "vip", 1),  // 新增 VIP，等级为 1
    ADMIN("管理员", "admin", 2);

    private final String text;
    private final String value;
    private final int level; // 新增等级字段

    UserRoleEnum(String text, String value, int level) {
        this.text = text;
        this.value = value;
        this.level = level;
    }

    public String getValue() { return value; }

    // 新增获取等级的方法
    public int getLevel() { return level; }

    public static UserRoleEnum getEnumByValue(String value) {
        if (value == null) return null;
        for (UserRoleEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}