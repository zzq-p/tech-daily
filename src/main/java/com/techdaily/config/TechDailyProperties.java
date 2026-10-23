package com.techdaily.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "techdaily")
public record TechDailyProperties(Fetch fetch, Llm llm, Push push, Schedule schedule) {

    public TechDailyProperties {
        fetch = fetch != null ? fetch : new Fetch(0, 0, 0, null);
        llm = llm != null ? llm : new Llm(null, null, null);
        push = push != null ? push : new Push(null, null, null);
        schedule = schedule != null ? schedule : new Schedule(null);
    }

    public record Fetch(int topN, int selectN, int minScore, List<String> githubLanguages) {
        public Fetch {
            if (topN <= 0) topN = 30;
            if (selectN <= 0) selectN = 12;
            if (minScore < 0) minScore = 0;
            if (githubLanguages == null || githubLanguages.isEmpty()) githubLanguages = List.of("");
        }
    }

    public record Llm(String baseUrl, String apiKey, String model) {
        public Llm {
            baseUrl = baseUrl == null || baseUrl.isBlank()
                    ? "https://open.bigmodel.cn/api/paas/v4" : baseUrl;
            model = model == null || model.isBlank() ? "glm-4.5-flash" : model;
            apiKey = apiKey == null ? "" : apiKey;
        }

        public boolean enabled() {
            return !apiKey.isBlank();
        }
    }

    public record Push(String channel, String wecomWebhook, String serverchanKey) {
        public Push {
            channel = channel == null || channel.isBlank() ? "none" : channel;
            wecomWebhook = wecomWebhook == null ? "" : wecomWebhook;
            serverchanKey = serverchanKey == null ? "" : serverchanKey;
        }
    }

    public record Schedule(String cron) {
        public Schedule {
            cron = cron == null || cron.isBlank() ? "0 30 8 * * *" : cron;
        }
    }
}
