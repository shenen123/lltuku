package com.liubinrui.model.dto.picture;

import com.liubinrui.common.DeleteRequest;
import lombok.Data;

@Data
public class PictureDeleteRequest extends DeleteRequest {
    private Long spaceId;
}
