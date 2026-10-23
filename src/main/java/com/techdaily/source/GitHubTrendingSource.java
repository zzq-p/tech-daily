package com.techdaily.source;

import com.techdaily.config.TechDailyProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 抓取 GitHub Trending(https://github.com/trending)每日热门仓库。
 * 页面为服务端渲染,直接用 Jsoup 解析;GitHub 改版时只需调整本类选择器。
 */
@Component
public class GitHubTrendingSource {

    private static final Logger log = LoggerFactory.getLogger(GitHubTrendingSource.class);

    private final TechDailyProperties props;

    public GitHubTrendingSource(TechDailyProperties props) {
        this.props = props;
    }

    public List<FetchedItem> fetch() {
        List<FetchedItem> items = new ArrayList<>();
        for (String lang : props.fetch().githubLanguages()) {
            String url = lang == null || lang.isBlank()
                    ? "https://github.com/trending?since=daily"
                    : "https://github.com/trending/" + lang.trim() + "?since=daily";
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15_000)
                        .get();
                int count = 0;
                for (Element article : doc.select("article.Box-row")) {
                    Optional<FetchedItem> item = parse(article);
                    if (item.isPresent()) {
                        items.add(item.get());
                        count++;
                    }
                }
                log.info("GitHub Trending [{}] 抓到 {} 个仓库", lang.isBlank() ? "全部" : lang, count);
            } catch (Exception e) {
                log.warn("抓取 GitHub Trending 失败: {}", url, e);
            }
        }
        return items;
    }

    private Optional<FetchedItem> parse(Element article) {
        Element link = article.selectFirst("h2 a");
        if (link == null) {
            return Optional.empty();
        }
        String href = link.attr("href");
        if (href == null || !href.startsWith("/") || href.length() < 2) {
            return Optional.empty();
        }
        String repo = href.substring(1);
        String url = "https://github.com/" + repo;
        String description = textOf(article.selectFirst("p"));
        String language = textOf(article.selectFirst("[itemprop=programmingLanguage]"));
        long starsToday = 0;
        for (Element span : article.select("span")) {
            String t = span.text();
            if (t != null && t.contains("stars today")) {
                starsToday = parseLong(t.replace("stars today", "").trim());
            }
        }
        if (starsToday == 0) {
            Element starsLink = article.selectFirst("a[href$='/stargazers']");
            if (starsLink != null) {
                starsToday = parseLong(starsLink.text().replace(",", ""));
            }
        }
        return Optional.of(new FetchedItem("GITHUB", repo, url, description, language, starsToday));
    }

    private String textOf(Element el) {
        return el == null ? "" : el.text();
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
