package org.acme.util;

import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SubtitleCatAPI {
    // 复用连接池提升性能（参考网页4、5、8）
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(new UserAgentInterceptor()) // 自定义UA拦截器
        .build();

    /**
     * 获取字幕搜索结果
     * @param keyword 搜索关键词（如SONE-562）
     * @return 包含前三条字幕链接的HTML字符串
     */
    public static String searchSubtitles(String keyword) throws IOException {
        // 构建动态URL（参考网页7参数处理）
        HttpUrl url = new HttpUrl.Builder()
            .scheme("https")
            .host("subtitlecat.com")
            .addPathSegment("index.php")
            .addQueryParameter("search", keyword) // 自动编码特殊字符
            .build();

        // 创建请求（参考网页5、6的GET请求示例）
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP错误码: " + response.code());
            }
            // 解析HTML获取字幕链接（参考网页9、11的解析方案）
            return parseSubtitleLinks(response.body().string());
        }
    }

    /**
     * 解析HTML获取前三条字幕链接
     */
    private static String parseSubtitleLinks(String html) {
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("table.sub-table tbody tr td:first-child a");

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(3, links.size()); i++) {
            String href = links.get(i).attr("href");
            String text = links.get(i).text();
            result.append(String.format("<a href='%s'>%s</a><br>",
                "https://subtitlecat.com" + href, text));
        }
        return result.toString();
    }

    /** 自定义UA拦截器（参考网页8、11的反爬建议） */
    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request()
                .newBuilder()
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
            return chain.proceed(request);
        }
    }
}
