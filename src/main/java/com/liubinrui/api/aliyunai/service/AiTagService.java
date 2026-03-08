package com.liubinrui.api.aliyunai.service;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiTagService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    // 模型名称：推荐使用 qwen-vl-max (效果最好) 或 qwen-vl-plus (性价比高)
    private static final String MODEL = "qwen-vl-max";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    // 配置 OkHttpClient 增加超时时间，防止 AI 生成超时
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // AI 可能需要几秒思考
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 根据本地图片文件自动生成标签
     *
     * @param本地图片文件
     * @return 标签列表
     */
    public List<String> generateTags(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件为空");
        }

        try (InputStream inputStream = file.getInputStream()) {
            // 1. 直接从流读取字节并转 Base64
            byte[] fileContent = inputStream.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(fileContent);

            // 动态判断 MIME 类型 (image/jpeg, image/png 等)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = "image/jpeg"; // 默认 fallback
            }
            String imageDataUrl = "data:" + contentType + ";base64," + base64Image;

            // 2. 构造 Prompt
            String prompt = "请分析这张图片，提取 5-8 个最能代表图片内容的关键词标签。" +
                    "要求：1. 仅输出标签词，用中文逗号或英文逗号分隔；2. 不要包含'标签：'等前缀；3. 不要输出任何解释性文字。";

            // 3. 构建请求体
            JSONObject requestBody = buildQwenVlRequestBody(imageDataUrl, prompt);

            // 4. 发送请求 (复用原有的 HTTP 逻辑)
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toJSONString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new IOException("API 请求失败 [" + response.code() + "]: " + responseBody);
                }
                return parseTagsFromResponse(responseBody);
            }

        } catch (IOException e) {
            throw new RuntimeException("AI 打标服务调用失败: " + e.getMessage(), e);
        }
    }
    public List<String> generateTagss(File imageFile) {
        if (!imageFile.exists()) {
            throw new IllegalArgumentException("图片文件不存在: " + imageFile.getAbsolutePath());
        }

        try {
            // 1. 读取并转换图片为 Base64
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(fileContent);
            // 关键修正：DashScope 要求图片数据必须带 MIME 类型前缀
            String imageDataUrl = "data:image/jpeg;base64," + base64Image;
            // 如果是 PNG 等其他格式，实际项目中可根据文件名后缀动态判断 mime type，这里默认 jpeg 兼容大多数情况

            // 2. 构造 Prompt
            String prompt = "请分析这张图片，提取 5-8 个最能代表图片内容的关键词标签。" +
                    "要求：1. 仅输出标签词，用中文逗号或英文逗号分隔；2. 不要包含'标签：'、'关键词：'等前缀；3. 不要输出任何解释性文字。";

            // 3. 构建符合 Qwen-VL 标准的请求体 JSON
            JSONObject requestBody = buildQwenVlRequestBody(imageDataUrl, prompt);

            // 4. 发送 HTTP POST 请求
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toJSONString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    throw new IOException("API 请求失败 [" + response.code() + "]: " + responseBody);
                }

                return parseTagsFromResponse(responseBody);
            }

        } catch (IOException e) {
            throw new RuntimeException("AI 打标服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建符合 DashScope Qwen-VL 接口规范的 JSON
     */
    private JSONObject buildQwenVlRequestBody(String imageDataUrl, String prompt) {
        JSONObject root = new JSONObject();
        root.put("model", MODEL);

        // 构造 input 对象
        JSONObject input = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");

        // 构造 content 数组 (多模态核心：混合图片和文本)
        JSONArray contentArray = new JSONArray();

        // 元素 1: 图片
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image");
        imageContent.put("image", imageDataUrl);
        contentArray.add(imageContent);

        // 元素 2: 文本 Prompt
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        contentArray.add(textContent);

        message.put("content", contentArray);
        messages.add(message);
        input.put("messages", messages);

        root.put("input", input);

        // 可选参数配置
        JSONObject parameters = new JSONObject();
        parameters.put("result_format", "message"); // 确保返回格式包含 message 字段
        root.put("parameters", parameters);

        return root;
    }

    /**
     * 解析 API 返回的 JSON，提取标签列表
     */
    private List<String> parseTagsFromResponse(String jsonResponse) {
        try {
            JSONObject json = JSONObject.parseObject(jsonResponse);

            // 检查是否有错误码
            if (json.containsKey("code")) {
                throw new RuntimeException("API 返回错误: " + json.getString("message"));
            }

            // 标准路径: output -> choices[0] -> message -> content
            String content = json.getJSONObject("output")
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            if (content == null || content.trim().isEmpty()) {
                return Collections.emptyList();
            }

            // 清洗数据：按中英文逗号分割，去除首尾空格，过滤空字符串
            return Arrays.stream(content.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("解析 AI 返回结果失败。原始返回: " + jsonResponse, e);
        }
    }
}
