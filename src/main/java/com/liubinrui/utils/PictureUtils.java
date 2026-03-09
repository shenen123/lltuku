package com.liubinrui.utils;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PictureUtils {
    //获取缩略图字节
    public static byte[] generateThumbnail(byte[] originalBytes, int maxSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(originalBytes))
                .size(maxSize, maxSize)
                .keepAspectRatio(true)
                .outputFormat("JPEG")
                .outputQuality(0.85)       // 质量85%
                .toOutputStream(baos);
        return baos.toByteArray();
    }
}
