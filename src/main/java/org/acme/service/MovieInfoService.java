package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.entity.Movie;
import org.acme.entity.MovieInfo;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 电影多语言信息服务
 * 处理电影多语言信息的创建、更新和检索
 */
@ApplicationScoped
public class MovieInfoService {

    private static final Logger logger = Logger.getLogger(MovieInfoService.class);
    private static final String[] SUPPORTED_LANGUAGES = {"zh", "en", "ja"}; // 支持的语言列表

    @Inject
    MovieParser movieParser;

    /**
     * 根据电影UUID和语言获取电影信息
     * 
     * @param movieUuid 电影UUID
     * @param language 语言代码
     * @return 指定语言的电影信息
     */
    public MovieInfo getMovieInfoByLanguage(UUID movieUuid, String language) {
        return MovieInfo.find("movieUuid = ?1 AND language = ?2", movieUuid, language).firstResult();
    }

    /**
     * 获取电影的所有语言版本信息
     * 
     * @param movieUuid 电影UUID
     * @return 所有语言版本的电影信息列表
     */
    public List<MovieInfo> getAllLanguageVersions(UUID movieUuid) {
        return MovieInfo.list("movieUuid", movieUuid);
    }

    /**
     * 保存电影的多语言信息
     * 
     * @param movie 电影对象
     * @param language 语言代码
     * @param info 电影多语言信息
     * @return 保存后的MovieInfo对象
     */
    @Transactional
    public MovieInfo saveMovieInfo(Movie movie, String language, MovieInfo info) {
        if (info == null) {
            info = MovieInfo.fromMovie(movie, language);
        } else {
            info.movieUuid = movie.movieUuid;
            info.language = language;
        }
        
        // 查找已存在的信息
        MovieInfo existingInfo = getMovieInfoByLanguage(movie.movieUuid, language);
        
        if (existingInfo != null) {
            // 更新已存在的记录
            existingInfo.title = info.title;
            existingInfo.description = info.description;
            existingInfo.tags = info.tags;
            existingInfo.genres = info.genres;
            existingInfo.director = info.director;
            existingInfo.maker = info.maker;
            existingInfo.actresses = info.actresses;
            existingInfo.series = info.series;
            existingInfo.persist();
            return existingInfo;
        } else {
            // 创建新记录
            info.persist();
            return info;
        }
    }

    /**
     * 从原始电影对象创建所有支持语言的电影信息
     * 现在只是复制相同信息，实际应用中应通过翻译API或其他方式获取多语言内容
     * 
     * @param movie 电影对象
     * @return 创建的电影信息列表
     */
    @Transactional
    public List<MovieInfo> createAllLanguageVersions(Movie movie) {
        List<MovieInfo> results = new ArrayList<>();
        
        for (String language : SUPPORTED_LANGUAGES) {
            // 使用不同语言抓取电影信息
            try {
                // 在实际应用中，这里可以根据language来修改请求路径
                // 现在先简单地从原Movie复制数据
                MovieInfo info = MovieInfo.fromMovie(movie, language);
                results.add(saveMovieInfo(movie, language, info));
                logger.infof("Created %s language version for movie %s", language, movie.code);
            } catch (Exception e) {
                logger.errorf("Error creating %s language version for movie %s: %s", 
                        language, movie.code, e.getMessage());
            }
        }
        
        return results;
    }

    /**
     * 更新电影在爬取过程中使用的默认语言
     * 用于修改现有爬虫接口以支持多语言
     * 
     * @param movieUrl 电影URL
     * @param language 目标语言代码
     * @return 修改后的URL
     */
    public String updateUrlWithLanguage(String movieUrl, String language) {
        if (movieUrl == null || movieUrl.isEmpty()) {
            return movieUrl;
        }
        
        // 假设URL格式为: https://example.com/zh/movie/123456
        // 我们需要替换语言部分 "/zh/" 为 "/{language}/"
        
        // 查找当前语言代码位置
        for (String lang : SUPPORTED_LANGUAGES) {
            String langPattern = "/" + lang + "/";
            if (movieUrl.contains(langPattern)) {
                // 替换为新的语言代码
                return movieUrl.replace(langPattern, "/" + language + "/");
            }
        }
        
        // 如果没有找到语言代码，尝试在域名后添加语言
        // 例如：https://example.com/movie/123456 → https://example.com/zh/movie/123456
        if (movieUrl.matches("https?://[^/]+/.*")) {
            int index = movieUrl.indexOf("/", 8); // 跳过"https://"或"http://"
            if (index > 0) {
                return movieUrl.substring(0, index) + "/" + language + movieUrl.substring(index);
            }
        }
        
        // 如果无法修改URL，返回原始URL
        return movieUrl;
    }
}
