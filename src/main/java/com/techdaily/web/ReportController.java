package com.techdaily.web;

import com.techdaily.digest.DigestService;
import com.techdaily.report.DailyReport;
import com.techdaily.report.DailyReportRepository;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class ReportController {

    private final DailyReportRepository reports;
    private final DigestService digestService;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public ReportController(DailyReportRepository reports, DigestService digestService) {
        this.reports = reports;
        this.digestService = digestService;
    }

    public record ReportView(LocalDate date, int articleCount, LocalDateTime createdAt, String html) {
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ReportView> views = reports.findAllByOrderByReportDateDesc().stream()
                .map(r -> new ReportView(r.getReportDate(), r.getArticleCount(),
                        r.getCreatedAt(), htmlRenderer.render(markdownParser.parse(
                        r.getContent() == null ? "" : r.getContent()))))
                .toList();
        model.addAttribute("reports", views);
        return "reports";
    }

    @PostMapping("/generate")
    public String generateAndRedirect() {
        digestService.generate();
        return "redirect:/";
    }

    @PostMapping("/api/generate")
    @ResponseBody
    public Map<String, Object> generate() {
        DigestService.Result result = digestService.generate();
        return Map.of("ok", true,
                "date", result.date(),
                "articles", result.articles(),
                "summary", result.summary(),
                "pushChannel", result.pushChannel());
    }

    @GetMapping("/api/reports")
    @ResponseBody
    public List<DailyReport> apiReports() {
        return reports.findAllByOrderByReportDateDesc();
    }
}
