package com.contractlens.service.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
public class PromptSafetyService {

    private static final Pattern ACTION = Pattern.compile("(输出|显示|打印|告诉我|给我|泄露|导出|提供|列出)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET = Pattern.compile("(system\\s*prompt|prompt|提示词|系统提示|jwt\\s*_?\\s*secret|jwt|dashscope|api\\s*key|apikey|application\\s*-?\\s*dev\\.yml|application\\.yml|环境变量|配置文件|数据库\\s*密码|mysql|neo4j|chroma)", Pattern.CASE_INSENSITIVE);

    public void checkOrThrow(String userMessage) {
        if (!StringUtils.hasText(userMessage)) return;
        String text = userMessage.trim();
        if (ACTION.matcher(text).find() && TARGET.matcher(text).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求包含敏感信息索取内容，已拒绝");
        }
    }
}

