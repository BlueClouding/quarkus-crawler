package org.acme.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.acme.service.translation.OptimizedSubtitleTranslationService;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * 优化翻译测试控制器 - 直接使用文件路径进行测试
 */
@Path("/test-optimized")
@Produces(MediaType.APPLICATION_JSON)
public class OptimizedTranslationTestController {

    private static final Logger logger = Logger.getLogger(OptimizedTranslationTestController.class);
    private static final String OUTPUT_DIR = "data";
    
    @Inject
    OptimizedSubtitleTranslationService translationService;
    
    /**
     * 读取文件并直接翻译 - 仅提取文本部分进行翻译以节省token
     */
    @GET
    @Path("/subtitle-file")
    public Response testTranslateSubtitleFile(
            @QueryParam("filePath") String filePath,
            @QueryParam("sourceLanguage") String sourceLanguage,
            @QueryParam("targetLanguage") String targetLanguage) {
        
        try {
            // 验证参数
            if (filePath == null || sourceLanguage == null || targetLanguage == null) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required parameters: filePath, sourceLanguage, or targetLanguage"))
                        .build();
            }
            
            logger.infof("Testing optimized subtitle translation with file: %s", filePath);
            
            // 确保输出目录存在
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            
            // 读取文件内容
            String subtitleContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            logger.infof("Read subtitle file with %d characters", subtitleContent.length());
            
            // 调用优化的翻译服务
            String translated = translationService.translateSubtitle(subtitleContent, sourceLanguage, targetLanguage);
            
            // 生成输出文件名
            String fileName = Paths.get(filePath).getFileName().toString();
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
            String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : ".srt";
            String outputFileName = baseName + "_" + targetLanguage + extension;
            String outputFilePath = OUTPUT_DIR + "/" + outputFileName;
            
            // 保存翻译后的文件
            Files.write(Paths.get(outputFilePath), translated.getBytes(StandardCharsets.UTF_8));
            
            logger.infof("Translation completed, saved to: %s", outputFilePath);
            
            // 返回结果
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Translation completed successfully",
                    "outputFile", outputFilePath,
                    "characterCount", translated.length()
            )).build();
            
        } catch (Exception e) {
            logger.error("Error in test translation", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Translation failed: " + e.getMessage()))
                    .build();
        }
    }
}
