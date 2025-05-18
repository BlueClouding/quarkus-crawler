package org.acme.util;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工具类，用于记录爬取失败的URL到文本文件中
 */
public class FailedUrlLogger {
    private static final Logger logger = Logger.getLogger(FailedUrlLogger.class);
    private static final String DEFAULT_FAILED_URLS_DIR = "failed_urls";
    private static final String DEFAULT_FAILED_URLS_FILE = "failed_urls.txt";
    
    /**
     * 记录失败的URL，包含时间戳、URL和错误信息
     * 
     * @param url 失败的URL
     * @param errorMessage 错误消息
     */
    public static void logFailedUrl(String url, String errorMessage) {
        try {
            // 确保目录存在
            Path dirPath = Paths.get(DEFAULT_FAILED_URLS_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            
            // 构建完整的文件路径
            Path filePath = dirPath.resolve(DEFAULT_FAILED_URLS_FILE);
            
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = now.format(formatter);
            
            // 构建日志条目
            String logEntry = String.format("[%s] URL: %s | Error: %s%n", 
                    timestamp, url, errorMessage);
            
            // 追加到文件
            Files.write(
                filePath, 
                logEntry.getBytes(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND
            );
            
            logger.info("Failed URL logged to: " + filePath.toAbsolutePath());
        } catch (IOException e) {
            // 如果写入文件失败，记录到应用日志
            logger.error("Could not write to failed URLs log: " + e.getMessage(), e);
        }
    }
    
    /**
     * 记录失败的URL，只包含URL
     * 
     * @param url 失败的URL
     */
    public static void logFailedUrl(String url) {
        logFailedUrl(url, "No specific error message");
    }
}
