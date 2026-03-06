package com.liubinrui.model.dto.picture;


import lombok.Data;

import java.util.List;

/**
 * 图片标签与分类 DTO
 */
@Data
public class PictureTagCategory {

    /**
     * 标签列表，例如：["热门", "搞笑", "生活", ...]
     */
    private List<String> tagList;

    /**
     * 分类列表，例如：["模板", "电商", "表情包", ...]
     */
    private List<String> categoryList;
}
