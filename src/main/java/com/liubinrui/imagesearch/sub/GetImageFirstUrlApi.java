package com.liubinrui.imagesearch.sub;

import com.liubinrui.exception.BusinessException;
import com.liubinrui.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GetImageFirstUrlApi {

    public static String getImageFirstUrl(String url) {
        try {
            // 使用 Jsoup 获取 HTML 内容
            // 注意：确保你的 pom.xml 或 build.gradle 中已引入 jsoup 依赖
            Document document = Jsoup.connect(url)
                    .timeout(5000)
                    .userAgent("Mozilla/5.0") // 建议加上 User-Agent，防止被部分网站拦截
                    .get();

            // 获取所有 <script> 标签
            // getElementsByTag 返回的是 org.jsoup.select.Elements
            Elements scriptElements = document.getElementsByTag("script");

            // 遍历找到包含 `firstUrl` 的脚本内容
            for (Element script : scriptElements) {
                // 注意：如果 script 标签是外部引用 (src="...")，script.html() 可能为空
                // 这里只处理内联脚本
                String scriptContent = script.html();

                if (scriptContent != null && scriptContent.contains("\"firstUrl\"")) {
                    // 正则表达式提取 firstUrl 的值
                    // 优化正则：考虑转义字符的情况，非贪婪匹配
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if (matcher.find()) {
                        String firstUrl = matcher.group(1);
                        // 处理常见的 JSON 转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        firstUrl = firstUrl.replace("\\\"", "\""); // 以防万一
                        return firstUrl;
                    }
                }
            }

            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到 url");
        } catch (BusinessException e) {
            // 业务异常直接抛出，不要包装
            throw e;
        } catch (Exception e) {
            log.error("搜索失败: {}", url, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }
}