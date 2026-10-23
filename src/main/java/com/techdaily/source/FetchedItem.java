package com.techdaily.source;

/**
 * 从数据源抓到的一条热门条目。
 *
 * @param source      来源标识:GITHUB / HACKERNEWS
 * @param title       GitHub 为 owner/repo,HN 为帖子标题
 * @param url         唯一链接(同时作为去重键)
 * @param description 简介(GitHub 仓库描述)
 * @param language    编程语言(仅 GitHub 有)
 * @param score       热度:GitHub 为今日新增 star 数,HN 为 points
 */
public record FetchedItem(String source, String title, String url, String description,
                          String language, long score) {
}
