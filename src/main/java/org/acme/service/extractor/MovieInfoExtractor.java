package org.acme.service.extractor;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;

import org.acme.util.MovieInfoExtractorUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Movie Information Extractor
 * Extracts movie information from a website in multiple languages
 */
public class MovieInfoExtractor {

    // Create an instance of OkHttpClient
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    // 使用静态OkHttpClient实例提高性能



    /**
     * Extracts movie information for a specific language.
     *
     * @return A map containing the extracted movie information
     * @throws IOException If an error occurs during extraction
     */
    public static Map<String, Object> extractMovieInfoForLanguage(String url) throws IOException {

        // Pass null for movieId since we don't know it at this point
        return MovieInfoExtractorUtil.extractMovieInfoFromUrl(url, null);
    }

    /**
     * Internal method to extract movie information from a specified URL.
     *
     * @param url The full URL to extract information from
     * @return A map containing the extracted movie information
     * @throws IOException If an error occurs during extraction
     */
    private static Map<String, Object> extractMovieInfoFromUrl(String url) throws IOException {
        // Enhanced headers to better mimic a real browser
        Headers headers = new Headers.Builder()
            .add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("accept-language", "en-US,en;q=0.9")
            .add("cache-control", "max-age=0")
            .add("sec-ch-ua", "Google Chrome;v=119, Chromium;v=119, Not?A_Brand;v=24")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "macOS")
            .add("sec-fetch-dest", "document")
            .add("sec-fetch-mode", "navigate")
            .add("sec-fetch-site", "none")
            .add("sec-fetch-user", "?1")
            .add("upgrade-insecure-requests", "1")
            .add("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            .build();

        Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .build();

        // Create the result map
        Map<String, Object> result = new HashMap<>();

        // Attempt to fetch and parse the webpage
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch URL, status code: " + response.code());
            }

            // Parse the HTML
            String html = response.body().string();
            Document doc = Jsoup.parse(html);

            // Store DVD ID
            result.put("dvd_id", url.substring(url.lastIndexOf("/") + 1));

            // Extract metadata
            Map<String, Object> metadata = extractMetadata(doc);
            result.putAll(metadata);

            // Extract title
            String title = doc.select("h1.video-details-title").text();
            result.put("title", title);

            // Extract description
            String description = doc.select("div.video-details-description").text();
            result.put("description", description);

            // Extract cover URL
            Elements imgElements = doc.select("div.video-cover img");
            if (!imgElements.isEmpty()) {
                String coverUrl = imgElements.first().attr("src");
                result.put("cover_url", coverUrl);
            }

            // Extract M3U8 links
            result.put("m3u8_info", extractM3u8Info(doc));

            // Extract duration
            Elements durationElements = doc.select("div.video-info span:contains(分钟), div.video-info span:contains(seconds), div.video-info span:contains(分)");
            if (!durationElements.isEmpty()) {
                String durationText = durationElements.first().text();
                // Extract numeric value
                String duration = durationText.replaceAll("[^0-9]", "");
                result.put("duration", duration);
            }
        }

        return result;
    }

    /**
     * Extracts M3U8 information from the document
     */
    private static List<String> extractM3u8Info(Document doc) {
        List<String> m3u8Links = new ArrayList<>();

        // Direct extraction from video source tags
        Elements sources = doc.select("video source");
        for (Element source : sources) {
            String url = source.attr("src");
            if (url != null && !url.isEmpty() && url.contains(".m3u8")) {
                m3u8Links.add(url);
            }
        }

        return m3u8Links;
    }

    // 移除未使用的方法

    /**
     * Extracts metadata from the document based on various language patterns
     *
     * @param doc The HTML document
     * @return A map containing the extracted metadata
     */
    private static Map<String, Object> extractMetadata(Document doc) {
        Map<String, Object> metadata = new HashMap<>();

        // Define field patterns for different languages
        Map<String, String[]> fieldPatterns = new HashMap<>();

        // For series field patterns - All 13 languages
        fieldPatterns.put("series", new String[]{
            "系列", // Traditional & Simplified Chinese
            "Series", // English
            "シリーズ", // Japanese
            "시리즈", // Korean
            "Siri", // Malay
            "ซีรีส์", // Thai
            "Serie", // German & French
            "Thể loại", // Vietnamese
            "Serial", // Indonesian
            "Serye", // Filipino
            "Série" // Portuguese
        });

        // For maker field patterns - All 13 languages
        fieldPatterns.put("maker", new String[]{
            "片商", "制作商", // Traditional & Simplified Chinese
            "Maker", // English
            "メーカー", // Japanese
            "제작사", // Korean
            "Pembuat", // Malay
            "ผู้ผลิต", // Thai
            "Hersteller", // German
            "Fabricant", // French
            "Nhà sản xuất", // Vietnamese
            "Produsen", // Indonesian
            "Gumagawa", // Filipino
            "Fabricante" // Portuguese
        });

        // For label field patterns - All 13 languages
        fieldPatterns.put("label", new String[]{
            "發行商", "发行商", // Traditional & Simplified Chinese
            "Label", // English
            "レーベル", // Japanese
            "레이블", // Korean
            "Label", // Malay
            "ค่าย", // Thai
            "Label", // German
            "Étiquette", // French
            "Nhãn hiệu", // Vietnamese
            "Label", // Indonesian
            "Tatak", // Filipino
            "Gravadora" // Portuguese
        });

        // For release date field patterns - All 13 languages
        fieldPatterns.put("releaseDate", new String[]{
            "發行日期", "发行日期", // Traditional & Simplified Chinese
            "Release date", // English
            "配信開始日", // Japanese
            "출시일", // Korean
            "Tarikh keluaran", // Malay
            "วันที่วางจำหน่าย", // Thai
            "Veröffentlichungsdatum", // German
            "Date de sortie", // French
            "Ngày phát hành", // Vietnamese
            "Tanggal rilis", // Indonesian
            "Petsa ng Paglabas", // Filipino
            "Data de lançamento" // Portuguese
        });

        // For actor field patterns - All 13 languages
        fieldPatterns.put("actor", new String[]{
            "女優", "女优", // Traditional & Simplified Chinese
            "Actress", // English
            "女優", // Japanese
            "배우", // Korean
            "Pelakon", // Malay
            "นักแสดง", // Thai
            "Darstellerin", // German
            "Actrice", // French
            "Diễn viên", // Vietnamese
            "Aktris", // Indonesian
            "Artista", // Filipino
            "Atriz" // Portuguese
        });

        // For genre field patterns - All 13 languages
        fieldPatterns.put("genre", new String[]{
            "類別", "类别", // Traditional & Simplified Chinese
            "Genre", // English
            "ジャンル", // Japanese
            "장르", // Korean
            "Genre", // Malay
            "ประเภท", // Thai
            "Genre", // German
            "Genre", // French
            "Thể loại", // Vietnamese
            "Genre", // Indonesian
            "Uri", // Filipino
            "Gênero" // Portuguese
        });

        // Extract information from the HTML
        Elements infoRows = doc.select("div.video-meta-row");
        for (Element row : infoRows) {
            String label = row.select("div.video-meta-title").text().trim();
            String value = row.select("div.video-meta-data").text().trim();

            // Process series field
            if (containsAnyIgnoreCase(label, fieldPatterns.get("series"))) {
                metadata.put("series", value);
            }

            // Process maker field
            else if (containsAnyIgnoreCase(label, fieldPatterns.get("maker"))) {
                metadata.put("maker", value);
            }

            // Process label field
            else if (containsAnyIgnoreCase(label, fieldPatterns.get("label"))) {
                metadata.put("label", value);
            }

            // Process actor field
            else if (containsAnyIgnoreCase(label, fieldPatterns.get("actor"))) {
                metadata.put("actor", value);
            }

            // Process genre field
            else if (containsAnyIgnoreCase(label, fieldPatterns.get("genre"))) {
                List<String> genres = new ArrayList<>();
                Elements genreElements = row.select("div.video-meta-data a");
                for (Element genre : genreElements) {
                    genres.add(genre.text().trim());
                }
                metadata.put("genres", genres);
            }

            // Process release date field
            else if (containsAnyIgnoreCase(label, fieldPatterns.get("releaseDate"))) {
                // Check if there's a time element
                Element timeElement = row.select("div.video-meta-data time").first();
                String releaseDate;

                if (timeElement != null) {
                    // Get date from datetime attribute
                    releaseDate = timeElement.attr("datetime");
                    if (releaseDate != null && !releaseDate.isEmpty()) {
                        // Only use the date part in case there's a time component
                        if (releaseDate.contains("T")) {
                            releaseDate = releaseDate.split("T")[0];
                        }
                        metadata.put("release_date", releaseDate);
                    } else {
                        metadata.put("release_date", timeElement.text().trim());
                    }
                } else {
                    // Fall back to the text content
                    metadata.put("release_date", value);
                }
            }
        }

        // Extract website publication date
        Element pubDateElement = doc.select("meta[property=article:published_time]").first();
        if (pubDateElement != null) {
            String pubDate = pubDateElement.attr("content");
            if (pubDate != null && !pubDate.isEmpty()) {
                // Only use the date part
                if (pubDate.contains("T")) {
                    pubDate = pubDate.split("T")[0];
                }
                metadata.put("website_date", pubDate);
            }
        }

        return metadata;
    }

    /**
     * Checks if a string contains any of the provided patterns, ignoring case
     *
     * @param text The text to check
     * @param patterns The patterns to look for
     * @return true if the text contains any of the patterns, false otherwise
     */
    private static boolean containsAnyIgnoreCase(String text, String[] patterns) {
        if (text == null || patterns == null) {
            return false;
        }

        for (String pattern : patterns) {
            if (text.toLowerCase().contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }
}
