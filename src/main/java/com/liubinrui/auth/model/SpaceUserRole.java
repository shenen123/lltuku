package com.liubinrui.auth.model;

import lombok.Data;

import java.util.List;

@Data
public class SpaceUserRole {
    /**
     *
     */
    private String key;
    /**
     *
     */
    private String name;
    /**
     *
     */
    private List<String> permissions;
    /**
     *
     */
    private String description;
}

