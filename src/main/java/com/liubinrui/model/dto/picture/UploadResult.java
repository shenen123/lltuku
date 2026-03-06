package com.liubinrui.model.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResult {
    String originalObjectName;

    String thumbnailObjectName;

    String contentType;

    private long originalSize;

    private long thumbnailSize;

    ImageInfo originalImage;

    ImageInfo thumbnailImage;
}
