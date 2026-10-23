package com.techdaily.digest;

import com.techdaily.article.Article;
import com.techdaily.article.ArticleRepository;
import com.techdaily.config.TechDailyProperties;
import com.techdaily.llm.LlmSummarizer;
import com.techdaily.push.PushService;
import com.techdaily.report.DailyReport;
import com.techdaily.report.DailyReportRepository;
import com.techdaily.source.FetchedItem;
import com.techdaily.source.GitHubTrendingSource;
import com.techdaily.source.HackerNewsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日报编排:抓取 → 去重入库 → 选热度 Top N → LLM 摘要 → 存档 → 推送。
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final GitHubTrendingSource github;
    private final HackerNewsSource hackerNews;
    private final ArticleRepository articles;
    private final DailyReportRepository reports;
    private final LlmSummarizer summarizer;
    private final PushService pushService;
    private final TechDailyProperties props;

    public DigestService(GitHubTrendingSource github, HackerNewsSource hackerNews,
                         ArticleRepository articles, DailyReportRepository reports,
                         LlmSummarizer summarizer, PushService pushService,
                         TechDailyProperties props) {
        this.github = github;
        this.hackerNews = hackerNews;
        this.articles = articles;
        this.reports = reports;
        this.summarizer = summarizer;
        this.pushService = pushService;
        this.props = props;
    }

    public Result generate() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<FetchedItem> fetched = new ArrayList<>();
        fetched.addAll(github.fetch());
        fetched.addAll(hackerNews.fetch());
        log.info("共抓到 {} 条", fetched.size());

        List<Article> todayArticles = upsert(fetched, now);

        List<Article> selected = selectBalanced(todayArticles, props.fetch().selectN());

        String content = buildContent(today, selected);

        DailyReport report = reports.findByReportDate(today).orElseGet(() -> {
            DailyReport r = new DailyReport();
            r.setReportDate(today);
            return r;
        });
        report.setContent(content);
        report.setArticleCount(selected.size());
        report.setCreatedAt(now);
        report = reports.save(report);

        pushService.push("AI 技术日报 " + today, content);
        log.info("日报 {} 完成,收录 {} 条", today, selected.size());
        return new Result(today.toString(), selected.size(),
                props.llm().enabled() ? "摘要:" + props.llm().model() : "未配置 LLM key,纯列表模式",
                props.push().channel());
    }

    private List<Article> upsert(List<FetchedItem> fetched, LocalDateTime now) {
        List<Article> result = new ArrayList<>();
        for (FetchedItem f : fetched) {
            if (f.url() == null || f.url().isBlank() || f.title() == null || f.title().isBlank()) {
                continue;
            }
            Article a = articles.findByUrl(f.url()).orElseGet(() -> {
                Article n = new Article();
                n.setSource(f.source());
                n.setTitle(f.title());
                n.setUrl(f.url());
                n.setFirstSeenAt(now);
                return n;
            });
            a.setDescription(f.description());
            a.setLanguage(f.language());
            a.setScore(f.score());
            a.setLastSeenAt(now);
            result.add(articles.save(a));
        }
        return result;
    }

    /**
     * 按来源均衡选条目:GitHub 的"今日 star 数"(数百~数千)和 HN 的 points
     * (数十~数百)量级差很大,直接全局排序会偏向单一来源,
     * 因此每个来源先各取均等名额,剩余名额再按全局热度补齐。
     */
    private List<Article> selectBalanced(List<Article> pool, int n) {
        Map<String, List<Article>> bySource = new LinkedHashMap<>();
        for (Article a : pool) {
            bySource.computeIfAbsent(a.getSource(), k -> new ArrayList<>()).add(a);
        }
        List<Article> selected = new ArrayList<>();
        List<Article> leftovers = new ArrayList<>();
        int perSource = Math.max(1, n / bySource.size());
        for (List<Article> list : bySource.values()) {
            list.sort(Comparator.comparingLong(Article::getScore).reversed());
            selected.addAll(list.subList(0, Math.min(perSource, list.size())));
            if (list.size() > perSource) {
                leftovers.addAll(list.subList(perSource, list.size()));
            }
        }
        leftovers.sort(Comparator.comparingLong(Article::getScore).reversed());
        for (Article a : leftovers) {
            if (selected.size() >= n) {
                break;
            }
            selected.add(a);
        }
        selected.sort(Comparator.comparingLong(Article::getScore).reversed());
        return selected;
    }

    private String buildContent(LocalDate today, List<Article> selected) {
        if (selected.isEmpty()) {
            return "# AI 技术日报 " + today + "\n\n今天没有抓到任何条目,检查一下网络或数据源配置。";
        }
        if (props.llm().enabled()) {
            try {
                return summarizer.summarize(selected);
            } catch (Exception e) {
                log.error("LLM 摘要失败,降级为纯列表", e);
            }
        }
        StringBuilder sb = new StringBuilder("# AI 技术日报 ").append(today).append("\n\n");
        for (int i = 1; i <= selected.size(); i++) {
            Article a = selected.get(i - 1);
            sb.append(i).append(". **").append(a.getTitle()).append("** (`")
                    .append(a.getSource()).append("`, 热度 ").append(a.getScore()).append(")\n");
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                sb.append("   ").append(a.getDescription()).append("\n");
            }
            sb.append("   <").append(a.getUrl()).append(">\n\n");
        }
        sb.append("> 未生成 AI 摘要:请在环境变量 TECHDAILY_LLM_API_KEY 配置 LLM key 后重新生成。\n");
        return sb.toString();
    }

    public record Result(String date, int articles, String summary, String pushChannel) {
    }
}
