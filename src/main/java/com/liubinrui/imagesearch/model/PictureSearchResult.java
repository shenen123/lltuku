package com.liubinrui.imagesearch.model;
import lombok.Data;

@Data
public class PictureSearchResult {
    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}
