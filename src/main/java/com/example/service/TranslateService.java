package com.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 翻译服务 - 使用微软Edge浏览器的免费翻译API，无需密钥
 */
public class TranslateService {
    
    // 微软翻译API
    private static final String MS_AUTH_URL = "https://edge.microsoft.com/translate/auth";
    private static final String MS_TRANSLATE_URL = "https://api-edge.cognitive.microsofttranslator.com/translate";
    
    /**
     * 检查是否已配置API（微软翻译不需要配置）
     */
    public static boolean isConfigured() {
        return true;
    }
    
    /**
     * 显示配置对话框（微软翻译不需要配置）
     */
    public static boolean showConfigDialog() {
        return true;
    }
    
    /**
     * 获取微软翻译认证Token
     */
    private static String getMsAuthToken() throws Exception {
        URL url = new URL(MS_AUTH_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        return response.toString();
    }
    
    /**
     * 翻译文本
     * @param text 要翻译的文本
     * @param from 源语言 (en, zh-Hans, ja等，空字符串表示自动检测)
     * @param to 目标语言
     * @return 翻译结果
     */
    public static String translate(String text, String from, String to) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // 获取认证Token
        String token = getMsAuthToken();
        
        // 构建URL
        String urlStr = MS_TRANSLATE_URL + "?api-version=3.0&to=" + to;
        if (from != null && !from.isEmpty()) {
            urlStr += "&from=" + from;
        }
        urlStr += "&includeSentenceLength=true";
        
        // 构建请求体
        String requestBody = "[{\"text\":\"" + escapeJson(text) + "\"}]";
        
        // 发送请求
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        
        // 读取响应
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        // 解析JSON响应
        return parseMsTranslateResult(response.toString());
    }
    
    /**
     * 自动检测语言并翻译
     * 如果是中文则翻译成英文，否则翻译成中文
     */
    public static String autoTranslate(String text) throws Exception {
        // 简单判断是否包含中文
        boolean hasChinese = text.matches(".*[\\u4e00-\\u9fa5]+.*");
        
        if (hasChinese) {
            return translate(text, "zh-Hans", "en");
        } else {
            return translate(text, "", "zh-Hans");
        }
    }
    
    /**
     * 转义JSON特殊字符
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 解析微软翻译结果JSON
     */
    private static String parseMsTranslateResult(String json) throws Exception {
        // 提取翻译结果 - 格式: [{"translations":[{"text":"翻译结果","to":"zh-Hans"}]}]
        StringBuilder result = new StringBuilder();
        int start = 0;
        
        while ((start = json.indexOf("\"text\":\"", start)) != -1) {
            start += 8;
            int end = findJsonStringEnd(json, start);
            if (end != -1) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                String text = json.substring(start, end);
                // 解码转义字符
                text = decodeJsonString(text);
                result.append(text);
                start = end;
            } else {
                break;
            }
        }
        
        if (result.length() == 0) {
            throw new Exception("无法解析翻译结果: " + json);
        }
        
        return result.toString();
    }
    
    /**
     * 找到JSON字符串的结束位置
     */
    private static int findJsonStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++; // 跳过转义字符
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 解码JSON字符串中的转义字符
     */
    private static String decodeJsonString(String str) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                switch (next) {
                    case 'n': result.append('\n'); i += 2; break;
                    case 'r': result.append('\r'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    case 'u':
                        if (i + 5 < str.length()) {
                            try {
                                int code = Integer.parseInt(str.substring(i + 2, i + 6), 16);
                                result.append((char) code);
                                i += 6;
                            } catch (NumberFormatException e) {
                                result.append(c);
                                i++;
                            }
                        } else {
                            result.append(c);
                            i++;
                        }
                        break;
                    default:
                        result.append(c);
                        i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
}
