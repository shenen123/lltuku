package com.liubinrui.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
@Data
public class PictureGetRequest implements Serializable {
    private Long pictureId;
    private Long spaceId;
}
