package com.techdaily.llm;

import tools.jackson.databind.JsonNode;
import com.techdaily.article.Article;
import com.techdaily.config.TechDailyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 调用 OpenAI 兼容接口(默认智谱 GLM)把当日热门条目写成一份中文技术日报。
 */
@Component
public class LlmSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmSummarizer.class);

    private static final String PROMPT_TEMPLATE = """
            你是一名资深技术编辑。下面是今天 GitHub Trending 和 Hacker News 上的热门项目/文章。
            请生成一份中文技术日报,Markdown 格式,要求:
            1. 开头是「## 今日速览」,用 2-3 句话概括今天的技术热点;
            2. 然后逐条输出,每条格式为:`序号. **名称** (来源/语言, 热度)` 加一行中文介绍,
               说明它是做什么的、为什么值得关注,不要逐字翻译标题;
            3. 结尾是「## 今日关键词」,用一行列出 3-5 个关键词。

            条目列表:
            """;

    private final TechDailyProperties props;
    private final RestClient rest;

    public LlmSummarizer(TechDailyProperties props) {
        this.props = props;
        this.rest = RestClient.create();
    }

    public String summarize(List<Article> articles) {
        StringBuilder list = new StringBuilder();
        for (int i = 1; i <= articles.size(); i++) {
            Article a = articles.get(i - 1);
            list.append(i).append(". [").append(a.getSource()).append("] ")
                    .append(a.getTitle());
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                list.append(" - ").append(a.getDescription());
            }
            list.append(" (热度:").append(a.getScore()).append(")\n");
        }

        Map<String, Object> body = Map.of(
                "model", props.llm().model(),
                "temperature", 0.6,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", PROMPT_TEMPLATE + list
                )));

        JsonNode resp = rest.post()
                .uri(props.llm().baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + props.llm().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null) {
            throw new IllegalStateException("LLM 接口返回为空");
        }
        JsonNode contentNode = resp.path("choices").path(0).path("message").path("content");
        String content = contentNode.isMissingNode() ? "" : contentNode.asString();
        if (content.isBlank()) {
            throw new IllegalStateException("LLM 未返回内容: " + resp);
        }
        log.info("LLM 日报生成成功,{} 字", content.length());
        return stripCodeFence(content.trim());
    }

    private String stripCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        String stripped = content.replaceFirst("^```[a-zA-Z]*\\s*", "");
        int fence = stripped.lastIndexOf("```");
        return fence >= 0 ? stripped.substring(0, fence).trim() : stripped.trim();
    }
}
