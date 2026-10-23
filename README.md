# AI 技术日报 (tech-daily)

每天定时抓取 **GitHub Trending**(支持按语言过滤)和 **Hacker News** 热帖,用 LLM 总结成一份中文技术日报,推送到微信,并在网页上存档可查。

## 快速开始

```bash
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

打开 http://127.0.0.1:8090 ,点「立即生成今日日报」即可看到当天内容。

- 没配 LLM key 时会生成「纯列表模式」日报(标题 + 链接),依然可用
- 配好 key 后自动升级为 AI 中文摘要版

## 配置(application.yml 或环境变量)

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| `techdaily.llm.api-key` | `TECHDAILY_LLM_API_KEY` | OpenAI 兼容接口的 key(默认智谱 GLM) |
| `techdaily.llm.base-url` | — | 默认 `https://open.bigmodel.cn/api/paas/v4`,可换成任意 OpenAI 兼容地址 |
| `techdaily.llm.model` | — | 默认 `glm-4.5-flash`,按你开通的模型调整 |
| `techdaily.push.channel` | — | `none` / `wecom` / `serverchan` |
| `techdaily.push.wecom-webhook` | `TECHDAILY_WECOM_WEBHOOK` | 企业微信群机器人地址 |
| `techdaily.push.serverchan-key` | `TECHDAILY_SERVERCHAN_KEY` | Server酱 SendKey(推送到个人微信) |
| `techdaily.schedule.cron` | — | 默认 `0 30 8 * * *`(每天 08:30) |
| `techdaily.fetch.top-n` | — | 检查 HN 前 N 条,默认 30 |
| `techdaily.fetch.min-score` | — | HN 热帖最低分数,默认 50 |
| `techdaily.fetch.select-n` | — | 日报收录条数,默认 12 |
| `techdaily.fetch.github-languages` | — | GitHub Trending 语言过滤,`["", "java"]` = 全站 + Java |

## 推送到微信的两种方式

**企业微信群机器人(推荐,2 分钟搞定)**
1. 建一个只有自己的企业微信群 → 群设置 → 群机器人 → 添加
2. 复制 webhook 地址,设置 `TECHDAILY_WECOM_WEBHOOK` 环境变量,`channel` 改为 `wecom`

**Server酱(推送到个人微信)**
1. 到 https://sct.ftqq.com 微信扫码登录,拿到 SendKey
2. 设置 `TECHDAILY_SERVERCHAN_KEY`,`channel` 改为 `serverchan`

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 历史日报页面 |
| POST | `/generate` | 立即生成并跳回首页(页面按钮) |
| POST | `/api/generate` | 立即生成,返回 JSON |
| GET | `/api/reports` | 历史日报 JSON |

## 技术栈

Spring Boot 4 · Java 21 · H2(文件库,零部署)· JPA · Jsoup · Thymeleaf · commonmark

数据存在 `./data/techdaily.mv.db`,构建产物在 `build/libs/tech-daily.jar`,可用 `java -jar` 部署。

## 说明

- **数据源**:GitHub Trending(全站 + 按语言)、Hacker News 热帖(官方 Firebase API,国内网络不稳时自动切换 Algolia 备用源,带 5s/10s 超时快速失败)
- **条目均衡**:GitHub 的"今日 star 数"和 HN 的 points 量级不同,日报按来源各取一半名额,避免单一来源刷屏
- **GitHub 改版**只影响 `GitHubTrendingSource` 里的 Jsoup 选择器,改那一处即可
