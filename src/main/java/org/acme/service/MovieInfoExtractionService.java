package org.acme.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.entity.MovieInfo;
import org.acme.entity.VideoSource;
import org.acme.extractor.MovieInfoExtractor;
import org.acme.util.FailedUrlLogger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class MovieInfoExtractionService {

    @Inject
    MovieParser movieParser;

    // Create language mapping with a Map.ofEntries to handle more than 10 entries
    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("", "zh-tw"), // Traditional Chinese (default)
            Map.entry("cn", "zh-cn"), // Simplified Chinese
            Map.entry("en", "en"), // English
            Map.entry("ja", "ja"), // Japanese
            Map.entry("ko", "ko"), // Korean
            Map.entry("ms", "ms"), // Malay
            Map.entry("th", "th"), // Thai
            Map.entry("de", "de"), // German
            Map.entry("fr", "fr"), // French
            Map.entry("vi", "vi"), // Vietnamese
            Map.entry("id", "id"), // Indonesian
            Map.entry("fil", "fil"), // Filipino
            Map.entry("pt", "pt") // Portuguese
    );

    // Base URL
    private static final String BASE_URL_MISSAV = "https://missav.ai/";
    private static final String BASE_URL_123 = "https://123av.com/";
    /**
     * Gets the URL for a specific DVD code and language
     *
     * @param dvdCode The DVD code
     * @param languageCode The language code (empty for default)
     * @return The full URL
     */
    private static String getLanguageUrl(String dvdCode, String languageCode) {
        if (languageCode.isEmpty()) {
            return BASE_URL_MISSAV + dvdCode;
        } else {
            return BASE_URL_MISSAV + languageCode + "/" + dvdCode;
        }
    }

    /**
     * 使用123av网站爬取多语言电影数据并封装成MovieInfo对象
     *
     * @param movieCode 电影代码
     * @return 多语言的MovieInfo列表
     */
    public List<MovieInfo> extract123AvMovieLinks(String movieLink, String movieCode) {
        List<MovieInfo> results = new ArrayList<>();
        UUID movieUuid = UUID.randomUUID(); // 生成一个共享的UUID用于所有语言版本

        for (Map.Entry<String, String> languageEntry : LANGUAGE_CODES.entrySet()) {
            String languagePathCode = languageEntry.getKey();
            String languageDbCode = languageEntry.getValue();

            try {
                // 构建特定语言的URL
                String prefix = BASE_URL_123;
                if (!languagePathCode.isEmpty()) {
                    prefix += languagePathCode + "/";
                }
                String url = prefix + movieLink;
                // 获取网页内容
                String htmlContent = fetchHtmlContent(url);
                if (htmlContent == null || htmlContent.isEmpty()) {
                    Log.warnf("Failed to fetch HTML content for language %s", languageDbCode);
                    continue;
                }

                // 提取电影链接
                List<Movie> movies = movieParser.extractMovieLinks(htmlContent, url);
                if (movies.isEmpty()) {
                    Log.infof("No movies found for language %s", languageDbCode);
                    continue;
                }

                // 查找匹配的电影
                Optional<Movie> matchedMovie = movies.stream()
                    .filter(m -> m.code != null && m.code.equalsIgnoreCase(movieCode))
                    .findFirst();

                if (matchedMovie.isPresent()) {
                    // 创建MovieInfo对象
                    MovieInfo movieInfo = new MovieInfo();
                    Movie movie = matchedMovie.get();

                    // 设置基本信息
                    movieInfo.code = movieCode.toLowerCase();
                    movieInfo.movieUuid = movieUuid;
                    movieInfo.language = languageDbCode;
                    movieInfo.title = movie.title;
                    movieInfo.description = movie.description;
                    movieInfo.coverUrl = movie.coverImageUrl;

                    // 设置其他可用信息
                    if (movie.actresses != null) {
                        movieInfo.actresses = movie.actresses;
                    } else {
                        movieInfo.actresses = Collections.emptyList();
                    }

                    if (movie.genres != null) {
                        movieInfo.genres = movie.genres;
                        movieInfo.tags = movie.genres; // 使用genres作为初始tags
                    } else {
                        movieInfo.genres = Collections.emptyList();
                        movieInfo.tags = Collections.emptyList();
                    }

                    movieInfo.director = movie.director;
                    movieInfo.maker = movie.maker;
                    movieInfo.series = movie.series;
                    movieInfo.source = VideoSource.AV123.getValue();

                    // 设置日期和时长
                    if (movie.releaseDate != null) {
                        movieInfo.releaseDate = parseDate(movie.releaseDate);
                    }

                    if (movie.duration != null && !movie.duration.isEmpty()) {
                        try {
                            movieInfo.duration = Integer.parseInt(movie.duration.replaceAll("\\D+", ""));
                        } catch (NumberFormatException e) {
                            Log.warnf("Could not parse duration: %s", movie.duration);
                        }
                    }

                    results.add(movieInfo);
                    Log.infof("Created MovieInfo for movie %s in language %s", movieCode, languageDbCode);
                }

            } catch (Exception e) {
                Log.errorf("Error extracting movie info for language %s: %s", languageDbCode, e.getMessage());
            }

            // 添加延迟以避免请求过于频繁
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return results;
    }

    /**
     * 使用123网站爬取多语言电影数据并保存到数据库
     *
     * @param movieCode 电影代码
     * @return 保存的MovieInfo实体列表
     */
    @Transactional
    public List<MovieInfo> extract123AndSaveAllLanguages(String movieLink, String movieCode) {
        // 获取多语言的MovieInfo列表
        List<MovieInfo> extractedInfos = extract123AvMovieLinks(movieLink, movieCode);
        List<MovieInfo> savedEntities = new ArrayList<>();

        for (MovieInfo info : extractedInfos) {
            if (info != null) {
                // 保存到数据库
                info.persist();
                savedEntities.add(info);
                Log.infof("Saved MovieInfo for movie %s in language %s", movieCode, info.language);
            } else {
                Log.infof("MovieInfo for movie %s is null", movieCode);
            }
        }

        return savedEntities;
    }

    /**
     * 获取指定URL的HTML内容
     *
     * @param url 要获取的URL
     * @return HTML内容，如果失败则返回null
     */
    private String fetchHtmlContent(String url) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.errorf("Error fetching HTML content from %s: HTTP %s", url, response.code());
                return null;
            }
            return response.body().string();
        } catch (Exception e) {
            Log.errorf("Error fetching HTML content from %s: %s", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts movie information for all 13 supported languages and saves it to the database.
     *
     * @return A list of the created MovieInfo entities
     */
    @Transactional
    public List<MovieInfo> extractAndSaveAllLanguages(Movie movie) {
        String movieCode = movie.code.toLowerCase();
        List<MovieInfo> savedEntities = new ArrayList<>();
        UUID movieUuid = UUID.randomUUID(); // Generate a common UUID for all language versions

        // 先查询 movie_info 对应 code 的数据，只对缺失的语言进行提取. 404的记录不进行提取
        List<MovieInfo> movieInfos = MovieInfo.list("code = ?1", movieCode);
        Set<String> existingLanguages = new HashSet<>();
        if (movieInfos != null && !movieInfos.isEmpty()) {
            existingLanguages = movieInfos.stream().map(MovieInfo::getLanguage).collect(Collectors.toSet());
        }

        for (Map.Entry<String, String> languageEntry : LANGUAGE_CODES.entrySet()) {
            String languagePathCode = languageEntry.getKey();
            String languageDbCode = languageEntry.getValue();
            // 添加延迟以避免请求过于频繁
            try {
                // 在请求之间添加800毫秒的延迟
                TimeUnit.MILLISECONDS.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("Sleep interrupted between language requests");
            }

            if (existingLanguages.contains(languageDbCode)) {
                Log.infof("Language %s already exists for movie %s, skipping extraction", languageDbCode, movieCode);
                continue;
            }

            // Extract movie information for this language
            String url = getLanguageUrl(movieCode, languagePathCode);
            Map<String, Object> extractedInfo;
            try {
                extractedInfo = MovieInfoExtractor.extractMovieInfoForLanguage(url);
            } catch (Exception e) {
                // 检查是否是404错误
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    // 对于404错误，记录为信息级别，因为这表示该语言版本不存在
                    Log.infof("Language version not found for %s, creating placeholder record: %s", languageDbCode, url);

                    // 创建并保存一个占位记录，标记该语言版本不存在
                    MovieInfo placeholderInfo = createPlaceholderMovieInfo(movieCode, movieUuid, languageDbCode);
                    placeholderInfo.persist();
                    savedEntities.add(placeholderInfo);

                    // 使用 123网站 的方法


                } else {
                    // 其他错误记录为错误级别
                    Log.error("Failed to extract movie info for " + url, e);
                    // 记录失败的URL到文件
                    FailedUrlLogger.logFailedUrl(url, e.getMessage());
                }
                // 继续处理其他语言版本，不中断整个流程
                continue;
            }

            // Create and save MovieInfo entity
            MovieInfo movieInfo = createMovieInfoFromExtracted(extractedInfo, movieCode, movieUuid, languageDbCode);
            movieInfo.persist();
            savedEntities.add(movieInfo);

            Log.info("Saved movie info for " + movieCode + " in language: " + languageDbCode);

        }

        return savedEntities;
    }

    /**
     * Creates a MovieInfo entity from extracted data.
     */
    private MovieInfo createMovieInfoFromExtracted(Map<String, Object> extractedInfo,
                                                  String movieCode,
                                                  UUID movieUuid,
                                                  String language) {
        MovieInfo movieInfo = new MovieInfo();

        // Set basic fields
        movieInfo.code = movieCode;
        movieInfo.movieUuid = movieUuid;
        movieInfo.language = language;

        // Set content fields
        movieInfo.title = (String) extractedInfo.get("title");
        movieInfo.description = (String) extractedInfo.get("description");

        // Handle m3u8 info
        @SuppressWarnings("unchecked")
        List<String> m3u8Info = (List<String>) extractedInfo.get("m3u8_info");
        movieInfo.m3u8Info = m3u8Info != null ? m3u8Info : Collections.emptyList();

        // Handle dates
        movieInfo.websiteDate = parseDate((String) extractedInfo.get("website_date"));
        movieInfo.releaseDate = parseDate((String) extractedInfo.get("release_date"));

        // Handle duration
        String durationStr = (String) extractedInfo.get("duration");
        if (durationStr != null && !durationStr.isEmpty()) {
            try {
                movieInfo.duration = Integer.parseInt(durationStr);
            } catch (NumberFormatException e) {
                Log.warn("Could not parse duration: " + durationStr);
            }
        }

        // Handle cover URL
        movieInfo.coverUrl = (String) extractedInfo.get("cover_url");

        // Handle metadata fields
        movieInfo.series = (String) extractedInfo.get("series");
        movieInfo.label = (String) extractedInfo.get("label");
        movieInfo.maker = (String) extractedInfo.get("maker");
        movieInfo.director = (String) extractedInfo.get("director"); // Now using the proper director field

        // Handle lists
        // Actress field
        String actressStr = (String) extractedInfo.get("actor");
        if (actressStr != null && !actressStr.isEmpty()) {
            movieInfo.actresses = Collections.singletonList(actressStr);
        } else {
            movieInfo.actresses = Collections.emptyList();
        }

        // Genres
        @SuppressWarnings("unchecked")
        List<String> genres = (List<String>) extractedInfo.get("genres");
        if (genres != null) {
            movieInfo.genres = genres;
        } else {
            movieInfo.genres = Collections.emptyList();
        }

        // Tags (can be same as genres initially)
        movieInfo.tags = movieInfo.genres;

        return movieInfo;
    }

    /**
     * Creates a placeholder MovieInfo entity for movies that return 404 errors.
     * This ensures we don't repeatedly try to crawl non-existent content.
     *
     * @param movieCode The DVD code
     * @param movieUuid The UUID shared by all language versions of this movie
     * @param language The language code
     * @return A minimal MovieInfo entity marked as a placeholder
     */
    private MovieInfo createPlaceholderMovieInfo(String movieCode, UUID movieUuid, String language) {
        MovieInfo placeholderInfo = new MovieInfo();

        // Set basic identification fields
        placeholderInfo.code = movieCode;
        placeholderInfo.movieUuid = movieUuid;
        placeholderInfo.language = language;

        // Set a title that clearly indicates this is a placeholder
        placeholderInfo.title = "[PLACEHOLDER] " + movieCode + " " + language;

        // Set empty collections to avoid null pointer exceptions
        placeholderInfo.m3u8Info = Collections.emptyList();
        placeholderInfo.actresses = Collections.emptyList();
        placeholderInfo.genres = Collections.emptyList();
        placeholderInfo.tags = Collections.emptyList();

        // Add a description that explains why this record exists
        placeholderInfo.description = "This is a placeholder record created because the movie could not be found in this language.";
        placeholderInfo.source = VideoSource.MISSAV.getValue();
        return placeholderInfo;
    }

    /**
     * Parses a date string into a LocalDate.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // Try different date formats
        String[] dateFormats = {
                "yyyy-MM-dd",
                "yyyy/MM/dd",
                "dd-MM-yyyy",
                "MM/dd/yyyy"
        };

        for (String format : dateFormats) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format));
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        Log.warn("Could not parse date: " + dateStr);
        return null;
    }
}
