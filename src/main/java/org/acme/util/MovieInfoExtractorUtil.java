package org.acme.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;

import org.acme.entity.WatchUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.acme.entity.VideoSource;
import org.jboss.logging.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovieInfoExtractorUtil {
    
    private static final Logger logger = Logger.getLogger(MovieInfoExtractorUtil.class);
    // Create an instance of OkHttpClient with redirect handling
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)        // 确保HTTP重定向被处理
        .followSslRedirects(true)     // 确保HTTPS重定向被处理
        .addInterceptor(chain -> {    // 添加拦截器记录请求和响应
            Request request = chain.request();
            logger.info("发送请求: " + request.url());
            Response response = chain.proceed(request);
            logger.info("收到响应: " + response.code() + " " + response.message());
            logger.info("最终URL: " + response.request().url());
            return response;
        })
        .build();
        
    // Language codes and names based on the HTML snippet
    private static final Map<String, String> LANGUAGES = new HashMap<String, String>() {{
        // Default language (base URL)
        put("", "繁體中文");
        // Other languages
        put("cn", "简体中文");
        put("en", "English");
        put("ja", "日本語");
        put("ko", "한국의");
        put("ms", "Melayu");
        put("th", "ไทย");
        put("de", "Deutsch");
        put("fr", "Français");
        put("vi", "Tiếng Việt");
        put("id", "Bahasa Indonesia");
        put("fil", "Filipino");
        put("pt", "Português");
    }};
    
    // Base URL
    private static final String BASE_URL = "https://missav.ai";

    /**
     * Extracts movie information for a specific language.
     * 
     * @param dvdCode The DVD code of the movie
     * @param languageCode The language code (empty string for Traditional Chinese, "cn" for Simplified Chinese, etc.)
     * @return A map containing the extracted movie information
     * @throws IOException If an error occurs during extraction
     */
    public static Map<String, Object> extractMovieInfoForLanguage(String dvdCode, String languageCode) throws IOException {
        String baseUrl = "https://missav.ai/";
        if (!languageCode.isEmpty()) {
            baseUrl += languageCode + "/";
        }
        String url = baseUrl + dvdCode;
        // Pass null for movieId since we don't know it at this point
        return extractMovieInfoFromUrl(url, null);
    }
    
    /**
     * Extracts movie information for the default language (English).
     * 
     * @param dvdCode The DVD code of the movie
     * @return A map containing the extracted movie information
     * @throws IOException If an error occurs during extraction
     */
    public static Map<String, Object> extractMovieInfo(String dvdCode) throws IOException {
        return extractMovieInfoForLanguage(dvdCode, "en");
    }
    
    /**
     * Internal method to extract movie information from a specified URL.
     *
     * @param url The full URL to extract information from
     * @param movieId The ID of the movie in the database, can be null if not known
     * @return A map containing the extracted movie information
     * @throws IOException If an error occurs during extraction
     */
    public static Map<String, Object> extractMovieInfoFromUrl(String url, Integer movieId) throws IOException {

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
            .add("cookie", "locale=en")
            .build();

        try {
            // Build the request with OkHttpClient
            Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .build();

            // Execute the request
            try (Response response = client.newCall(request).execute()) {
                // Check if we got a successful response
                if (!response.isSuccessful()) {
                    logger.error("请求失败: " + response.code() + " " + response.message());
                    logger.error("原始URL: " + url);
                    logger.error("最终URL: " + response.request().url());
                    throw new IOException("Unexpected HTTP response: " + response.code());
                }
                
                // 打印URL重定向信息
                String finalUrl = response.request().url().toString();
                if (!url.equals(finalUrl)) {
                    logger.info("URL重定向: " + url + " -> " + finalUrl);
                }
                
                // Get response body and parse with JSoup
                String responseBody = response.body().string();
                Document doc = Jsoup.parse(responseBody, url);
                
                // Add cookies from the response if needed
                // The cookies were included in the curl command but we're not explicitly using them here
                // as JSoup will handle the HTML parsing without needing the cookies

                // Extract DVD ID
                String dvdId = null;
                Element metaOgUrl = doc.selectFirst("meta[property=og:url]");
                if (metaOgUrl != null && metaOgUrl.attr("content") != null) {
                    String contentUrl = metaOgUrl.attr("content");
                    dvdId = contentUrl.substring(contentUrl.lastIndexOf('/') + 1);
                } else {
                    Element titleTag = doc.selectFirst("title");
                    if (titleTag != null) {
                        String[] titleParts = titleTag.text().split("\\s+");
                        if (titleParts.length > 0) {
                            dvdId = titleParts[0];
                        }
                    }
                }

                // Check if we already have extracted m3u8 for this movie code in the WatchUrl table
                List<String> m3u8Info = new ArrayList<>();
                
                if (dvdId != null) {
                    // Query the WatchUrl table to see if we already have m3u8 info for this movie code and source
                    WatchUrl existingWatchUrl = WatchUrl.find("code = ?1 and source = ?2", 
                            dvdId, VideoSource.MISSAV.getValue()).firstResult();
                    
                    if (existingWatchUrl != null && existingWatchUrl.url != null) {
                        try {
                            // Parse the stored m3u8 info from JSON
                            List<String> storedM3u8Info = JacksonUtils.parseList(existingWatchUrl.url, String.class);
                            if (storedM3u8Info != null && !storedM3u8Info.isEmpty()) {
                                m3u8Info = storedM3u8Info;
                                logger.info("Found existing m3u8 info for code: " + dvdId);
                            }
                        } catch (Exception e) {
                            logger.error("Error parsing stored m3u8 info for code: " + dvdId);
                        }
                    } else {
                        Map<String, Object> m3u8Result = extractM3u8Info(doc);
                        m3u8Info = deobfuscateM3u8(
                                (String) m3u8Result.get("encryptedCode"),
                                (List<String>) m3u8Result.get("dictionary")
                        );
                        
                        // Save m3u8 info to WatchUrl table if we have movie ID and valid m3u8 info
                        if (!m3u8Info.isEmpty()) {
                                // 只有当movieId不为null时才保存到数据库
                                WatchUrl watchUrl = new WatchUrl();
                                watchUrl.movieId = movieId;
                                watchUrl.code = dvdId;
                                watchUrl.url = JacksonUtils.toJsonString(m3u8Info);
                                watchUrl.originalUrl = url;
                                watchUrl.index = 0;
                                watchUrl.source = VideoSource.MISSAV.getValue();
                                watchUrl.persist();
                        }
                    }
                }

                String coverUrl = null;
                Element metaOgImage = doc.selectFirst("meta[property=og:image]");
                if (metaOgImage != null) {
                    coverUrl = metaOgImage.attr("content");
                }

                String title = null;
                Element metaTitle = doc.selectFirst("meta[property=og:title]");
                if (metaTitle != null) {
                    title = metaTitle.attr("content");
                }

                String description = null;
                Element metaDesc = doc.selectFirst("meta[property=og:description]");
                if (metaDesc != null) {
                    description = metaDesc.attr("content");
                }

                String releaseDate = null;
                Element metaDate = doc.selectFirst("meta[property=og:video:release_date]");
                if (metaDate != null) {
                    releaseDate = metaDate.attr("content");
                }

                String duration = null;
                Element metaDuration = doc.selectFirst("meta[property=og:video:duration]");
                if (metaDuration != null) {
                    duration = metaDuration.attr("content");
                }

                String actor = null;
                Element metaActor = doc.selectFirst("meta[property=og:video:actor]");
                if (metaActor != null) {
                    actor = metaActor.attr("content");
                }

                String series = null;
                String maker = null;
                String label = null;
                String director = null;
                String extractedActress = null;
                String htmlReleaseDate = null;
                List<String> genres = new ArrayList<>();
                
                // Language-specific field names (case-insensitive)
                Map<String, String[]> fieldPatterns = initializeFieldPatterns();
                
                // Create optimized lookup maps for faster pattern matching
                Map<String, Map<String, String>> fieldPatternMaps = new HashMap<>();
                for (String field : fieldPatterns.keySet()) {
                    Map<String, String> patternMap = new HashMap<>();
                    for (String pattern : fieldPatterns.get(field)) {
                        patternMap.put(pattern.toLowerCase(), field);
                    }
                    fieldPatternMaps.put(field, patternMap);
                }
                
                Element infoDiv = doc.selectFirst("div.space-y-2");
                if (infoDiv != null) {
                    Elements infoItems = infoDiv.select("div.text-secondary");
                    for (Element item : infoItems) {
                        String text = item.text().trim();
                        int colonIndex = text.indexOf(':');
                        if (colonIndex <= 0) continue;
                        
                        String key = text.substring(0, colonIndex).trim();
                        String value = text.substring(colonIndex + 1).trim();
                        
                        // Use case-insensitive matching
                        // Check for series
                        boolean matchFound = false;
                        for (String pattern : fieldPatterns.get("series")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                series = value;
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If series was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for maker
                        for (String pattern : fieldPatterns.get("maker")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                maker = value;
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If maker was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for label
                        for (String pattern : fieldPatterns.get("label")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                label = value;
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If label was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for genre
                        for (String pattern : fieldPatterns.get("genre")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                String[] genreArray = value.split(",");
                                for (String genre : genreArray) {
                                    genres.add(genre.trim());
                                }
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If genre was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for actress - using optimized lookup
                        Map<String, String> actressPatternMap = fieldPatternMaps.get("actress");
                        String keyLower = key.toLowerCase();
                        for (Map.Entry<String, String> entry : actressPatternMap.entrySet()) {
                            if (keyLower.contains(entry.getKey())) {
                                extractedActress = value;
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If actress was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for director
                        for (String pattern : fieldPatterns.get("director")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                director = value;
                                matchFound = true;
                                break;
                            }
                        }
                        
                        // If director was matched, continue to next item
                        if (matchFound) continue;
                        
                        // Check for release date
                        for (String pattern : fieldPatterns.get("releaseDate")) {
                            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                                htmlReleaseDate = value;
                                // If the date is in a time tag, try to extract the datetime attribute value
                                Element timeElement = item.selectFirst("time");
                                if (timeElement != null && timeElement.hasAttr("datetime")) {
                                    htmlReleaseDate = timeElement.attr("datetime");
                                    // If datetime has time component, strip it for consistency
                                    if (htmlReleaseDate.contains("T")) {
                                        htmlReleaseDate = htmlReleaseDate.split("T")[0];
                                    }
                                }
                                break;
                            }
                        }
                    }
                }

                Map<String, Object> movieInfo = new HashMap<>();
                // Use actress field extraction if available
                
                movieInfo.put("dvd_id", dvdId);
                movieInfo.put("m3u8_info", m3u8Info);
                movieInfo.put("cover_url", coverUrl);
                movieInfo.put("title", title);
                movieInfo.put("description", description);
                // Store both release dates: meta tag date (website publish) and HTML release date (actual movie release)
                movieInfo.put("website_date", releaseDate); // From meta tags (website publication date)
                movieInfo.put("release_date", htmlReleaseDate != null ? htmlReleaseDate : releaseDate); // Actual movie release date from HTML content
                movieInfo.put("duration", duration);
                // Use extracted actress if available, otherwise use the actor from meta tags
                movieInfo.put("actor", extractedActress != null ? extractedActress : actor);
                movieInfo.put("series", series);
                movieInfo.put("maker", maker);
                movieInfo.put("label", label);
                movieInfo.put("director", director);
                movieInfo.put("genres", genres);

                return movieInfo;
            } // Close the Response try-with-resources block
        } catch (IOException e) {
            System.err.println("Error fetching URL: " + url);
            throw e;
        }
    }

    private static Map<String, Object> extractM3u8Info(Document doc) {
        Map<String, Object> result = new HashMap<>();
        Elements scripts = doc.select("script");
        // Fixed pattern with properly balanced parentheses
    Pattern pattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{(.+?)\\}\\('(.+?)',([0-9]+),([0-9]+),'(.+?)'\\.((?:split\\('\\|'\\))|(?:split\\('\\|'\\),0,\\{\\}))\\)");

        for (Element script : scripts) {
            String scriptContent = script.html();
            if (scriptContent.contains("eval(function(p,a,c,k,e,d)")) {
                Matcher matcher = pattern.matcher(scriptContent);
                if (matcher.find()) {
                    String dictionaryStr = matcher.group(5);
                    List<String> dictionary = List.of(dictionaryStr.split("\\|"));
                    String encryptedCode = matcher.group(2);
                    result.put("encryptedCode", encryptedCode);
                    result.put("dictionary", dictionary);
                    return result;
                }
            }
        }
        result.put("encryptedCode", null);
        result.put("dictionary", null);
        return result;
    }

    private static List<String> deobfuscateM3u8(String encryptedCode, List<String> dictionary) {
        if (encryptedCode == null || dictionary == null) {
            return new ArrayList<>();
        }

        String[] parts = encryptedCode.split(";");
        List<String> results = new ArrayList<>();

        for (String part : parts) {
            if (!part.contains("=")) continue;
            String value = part.split("=")[1].replaceAll("[\"'\\\\\\s]", "");

            StringBuilder decoded = new StringBuilder();
            for (char c : value.toCharArray()) {
                if (c == '.' || c == '-' || c == '/' || c == ':') {
                    decoded.append(c);
                } else {
                    int number = Integer.parseInt(String.valueOf(c), 16);
                    decoded.append(dictionary.get(number));
                }
            }
            results.add(decoded.toString());
        }
        return results;
    }

    /**
     * Initialize and return field patterns for all supported languages and fields
     * @return A map containing all field patterns
     */
    private static Map<String, String[]> initializeFieldPatterns() {
        Map<String, String[]> fieldPatterns = new HashMap<>();
        // For series field patterns - All 13 languages
        fieldPatterns.put("series", new String[]{
            "系列", // Traditional Chinese
            "系列", // Simplified Chinese
            "Series", // English
            "シリーズ", // Japanese
            "시리즈", // Korean
            "Siri", "Series", // Malay
            "ชุด", // Thai
            "Serie", // German
            "Série", // French
            "Loạt", // Vietnamese
            "Seri", // Indonesian
            "Serye", // Filipino
            "Série" // Portuguese
        });
        
        // For maker field patterns - All 13 languages
        fieldPatterns.put("maker", new String[]{
            "製作商", // Traditional Chinese
            "发行商", // Simplified Chinese
            "Maker", // English
            "メーカー", // Japanese
            "메이커", // Korean
            "Pembuat", // Malay
            "ผู้ผลิต", // Thai
            "Hersteller", // German
            "Fabricant", // French
            "nhà sản xuất", // Vietnamese
            "Pembuat", // Indonesian
            "Gumawa", // Filipino
            "Fabricante" // Portuguese
        });
        
        // For label field patterns - All 13 languages
        fieldPatterns.put("label", new String[]{
            "標籤", // Traditional Chinese
            "标籤", // Simplified Chinese
            "Label", // English
            "レーベル", // Japanese
            "상표", // Korean
            "Label", // Malay
            "ฉลาก", // Thai
            "Etikett", // German
            "Étiqueter", // French
            "Nhãn", // Vietnamese
            "Label", // Indonesian
            "Label", // Filipino
            "Rótulo" // Portuguese
        });
        
        // For genre field patterns - All 13 languages
        fieldPatterns.put("genre", new String[]{
            "類型", // Traditional Chinese
            "类型", // Simplified Chinese
            "Genre", // English
            "ジャンル", // Japanese
            "장르", // Korean
            "Genre", // Malay
            "ประเภท", // Thai
            "Genre", // German
            "Le genre", // French
            "thể loại", // Vietnamese
            "Genre", // Indonesian
            "Genre", // Filipino
            "Gênero" // Portuguese
        });
        
        // For actress field patterns - All 13 languages
        fieldPatterns.put("actress", new String[]{
            "女優", // Traditional Chinese
            "女优", // Simplified Chinese
            "Actress", // English
            "女優", // Japanese
            "여배우", // Korean
            "Pelakon wanita", // Malay
            "นักแสดงหญิง", // Thai
            "Schauspielerin", // German
            "Actrice", // French
            "Diễn viên", // Vietnamese
            "Aktris", // Indonesian
            "Artista", // Filipino
            "Actriz" // Portuguese
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
        
        // For male actor field patterns - All 13 languages
        fieldPatterns.put("maleActor", new String[]{
            "男優", // Traditional Chinese
            "男优", // Simplified Chinese
            "Actor", "Male actor", // English
            "男優", // Japanese
            "남자 배우", "남배우", // Korean
            "Pelakon lelaki", // Malay
            "นักแสดงชาย", // Thai
            "Schauspieler", "Darsteller", // German
            "Acteur", // French
            "Diễn viên nam", // Vietnamese
            "Aktor", // Indonesian
            "Aktor", // Filipino
            "Ator" // Portuguese
        });
        
        // For director field patterns - All 13 languages
        fieldPatterns.put("director", new String[]{
            "導演", "监督", // Traditional Chinese
            "导演", "监督", // Simplified Chinese
            "Director", // English
            "監督", "ディレクター", // Japanese
            "감독", // Korean
            "Pengarah", // Malay
            "ผู้กำกับ", // Thai
            "Direktor", "Regisseur", // German
            "Directeur", // French
            "Đạo diễn", // Vietnamese
            "Sutradara", // Indonesian
            "Direktor", // Filipino
            "Diretor" // Portuguese
        });
        
        return fieldPatterns;
    }
    
    /**
     * Downloads HTML content for all language variants and saves them to files
     * @param videoId The video ID to fetch
     * @throws IOException If an I/O error occurs
     */
    public static void downloadAllLanguages(String videoId) throws IOException {
        // Create data directory if it doesn't exist
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
        
        // Download HTML for each language
        for (Map.Entry<String, String> language : LANGUAGES.entrySet()) {
            String langCode = language.getKey();
            String langName = language.getValue();
            
            String url;
            String fileName;
            
            if (langCode.isEmpty()) {
                // Base URL for default language
                url = BASE_URL + "/" + videoId;
                fileName = "missav_default.html";
            } else {
                // Localized URL
                url = BASE_URL + "/" + langCode + "/" + videoId;
                fileName = "missav_" + langCode + ".html";
            }
            
            System.out.println("Downloading " + langName + " (" + langCode + ") from: " + url);
            
            try {
                // Download the HTML
                String html = downloadHtml(url);
                
                // Save to file
                saveToFile(dataDir.resolve(fileName).toString(), html);
                
                System.out.println("Saved to: " + fileName);
                
                // Delay to avoid hitting rate limits
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("Error downloading " + langName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Downloads HTML content from the given URL
     */
    private static String downloadHtml(String url) throws IOException {
        // Build headers similar to a browser request
        Headers headers = new Headers.Builder()
            .add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,zh-TW;q=0.7")
            .add("cache-control", "max-age=0")
            .add("dnt", "1")
            .add("sec-fetch-dest", "document")
            .add("sec-fetch-mode", "navigate")
            .add("sec-fetch-site", "none")
            .add("sec-fetch-user", "?1")
            .add("sec-gpc", "1")
            .add("upgrade-insecure-requests", "1")
            .add("user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
            .build();

        // Build the request
        Request request = new Request.Builder()
            .url(url)
            .headers(headers)
            .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            // Check if we got a successful response
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP response: " + response.code());
            }
            
            // Return response body
            return response.body().string();
        }
    }
    
    /**
     * Saves content to a file
     */
    private static void saveToFile(String filePath, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
        }
    }
    
    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--download-languages")) {
                // Download all language variants
                String videoId = args.length > 1 ? args[1] : "dvaj-114-uncensored-leak";
                System.out.println("Starting language scraper for: " + videoId);
                downloadAllLanguages(videoId);
                System.out.println("Done!");
            } else {
                // Extract movie info (original functionality)
                long startTime = System.currentTimeMillis();
                Map<String, Object> info = extractMovieInfo("sdde-667");

                System.out.println("影片信息:");
                System.out.println("DVD ID: " + info.get("dvd_id"));
                System.out.println("M3U8 链接: " + info.get("m3u8_info"));
                System.out.println("封面 URL: " + info.get("cover_url"));
                System.out.println("标题: " + info.get("title"));
                System.out.println("介绍: " + info.get("description"));
                System.out.println("网站发布日期: " + info.get("website_date")); // Website publication date
                System.out.println("实际发行日期: " + info.get("release_date")); // Actual movie release date
                System.out.println("时长: " + info.get("duration") + " 秒");
                System.out.println("演员: " + info.get("actor"));
                System.out.println("系列: " + info.get("series"));
                System.out.println("制作商: " + info.get("maker"));
                System.out.println("导演: " + info.get("director"));
                System.out.println("标签: " + info.get("label"));
                System.out.println("分类: " + String.join(", ", (List<String>) info.get("genres")));

                long endTime = System.currentTimeMillis();
                System.out.printf("用时: %.3f 秒%n", (endTime - startTime) / 1000.0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
