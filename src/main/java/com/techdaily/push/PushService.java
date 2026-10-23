package com.techdaily.push;

import com.techdaily.config.TechDailyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 把日报推送到微信。支持:
 * - wecom:     企业微信群机器人 webhook(在群里添加"群机器人"即可拿到地址)
 * - serverchan: Server酱(https://sct.ftqq.com ,推送到个人微信)
 */
@Component
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final TechDailyProperties props;
    private final RestClient rest = RestClient.create();

    public PushService(TechDailyProperties props) {
        this.props = props;
    }

    public void push(String title, String markdown) {
        String channel = props.push().channel();
        try {
            switch (channel) {
                case "wecom" -> pushWecom(markdown);
                case "serverchan" -> pushServerChan(title, markdown);
                default -> log.info("push.channel=none,跳过推送(可在 application.yml 中配置)");
            }
        } catch (Exception e) {
            log.error("推送失败(channel={})", channel, e);
        }
    }

    private void pushWecom(String markdown) {
        String webhook = props.push().wecomWebhook();
        if (webhook.isBlank()) {
            log.warn("push.channel=wecom 但未配置 wecom-webhook,跳过");
            return;
        }
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", truncateUtf8(markdown, 4000)));
        String resp = rest.post()
                .uri(webhook)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("企业微信推送完成: {}", resp);
    }

    private void pushServerChan(String title, String markdown) {
        String key = props.push().serverchanKey();
        if (key.isBlank()) {
            log.warn("push.channel=serverchan 但未配置 serverchan-key,跳过");
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", title);
        form.add("desp", markdown);
        String resp = rest.post()
                .uri("https://sctapi.ftqq.com/" + key + ".send")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        log.info("Server酱 推送完成: {}", resp);
    }

    /**
     * 企业微信 markdown 消息上限 4096 字节,按 UTF-8 字节安全截断。
     */
    private String truncateUtf8(String s, int maxBytes) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int cut = maxBytes;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
            cut--;
        }
        return new String(bytes, 0, cut, StandardCharsets.UTF_8) + "\n\n> (内容过长,已截断,完整版见网页)";
    }
}
