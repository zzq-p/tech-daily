package com.techdaily.source;

import com.techdaily.config.TechDailyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 抓取 Hacker News 热帖。
 * 主源为官方 Firebase API;国内网络对其不稳定,失败时自动切换 Algolia 备用源。
 */
@Component
public class HackerNewsSource {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsSource.class);
    private static final String FIREBASE_API = "https://hacker-news.firebaseio.com/v0";
    private static final String ALGOLIA_API = "https://hn.algolia.com/api/v1";

    private final TechDailyProperties props;
    private final RestClient rest;

    public HackerNewsSource(TechDailyProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<FetchedItem> fetch() {
        try {
            return fetchFirebase();
        } catch (Exception e) {
            log.warn("HN 官方 API 失败({}),切换 Algolia 备用源", e.getMessage());
            return fetchAlgolia();
        }
    }

    private List<FetchedItem> fetchFirebase() {
        Integer[] ids = rest.get()
                .uri(FIREBASE_API + "/topstories.json")
                .retrieve()
                .body(Integer[].class);
        if (ids == null) {
            return List.of();
        }
        int limit = Math.min(props.fetch().topN(), ids.length);
        List<FetchedItem> items = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            try {
                HnItem item = rest.get()
                        .uri(FIREBASE_API + "/item/" + ids[i] + ".json")
                        .retrieve()
                        .body(HnItem.class);
                if (item == null || item.title() == null) {
                    continue;
                }
                if (item.score() != null && item.score() < props.fetch().minScore()) {
                    continue;
                }
                items.add(toItem(item.title(), item.url(),
                        "https://news.ycombinator.com/item?id=" + item.id(),
                        item.score() == null ? 0 : item.score()));
            } catch (Exception e) {
                log.debug("跳过 HN 条目 {}: {}", ids[i], e.getMessage());
            }
        }
        log.info("Hacker News(官方源)抓到 {} 条热帖", items.size());
        return items;
    }

    private List<FetchedItem> fetchAlgolia() {
        AlgoliaResp resp = rest.get()
                .uri(ALGOLIA_API + "/search?tags=front_page&hitsPerPage=" + props.fetch().topN())
                .retrieve()
                .body(AlgoliaResp.class);
        List<FetchedItem> items = new ArrayList<>();
        if (resp == null || resp.hits() == null) {
            return items;
        }
        for (HnHit hit : resp.hits()) {
            if (hit.title() == null) {
                continue;
            }
            long score = hit.points() == null ? 0 : hit.points();
            if (score < props.fetch().minScore()) {
                continue;
            }
            items.add(toItem(hit.title(), hit.url(),
                    "https://news.ycombinator.com/item?id=" + hit.objectID(), score));
        }
        log.info("Hacker News(Algolia 备用源)抓到 {} 条热帖", items.size());
        return items;
    }

    private FetchedItem toItem(String title, String linkUrl, String discussionUrl, long score) {
        String url = linkUrl != null && !linkUrl.isBlank() ? linkUrl : discussionUrl;
        return new FetchedItem("HACKERNEWS", title, url, "", "", score);
    }

    record HnItem(Long id, String title, String url, Integer score, String type) {
    }

    record AlgoliaResp(List<HnHit> hits) {
    }

    record HnHit(String title, String url, Integer points, String objectID) {
    }
}
