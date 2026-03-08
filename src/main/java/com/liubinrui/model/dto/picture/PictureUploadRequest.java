package com.liubinrui.model.dto.picture;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {

    private String fileUrl;

    @Schema(description = "图片名称")
    private String picName;

    @Schema(description = "空间ID")
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}

