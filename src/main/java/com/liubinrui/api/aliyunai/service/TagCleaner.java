package com.liubinrui.api.aliyunai.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagCleaner {
    public List<String> cleanTags(List<String> tags) {
        // 1. 基础校验
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 【关键修改】取出 List 中的第一个元素（它是一个 JSON 格式的字符串）
        String jsonStr = tags.get(0);

        // 防御性检查：如果字符串为空或只是空白
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 3. 解析这个 JSON 字符串 -> 得到 JSONArray
            // 此时 jsonStr 是 "[{\"text\":\"洞穴,光束...\"}]"
            // 解析后 jsonArray 是 [{"text":"洞穴,光束..."}]
            JSONArray jsonArray = JSONArray.parseArray(jsonStr);

            if (jsonArray.isEmpty()) {
                return new ArrayList<>();
            }

            // 4. 获取第一个对象
            Object firstObj = jsonArray.get(0);

            String textContent;
            if (firstObj instanceof JSONObject) {
                // 正常情况：提取 text 字段
                textContent = ((JSONObject) firstObj).getString("text");
            } else if (firstObj instanceof String) {
                // 兼容情况：如果数组里直接就是字符串 "洞穴,光束..." (没有 text 字段包裹)
                textContent = (String) firstObj;
            } else {
                // 其他未知格式
                return new ArrayList<>();
            }

            if (textContent == null || textContent.trim().isEmpty()) {
                return new ArrayList<>();
            }

            // 5. 分割字符串 (支持中文逗号，和英文逗号) 并去除空格
            return Arrays.stream(textContent.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("JSON 解析失败，尝试作为纯文本处理: " + e.getMessage());

            // 兜底策略：如果 JSON 解析失败，直接把整个字符串当作文本分割（防止程序崩溃）
            return Arrays.stream(jsonStr.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }
}
