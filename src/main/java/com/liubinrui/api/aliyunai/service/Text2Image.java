package com.liubinrui.api.aliyunai.service;

// Copyright (c) Alibaba, Inc. and its affiliates.

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.dashscope.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
@Service
public class Text2Image {
    static {
        // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    // 若没有配置环境变量，请用百炼API Key将下行替换为：static String apiKey = "sk-xxx"
    static String apiKey = ("sk-00c4b9314bf547aba4f458ec8ac75c83");

    public String basicCall(String prompt) throws ApiException, NoApiKeyException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("negative_prompt", " ");
        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .apiKey(apiKey)
                        // 当前仅qwen-image-plus、qwen-image模型支持异步接口
                        .model("qwen-image-plus")
                        .prompt(prompt)
                        .n(1)
                        .size("1664*928")
                        .parameters(parameters)
                        .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();
        ImageSynthesisResult result = null;
        try {
            System.out.println("---同步调用，请等待任务执行----");
            result = imageSynthesis.call(param);
        } catch (ApiException | NoApiKeyException e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println(JsonUtils.toJson(result));
        String imageUrl=null;
        if (result != null && result.getOutput() != null && result.getOutput().getResults() != null && !result.getOutput().getResults().isEmpty()) {
            System.out.println("图片生成成功！");
            // 获取列表中的第一个元素，它的类型是 Map<String, Object>
            Map<String, String> firstResult = result.getOutput().getResults().get(0);
            // 从 Map 中取出 url 字段
            if (firstResult.containsKey("url")) {
                // 强制转换为 String
                imageUrl = (String) firstResult.get("url");

            }
        }
        return imageUrl;
    }


//    public static void main(String[] args) {
//        try {
//            basicCall(prompt);
//        } catch (ApiException | NoApiKeyException e) {
//            System.out.println(e.getMessage());
//        }
//    }
}
