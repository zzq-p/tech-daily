package com.techdaily.schedule;

import com.techdaily.digest.DigestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时生成日报。cron 在 application.yml 的 techdaily.schedule.cron 配置。
 */
@Component
public class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final DigestService digestService;

    public DigestScheduler(DigestService digestService) {
        this.digestService = digestService;
    }

    @Scheduled(cron = "${techdaily.schedule.cron}")
    public void run() {
        try {
            DigestService.Result result = digestService.generate();
            log.info("定时日报完成: {} 收录 {} 条", result.date(), result.articles());
        } catch (Exception e) {
            log.error("定时日报生成失败", e);
        }
    }
}
