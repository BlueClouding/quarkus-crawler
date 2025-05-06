package org.acme.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.acme.model.VScopeParseResult;
import org.jboss.logging.Logger;
import org.acme.entity.Movie;
import org.acme.enums.MovieStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@ApplicationScoped
public class MovieParser {

    private static final Logger logger = Logger.getLogger(MovieParser.class.getName());
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MovieParser() {
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Movie> extractMovieLinks(String htmlContent, String baseUrl) {
        List<Movie> movies = new ArrayList<>();
        try {
            logger.info(String.format("Extracting movie links from HTML content (length: %d)", htmlContent.length()));
            Document soup = Jsoup.parse(htmlContent);
            List<String> selectors = List.of(
                    ".movie-item",
                    ".video-item",
                    ".item",
                    ".box-item",
                    ".movie-box",
                    ".video-box",
                    ".thumbnail",
                    ".movie",
                    ".video",
                    ".col-6 .box-item"
            );

            for (String selector : selectors) {
                Elements items = soup.select(selector);
                if (!items.isEmpty()) {
                    logger.info(String.format("Found %d items using selector: %s", items.size(), selector));
                    for (Element item : items) {
                        try {
                            Movie movie = new Movie();

                            Element favouriteDiv = item.selectFirst(".favourite");
                            if (favouriteDiv != null) {
                                String movieCode = favouriteDiv.attr("data-code");
                                if (!movieCode.isEmpty()) {
                                    movie.code = movieCode;
                                }
                                String vScope = favouriteDiv.attr("v-scope");
                                if (!vScope.isEmpty()) {
                                    Matcher idMatcher = Pattern.compile("Favourite\\('movie', (\\d+)").matcher(vScope);
                                    if (idMatcher.find()) {
                                        movie.originalId = Integer.parseInt(idMatcher.group(1));
                                    }
                                }
                            }

                            Element detailDiv = item.selectFirst(".detail a");
                            if (detailDiv != null) {
                                String title = detailDiv.text().trim();
                                if (!title.isEmpty()) {
                                    movie.title = title;
                                }
                                String url = detailDiv.attr("href");
                                movie.link = baseUrl + "/" + url;
                            }

                            Element img = item.selectFirst("img");
                            if (img != null) {
                                String thumbnail = img.attr("src");
                                if (thumbnail.isEmpty()) {
                                    thumbnail = img.attr("data-src");
                                }
                                if (!thumbnail.isEmpty()) {
                                    if (!thumbnail.startsWith("http")) {
                                        if (thumbnail.startsWith("/")) {
                                            thumbnail = baseUrl + thumbnail;
                                        } else {
                                            thumbnail = baseUrl + "/" + thumbnail;
                                        }
                                    }
                                    movie.thumbnail = thumbnail;
                                }
                            }

                            Element durationElem = item.selectFirst(".duration");
                            if (durationElem != null) {
                                movie.duration = durationElem.text().trim();
                            }

                            if (movie.link != null && !movie.link.isEmpty()) {
                                logger.info(String.format("Found movie: %s", movie));
                                movies.add(movie);
                            } else {
                                logger.warn(String.format("Skipping movie without URL: %s", movie));
                            }

                        } catch (Exception e) {
                            logger.error("Error processing movie item: " + e.getMessage(), e);
                        }
                    }
                    if (!movies.isEmpty()) {
                        logger.info(String.format("Successfully extracted %d movies using selector: %s", movies.size(), selector));
                        break;
                    }
                }
            }
            logger.info(String.format("Extracted %d movies in total", movies.size()));
            return movies;
        } catch (Exception e) {
            logger.error("Error extracting movie links: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public Optional<Movie> parseMoviePage(Movie movie, String htmlContent, String url) {
        if (htmlContent == null || htmlContent.isEmpty() || url == null || url.isEmpty()) {
            logger.error("Invalid input: htmlContent and url must be non-empty strings");
            return Optional.empty();
        }

        try {
            Document soup = Jsoup.parse(htmlContent);

            Element titleTag = soup.selectFirst("h1");
            if (titleTag != null) {
                movie.title = titleTag.text().trim();
            }

            Element codeTag = soup.selectFirst("span:containsOwn(コード:)");
            if (codeTag != null) {
                Element codeSpan = codeTag.nextElementSibling();
                if (codeSpan != null) {
                    movie.code = codeSpan.text().trim();
                }
            }

            Element releaseDateTag = soup.selectFirst("span:containsOwn(リリース日:)");
            if (releaseDateTag != null) {
                Element releaseDateSpan = releaseDateTag.nextElementSibling();
                if (releaseDateSpan != null) {
                    movie.releaseDate = releaseDateSpan.text().trim();
                }
            }

            Element durationTag = soup.selectFirst("span:containsOwn(再生時間:)");
            if (durationTag != null) {
                Element durationSpan = durationTag.nextElementSibling();
                if (durationSpan != null) {
                    movie.duration = durationSpan.text().trim();
                }
            }

            Element actressTag = soup.selectFirst("span:containsOwn(女優:)");
            if (actressTag != null) {
                Element actressSpan = actressTag.nextElementSibling();
                if (actressSpan != null) {
                    String actressName = actressSpan.text().trim();
                    if (!actressName.isEmpty()) {
                        movie.actresses = List.of(actressName);
                    }
                }
            }

            Element genresTag = soup.selectFirst("span:containsOwn(ジャンル:)");
            if (genresTag != null) {
                Element genresSpan = genresTag.nextElementSibling();
                if (genresSpan != null) {
                    Elements genreLinks = genresSpan.select("a");
                    movie.genres = genreLinks.stream().map(Element::text).map(String::trim).toList();
                }
            }

            Element makerTag = soup.selectFirst("span:containsOwn(メーカー:)");
            if (makerTag != null) {
                Element makerSpan = makerTag.nextElementSibling();
                if (makerSpan != null) {
                    String makerName = makerSpan.text().trim();
                    if (!makerName.isEmpty()) {
                        movie.maker = makerName;
                    }
                }
            }

            Element seriesTag = soup.selectFirst("span:containsOwn(ラベル:)");
            if (seriesTag != null) {
                Element seriesSpan = seriesTag.nextElementSibling();
                if (seriesSpan != null) {
                    String seriesName = seriesSpan.text().trim();
                    if (!seriesName.isEmpty()) {
                        movie.series = seriesName;
                    }
                }
            }

            Element likesButton = soup.selectFirst("button.favourite span[ref=counter]");
            if (likesButton != null) {
                String likesText = likesButton.text().trim();
                if (likesText.matches("\\d+")) {
                    movie.likes = Integer.parseInt(likesText);
                }
            }

            Element descriptionMeta = soup.selectFirst("meta[name=description]");
            if (descriptionMeta != null && descriptionMeta.hasAttr("content")) {
                movie.description = descriptionMeta.attr("content").trim();
            }

            Element tagsTag = soup.selectFirst("span:containsOwn(タグ:)");
            if (tagsTag != null) {
                Element tagsSpan = tagsTag.nextElementSibling();
                if (tagsSpan != null) {
                    Elements tagLinks = tagsSpan.select("a");
                    movie.tags = tagLinks.stream().map(Element::text).map(String::trim).toList();
                }
            }

            Element directorTag = soup.selectFirst("span:containsOwn(監督:)");
            if (directorTag != null) {
                Element directorSpan = directorTag.nextElementSibling();
                if (directorSpan != null) {
                    String directorName = directorSpan.text().trim();
                    if (!directorName.isEmpty()) {
                        movie.director = directorName;
                    }
                }
            }

            Element videoTag = soup.selectFirst("video");
            if (videoTag != null) {
                movie.coverImageUrl = videoTag.attr("poster");
                movie.previewVideoUrl = videoTag.attr("src");
            }

            if (movie.coverImageUrl != null && !movie.coverImageUrl.isEmpty()) {
                movie.thumbnail = movie.coverImageUrl;
            }

            movie.status = MovieStatus.ONLINE.getValue();
            return Optional.of(movie);

        } catch (Exception e) {
            logger.error("Error getting movie detail: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<VideoUrlsResult> getVideoUrls(long videoId) {
        try {
            String ajaxUrl = String.format("https://123av.com/ja/ajax/v/%d/videos", videoId);
            logger.info("Requesting ajax endpoint: " + ajaxUrl);

            Request request = new Request.Builder()
                    .url(ajaxUrl)
                    .get()
                    .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                    .addHeader("accept", "application/json, text/javascript, */*; q=0.01")
                    .addHeader("accept-language", "zh-CN,zh;q=0.9")
                    .addHeader("cookie", "_ga=GA1.1.1641394730.1737617680; _ga_VZGC2QQBZ8=GS1.1.1744253403.22.1.1744254946.0.0.0")
                    .addHeader("x-requested-with", "XMLHttpRequest") // 很关键！很多 AJAX 接口要求
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("Failed to fetch video URLs: " + response.code());
                    return Optional.empty();
                }

                String body = response.body().string();
                JsonNode data = objectMapper.readTree(body);

                if (data.has("status") && data.get("status").asInt() == 200 && data.has("result")) {
                    JsonNode result = data.get("result");
                    List<WatchInfo> watchUrls = new ArrayList<>();
                    JsonNode watchNode = result.get("watch");
                    if (watchNode != null && watchNode.isArray()) {
                        for (JsonNode node : watchNode) {
                            if (node.has("index") && node.has("name") && node.has("url")) {
                                watchUrls.add(new WatchInfo(node.get("index").asInt(), node.get("name").asText(), node.get("url").asText()));
                            }
                        }
                    }

                    List<DownloadInfo> downloadUrls = new ArrayList<>();
                    JsonNode downloadNode = result.get("download");
                    if (downloadNode != null && downloadNode.isArray()) {
                        for (JsonNode node : downloadNode) {
                            if (node.has("host") && node.has("index") && node.has("name") && node.has("url")) {
                                downloadUrls.add(new DownloadInfo(node.get("host").asText(), node.get("index").asInt(), node.get("name").asText(), node.get("url").asText()));
                            }
                        }
                    }

                    return Optional.of(new VideoUrlsResult(watchUrls, downloadUrls));
                } else {
                    logger.warn("Invalid AJAX response format");
                    return Optional.empty();
                }
            }

        } catch (IOException e) {
                logger.error("Error getting videoId: " + videoId);
            return Optional.empty();
        }
    }

    /**
     * Extract M3U8 URL from player page. Equivalent to Python's _extract_m3u8_from_player.
     *
     * @param playerUrl Player page URL
     * @return M3U8Result containing m3u8_url and vtt_url, or null if extraction fails.
     */
    public VScopeParseResult extractM3U8FromPlayer(String playerUrl) {
        if (playerUrl == null || playerUrl.trim().isEmpty()) {
            logger.warn("Player URL is empty or null");
            return null;
        }

        Request request = new Request.Builder()
                .url(playerUrl)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warnf("Failed to get player page '%s', status code: %d", playerUrl, response.code());
                return null;
            }

            return parsePlayerPage(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse player page HTML to extract stream data. Equivalent to Python's _parse_player_page.
     *
     * @param htmlContent HTML content of the player page
     * @return Map containing 'stream' and 'vtt' URLs, or null if parsing fails.
     */
    private VScopeParseResult parsePlayerPage(String htmlContent) {
        try {
            Document soup = Jsoup.parse(htmlContent);
            Element playerDiv = soup.getElementById("player"); // Find <div id="player">

            if (playerDiv == null) {
                logger.error("Player div with id='player' not found in HTML");
                return null;
            }

            String vScope = playerDiv.attr("v-scope"); // Get the 'v-scope' attribute value
            if (vScope.trim().isEmpty()) {
                logger.error("v-scope attribute not found or is empty in player div");
                return null;
            }

            return extractVScopeData(vScope);

        } catch (Exception e) {
            logger.error("Error parsing player page HTML: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract and parse JSON data from v-scope attribute string. Equivalent to Python's _extract_json_from_vscope.
     *
     * @param vScope v-scope attribute content
     * @return Parsed JSON data as a Map, or null if extraction/parsing fails.
     */
    private VScopeParseResult extractVScopeData(String vScope) {
        Integer videoId = null;
        String streamUrl = null;
        String vttUrl = null;
        Map<String, Object> tempJsonData; // To hold the intermediate parsed JSON

        if (vScope == null || vScope.isEmpty()) {
            logger.warn("v-scope attribute is null or empty.");
            return null;
        }

        try {
            // 1. Extract videoId (same logic as before)
            int openParenIndex = vScope.indexOf('(');
            int commaIndex = vScope.indexOf(',');

            if (openParenIndex != -1 && commaIndex != -1 && openParenIndex < commaIndex) {
                String videoIdStr = vScope.substring(openParenIndex + 1, commaIndex).trim();
                try {
                    videoId = Integer.parseInt(videoIdStr);
                    logger.debugf("Extracted videoId: {}", videoId);
                } catch (NumberFormatException nfe) {
                    logger.warnf("Failed to parse videoId '{}' as integer from v-scope: {}", videoIdStr, vScope);
                }
            } else {
                logger.warnf("Could not find '(videoId,' pattern to extract videoId in v-scope: {}", vScope);
            }

            // 2. Extract and parse JSON object (similar logic, but store in temp map)
            if (commaIndex == -1) {
                logger.errorf("Required comma delimiter missing in v-scope, cannot extract JSON: {}", vScope);
                return null; // Comma is essential for finding JSON part
            }

            int jsonStart = vScope.indexOf('{', commaIndex + 1);
            if (jsonStart == -1) {
                logger.errorf("Could not find starting '{{' for JSON object after comma in v-scope: {}", vScope);
                return null; // JSON structure is required
            }

            int braceCount = 1;
            int jsonEnd = jsonStart + 1;
            boolean inString = false;
            char prevChar = '\0';

            while (braceCount > 0 && jsonEnd < vScope.length()) {
                char currentChar = vScope.charAt(jsonEnd);
                if (currentChar == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (currentChar == '{') {
                        braceCount++;
                    } else if (currentChar == '}') {
                        braceCount--;
                    }
                }
                prevChar = currentChar;
                jsonEnd++;
            }

            if (braceCount > 0) {
                logger.errorf("Could not find matching closing '}}' for JSON object in v-scope: {}", vScope);
                return null; // Incomplete JSON
            }

            String jsonStr = vScope.substring(jsonStart, jsonEnd);

            try {
                TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
                tempJsonData = objectMapper.readValue(jsonStr, typeRef); // Parse into temp map
            } catch (Exception e) {
                logger.error("Error parsing JSON extracted from v-scope. Error: {}", e);
                return null; // JSON parsing failed
            }

            // 3. Extract specific fields (stream, vtt) from the temp map
            Object streamObj = tempJsonData.get("stream");
            Object vttObj = tempJsonData.get("vtt");

            // Validate and cast 'stream'
            if (!(streamObj instanceof String) || ((String) streamObj).trim().isEmpty()) {
                logger.errorf("Required 'stream' URL is missing, not a string, or empty in JSON data: {}", jsonStr);
                return null; // Stream URL is mandatory
            }
            streamUrl = (String) streamObj;

            // Validate and cast 'vtt'
            if (!(vttObj instanceof String) || ((String) vttObj).trim().isEmpty()) {
                logger.errorf("Required 'vtt' URL is missing, not a string, or empty in JSON data: {}", jsonStr);
                return null; // VTT URL is mandatory
            }
            vttUrl = (String) vttObj;

            // 4. If all required parts are extracted, create and return the result object
            return new VScopeParseResult(videoId, streamUrl, vttUrl);

        } catch (Exception e) { // Catch unexpected errors
            logger.error("Unexpected error extracting data from v-scope attribute,Error: {}", e);
            return null;
        }
    }

    public record VideoUrlsResult(List<WatchInfo> watch, List<DownloadInfo> download) {
    }

    public record WatchInfo(int index, String name, String url) {
    }

    public record DownloadInfo(String host, int index, String name, String url) {
    }

    public record WatchUrlData(int index, String name, String url) {
    }

    public record M3U8Result(String m3u8Url, String vttUrl, Integer videoId) {
    }
}
