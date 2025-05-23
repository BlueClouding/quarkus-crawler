package org.acme.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.entity.MovieInfo;
import org.acme.entity.VideoSource;
import org.acme.enums.Av123LanguageCode;
import org.acme.enums.MissAvLanguageCode;
import org.acme.service.extractor.MovieInfoExtractor;
import org.acme.util.FailedUrlLogger;
import org.acme.util.MovieInfoExtractorUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



@ApplicationScoped
public class MovieInfoExtractionService {

    @Inject
    MovieParser movieParser;

    @Inject
    MovieDetailCrawlerService movieDetailCrawlerService;

    // Using LanguageCode enum for language mapping

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
    private static String getMissavLanguageUrl(String dvdCode, String languageCode) {
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

        for (Av123LanguageCode languageCode : Av123LanguageCode.values()) {
            String languagePathCode = languageCode.getPathCode();
            String languageDbCode = languageCode.getDbCode();

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
                Movie movie = new Movie();
                movie.setLink(url);
                movie = movieDetailCrawlerService.processMovie(movie);

                if (movie == null) {
                    Log.infof("No movies found for language %s", languageDbCode);
                    continue;
                }
                // 创建MovieInfo对象
                MovieInfo movieInfo = new MovieInfo();
                movieInfo.code = movieCode.toLowerCase();
                movieInfo.movieUuid = movieUuid;
                movieInfo.language = languageDbCode;
                movieInfo.title = movie.title;
                movieInfo.description = movie.description;
                movieInfo.coverUrl = movie.coverImageUrl;

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

            } catch (Exception e) {
                Log.errorf("Error extracting movie info for language %s: %s", languageDbCode, e.getMessage());
            }

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
    // OkHttpClient as a class field for reuse
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private String fetchHtmlContent(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
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
     * Extracts movie information for all supported languages and saves it to the database.
     *
     * @return A list of the created MovieInfo entities
     */
    @Transactional
    public List<MovieInfo> extractAndSaveAllLanguages(Movie movie) {
        if (movie == null) {
            Log.error("Cannot extract movie info: Movie is null");
            return Collections.emptyList();
        }

        // Extract the DVD code from the link URL
        String movieCode = movie.getCode();
        if (movieCode == null || movieCode.isEmpty()) {
            Log.errorf("Could not extract code from movie: %s", movie.getLink());
            return Collections.emptyList();
        }

        List<MovieInfo> savedEntities = new ArrayList<>();
        UUID movieUuid = UUID.randomUUID(); // Generate a shared UUID for all language versions

        // 先查询 movie_info 对应 code 的数据，只对缺失的语言进行提取. 404的记录不进行提取
        List<MovieInfo> movieInfos = MovieInfo.list("code = ?1", movieCode);
        Set<String> existingLanguages = new HashSet<>();
        if (movieInfos != null && !movieInfos.isEmpty()) {
            existingLanguages = movieInfos.stream().map(MovieInfo::getLanguage).collect(Collectors.toSet());
        }

        // Iterate through all language codes
        for (MissAvLanguageCode languageCode : MissAvLanguageCode.values()) {
            String languagePathCode = languageCode.getPathCode();
            String languageDbCode = languageCode.getDbCode();
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
            String url = getMissavLanguageUrl(movieCode, languagePathCode);
            Map<String, Object> extractedInfo;
            try {
                extractedInfo = MovieInfoExtractor.extractMovieInfoForLanguage(url);
            } catch (Exception e) {
                // 检查是否是404错误
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    // 对于404错误，尝试从123av获取信息
                    Log.infof("MissAV language version not found for %s, trying 123av: %s", languageDbCode, url);

                    try {
                        // 尝试从123av获取相同语言的信息
                        Map<String, Object> av123Info = extract123AvInfo(movieCode, languageDbCode, movie.getLink());

                        if (av123Info != null && !av123Info.isEmpty()) {
                            Log.infof("Successfully retrieved info from 123av for %s", movieCode);

                            // 创建并保存从123av获取的信息
                            MovieInfo av123MovieInfo = createMovieInfoFromExtractedData(av123Info, movieUuid, languageDbCode);
                            av123MovieInfo.persist();
                            savedEntities.add(av123MovieInfo);
                        } else {
                            // 如果123av也没有数据，创建占位记录
                            Log.infof("No data from 123av either, creating placeholder record for %s", movieCode);
                            MovieInfo placeholderInfo = createPlaceholderMovieInfo(movieCode, movieUuid, languageDbCode);
                            placeholderInfo.persist();
                            savedEntities.add(placeholderInfo);
                        }
                    } catch (Exception av123Ex) {
                        // 如果从123av获取信息也失败，创建占位记录
                        Log.warnf("Failed to get info from 123av for %s: %s", movieCode, av123Ex.getMessage());
                        MovieInfo placeholderInfo = createPlaceholderMovieInfo(movieCode, movieUuid, languageDbCode);
                        placeholderInfo.persist();
                        savedEntities.add(placeholderInfo);
                    }
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
 * 从123av网站获取电影信息
 *
 * @param movieCode 电影代码
 * @param languageDbCode 语言代码
 * @param movieLink 电影链接
 * @return 提取的电影信息
 * @throws IOException 如果提取过程中发生错误
 */
private Map<String, Object> extract123AvInfo(String movieCode, String languageDbCode, String movieLink) throws IOException {
    // 查找对应的123av语言代码
    Av123LanguageCode languageCode = Av123LanguageCode.findByDbCode(languageDbCode);
    if (languageCode == null) {
        // 如果找不到对应的语言代码，使用英语作为默认值
        languageCode = Av123LanguageCode.ENGLISH;
        Log.warnf("No matching 123av language code for %s, using English as fallback", languageDbCode);
    }

    // 构建123av URL
    String prefix = "https://123av.com/";
    String pathCode = languageCode.getPathCode();

    if (!pathCode.isEmpty()) {
        prefix += pathCode + "/";
    }

    String url = prefix + movieLink;
    Log.infof("Trying to extract info from 123av: %s", url);

    // 使用OkHttpClient获取HTML内容
    Request request = new Request.Builder()
            .url(url)
            .addHeader("Accept-Language", languageCode.getDbCode())
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
            Log.warnf("123av request failed with code: %d for language: %s", response.code(), languageDbCode);
            return null;
        }

        // 获取响应内容
        String responseBody = response.body().string();

        // 使用MovieInfoExtractorUtil提取信息
        Map<String, Object> extractedInfo = MovieInfoExtractorUtil.extractMissavInfo(responseBody);

        // 添加来源信息
        extractedInfo.put("source", "123av");
        extractedInfo.put("language_code", languageDbCode);

        return extractedInfo;
    }
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
 * @param languageCode The language code
 * @return A minimal MovieInfo entity marked as a placeholder
 */
private MovieInfo createPlaceholderMovieInfo(String movieCode, UUID movieUuid, String languageCode) {
    MovieInfo info = new MovieInfo();
    info.movieUuid = movieUuid;
    info.code = movieCode;
    info.language = languageCode;
    info.title = movieCode + " (Placeholder)";
    info.description = "This is a placeholder record for a language version that does not exist.";
    info.source = "missav";
    info.tags = Collections.emptyList();
    info.genres = Collections.emptyList();
    info.actresses = Collections.emptyList();
    info.m3u8Info = Collections.emptyList();
    return info;
}

/**
 * 从提取的数据创建MovieInfo对象
 *
 * @param extractedData 提取的数据
 * @param movieUuid 电影UUID
 * @param languageCode 语言代码
 * @return 创建的MovieInfo对象
 */
private MovieInfo createMovieInfoFromExtractedData(Map<String, Object> extractedData, UUID movieUuid, String languageCode) {
    MovieInfo info = new MovieInfo();
    info.movieUuid = movieUuid;
    info.language = languageCode;

    // 设置电影代码
    if (extractedData.containsKey("dvd_id")) {
        info.code = (String) extractedData.get("dvd_id");
    } else if (extractedData.containsKey("code")) {
        info.code = (String) extractedData.get("code");
    }

    // 设置标题
    if (extractedData.containsKey("title")) {
        info.title = (String) extractedData.get("title");
    }

    // 设置封面URL
    if (extractedData.containsKey("cover_url")) {
        info.coverUrl = (String) extractedData.get("cover_url");
    }

    // 设置描述
    if (extractedData.containsKey("description")) {
        info.description = (String) extractedData.get("description");
    }

    // 设置发布日期
    if (extractedData.containsKey("release_date")) {
        String releaseDateStr = (String) extractedData.get("release_date");
        try {
            // 尝试解析日期字符串为LocalDate
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            info.releaseDate = LocalDate.parse(releaseDateStr, formatter);
        } catch (Exception e) {
            Log.warnf("Could not parse release date: %s", releaseDateStr);
        }
    }

    // 设置时长
    if (extractedData.containsKey("duration")) {
        String durationStr = (String) extractedData.get("duration");
        try {
            info.duration = Integer.parseInt(durationStr);
        } catch (NumberFormatException e) {
            // 如果无法解析为整数，尝试解析时间格式 (HH:MM:SS)
            if (durationStr != null && durationStr.contains(":")) {
                String[] parts = durationStr.split(":");
                if (parts.length == 3) {
                    try {
                        int hours = Integer.parseInt(parts[0]);
                        int minutes = Integer.parseInt(parts[1]);
                        int seconds = Integer.parseInt(parts[2]);
                        info.duration = hours * 3600 + minutes * 60 + seconds;
                    } catch (NumberFormatException ex) {
                        Log.warnf("Could not parse duration: %s", durationStr);
                    }
                }
            }
        }
    }

    // 设置演员
    if (extractedData.containsKey("actor")) {
        String actorStr = (String) extractedData.get("actor");
        info.actresses = Collections.singletonList(actorStr);
    } else if (extractedData.containsKey("actress") || extractedData.containsKey("actresses")) {
        String actressStr = (String) (extractedData.containsKey("actress") ?
                            extractedData.get("actress") : extractedData.get("actresses"));
        info.actresses = Collections.singletonList(actressStr);
    }

    // 设置制作商
    if (extractedData.containsKey("maker")) {
        info.maker = (String) extractedData.get("maker");
    }

    // 设置来源
    if (extractedData.containsKey("source")) {
        info.source = (String) extractedData.get("source");
    } else {
        info.source = "123av";
    }

    // 初始化集合字段以避免null异常
    if (info.tags == null) {
        info.tags = Collections.emptyList();
    }

    if (info.genres == null) {
        info.genres = Collections.emptyList();
    }

    if (info.actresses == null) {
        info.actresses = Collections.emptyList();
    }

    if (info.m3u8Info == null) {
        info.m3u8Info = Collections.emptyList();
    }

    return info;
}

/**
 * Parses a date string into a LocalDate object.
 *
 * @param dateStr The date string to parse
 * @return The parsed LocalDate, or null if parsing fails
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
            "dd/MM/yyyy",
            "MM-dd-yyyy",
            "MM/dd/yyyy"
    };

    for (String format : dateFormats) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            // Try next format
        }
    }

    Log.warn("Could not parse date: " + dateStr);
    return null;
}

}
