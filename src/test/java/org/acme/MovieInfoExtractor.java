package org.acme;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
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

public class MovieInfoExtractor {
    // Create an instance of OkHttpClient
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

    public static Map<String, Object> extractMovieInfo(String dvdCode) throws IOException {
        String baseUrl = "https://missav.ai/dm5/ja/";
        String url = baseUrl + dvdCode;

        // Build headers from the curl example
        Headers headers = new Headers.Builder()
            .add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,zh-TW;q=0.7")
            .add("cache-control", "max-age=0")
            .add("dnt", "1")
            .add("priority", "u=0, i")
            .add("sec-fetch-dest", "document")
            .add("sec-fetch-mode", "navigate")
            .add("sec-fetch-site", "none")
            .add("sec-fetch-user", "?1")
            .add("sec-gpc", "1")
            .add("upgrade-insecure-requests", "1")
            .add("user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
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
                    throw new IOException("Unexpected HTTP response: " + response.code());
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

                // Rest of the extraction logic remains the same
                Map<String, Object> m3u8Result = extractM3u8Info(doc);
                List<String> m3u8Info = deobfuscateM3u8(
                        (String) m3u8Result.get("encryptedCode"),
                        (List<String>) m3u8Result.get("dictionary")
                );

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
                List<String> genres = new ArrayList<>();
                Element infoDiv = doc.selectFirst("div.space-y-2");
                if (infoDiv != null) {
                    Elements infoItems = infoDiv.select("div.text-secondary");
                    for (Element item : infoItems) {
                        String text = item.text().trim();
                        if (text.contains("シリーズ:")) {
                            series = text.split(":")[1].trim();
                        } else if (text.contains("メーカー:")) {
                            maker = text.split(":")[1].trim();
                        } else if (text.contains("レーベル:")) {
                            label = text.split(":")[1].trim();
                        } else if (text.contains("ジャンル:")) {
                            String[] genreArray = text.split(":")[1].split(",");
                            for (String genre : genreArray) {
                                genres.add(genre.trim());
                            }
                        }
                    }
                }

                Map<String, Object> movieInfo = new HashMap<>();
                movieInfo.put("dvd_id", dvdId);
                movieInfo.put("m3u8_info", m3u8Info);
                movieInfo.put("cover_url", coverUrl);
                movieInfo.put("title", title);
                movieInfo.put("description", description);
                movieInfo.put("release_date", releaseDate);
                movieInfo.put("duration", duration);
                movieInfo.put("actor", actor);
                movieInfo.put("series", series);
                movieInfo.put("maker", maker);
                movieInfo.put("label", label);
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
                String videoId = args.length > 1 ? args[1] : "shmo-162";
                System.out.println("Starting language scraper for: " + videoId);
                downloadAllLanguages(videoId);
                System.out.println("Done!");
            } else {
                // Extract movie info (original functionality)
                long startTime = System.currentTimeMillis();
                Map<String, Object> info = extractMovieInfo("shmo-162");

                System.out.println("影片信息:");
                System.out.println("DVD ID: " + info.get("dvd_id"));
                System.out.println("M3U8 链接: " + info.get("m3u8_info"));
                System.out.println("封面 URL: " + info.get("cover_url"));
                System.out.println("标题: " + info.get("title"));
                System.out.println("介绍: " + info.get("description"));
                System.out.println("发布日期: " + info.get("release_date"));
                System.out.println("时长: " + info.get("duration") + " 秒");
                System.out.println("演员: " + info.get("actor"));
                System.out.println("系列: " + info.get("series"));
                System.out.println("メーカー: " + info.get("maker"));
                System.out.println("レーベル: " + info.get("label"));
                System.out.println("ジャンル: " + String.join(", ", (List<String>) info.get("genres")));

                long endTime = System.currentTimeMillis();
                System.out.printf("用时: %.3f 秒%n", (endTime - startTime) / 1000.0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
