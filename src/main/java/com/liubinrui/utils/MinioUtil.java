package com.liubinrui.utils;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class MinioUtil {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "lbryuntuku";

    private static MinioClient minioClient;

    static {
        minioClient = MinioClient.builder()
                .endpoint(ENDPOINT)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        createBucketIfNotExists();
    }

    private static void createBucketIfNotExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(BUCKET_NAME).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(BUCKET_NAME).build()
                );
                System.out.println("Bucket created: " + BUCKET_NAME);
            }
        } catch (Exception e) {
            throw new RuntimeException("创建 MinIO Bucket 失败", e);
        }
    }

    /**
     * 上传文件
     * @param objectName   对象名（含路径，如 "images/2026/02/avatar.jpg"）
     * @param contentType  MIME 类型（如 "image/jpeg", "video/mp4"）
     * @param inputStream  文件输入流
     */
    public static void upload(String objectName, String contentType, InputStream inputStream) {
        try {
            long size = inputStream.available();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("上传文件到 MinIO 失败: " + objectName, e);
        }
    }

    /**
     * 下载文件（返回 InputStream）
     * @param objectName 对象名
     * @return 文件流
     */
    public static InputStream download(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("从 MinIO 下载文件失败: " + objectName, e);
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名
     */
    public static void delete(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("删除 MinIO 文件失败: " + objectName, e);
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名
     * @return true 存在，false 不存在
     */
    public static boolean exists(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("检查文件是否存在时出错: " + objectName, e);
        } catch (Exception e) {
            throw new RuntimeException("检查文件是否存在时出错: " + objectName, e);
        }
    }
    /**
     * 生成预签名 URL（用于临时公开访问，例如给前端展示图片/视频）
     *
     * @param objectName 对象名
     * @param expires    过期时间（秒），例如 3600 = 1小时
     * @return 可直接访问的 URL
     */
    public static String getPresignedUrl(String objectName, int expires) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .expiry(expires, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("生成预签名 URL 失败: " + objectName, e);
        }
    }
    public static void removeObject(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(BUCKET_NAME)
                .object(objectName)
                .build());
    }
}