package org.acme.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovieInfoExtractor {

    public static Map<String, Object> extractMovieInfo(String dvdCode) throws IOException {
        String baseUrl = "https://missav.ai/dm5/en/";
        String url = baseUrl + dvdCode;

        // Enhanced headers to mimic a real browser
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");

        try {
            // Configure connection with timeout and headers
            Connection connection = Jsoup.connect(url)
                    .headers(headers)
                    .timeout(10000) // 10 seconds timeout
                    .ignoreHttpErrors(true) // Don't throw on HTTP errors
                    .followRedirects(true);

            Document doc = connection.get();

            // Check if we got a 403 response
            if (connection.response().statusCode() == 403) {
                throw new IOException("Received 403 Forbidden response");
            }

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

        } catch (IOException e) {
            System.err.println("Error fetching URL: " + url);
            throw e;
        }
    }

    private static Map<String, Object> extractM3u8Info(Document doc) {
        Map<String, Object> result = new HashMap<>();
        Elements scripts = doc.select("script");
        Pattern pattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{(.+?)\\}\\('(.+?)',([0-9]+),([0-9]+),'(.+?)'\\.(split\\('\\|'\\)|split\\('\\|'\\),0,\\{\\}\\)\\)");

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

    public static void main(String[] args) {
        try {
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
