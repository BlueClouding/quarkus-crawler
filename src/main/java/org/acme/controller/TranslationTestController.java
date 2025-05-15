package org.acme.controller;

import java.io.File;
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

/**
 * Simple controller for directly testing subtitle translations without using the web UI
 */
@Path("/test-translation")
@Produces(MediaType.APPLICATION_JSON)
public class TranslationTestController {
    
    private static final Logger logger = Logger.getLogger(TranslationTestController.class);
    
    @Inject
    OptimizedSubtitleTranslationService translationService;
    
    /**
     * Direct test for translating a subtitle file on the server
     * This is a simplified method that avoids file uploads and directly reads from the filesystem
     */
    @GET
    @Path("/subtitle-file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response testTranslateSubtitleFile(
            @QueryParam("filePath") String filePath,
            @QueryParam("sourceLanguage") String sourceLanguage,
            @QueryParam("targetLanguage") String targetLanguage,
            @QueryParam("outputFilePath") String outputFilePath) {
        
        try {
            logger.infof("Testing direct subtitle translation: %s (%s to %s)", 
                    filePath, sourceLanguage, targetLanguage);
            
            // Validate parameters
            if (filePath == null || sourceLanguage == null || targetLanguage == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required parameters"))
                        .build();
            }
            
            // Check if the file exists
            File subtitleFile = new File(filePath);
            if (!subtitleFile.exists() || !subtitleFile.isFile()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Subtitle file not found: " + filePath))
                        .build();
            }
            
            // Set default output path if not provided
            if (outputFilePath == null || outputFilePath.isEmpty()) {
                String basePath = filePath.substring(0, filePath.lastIndexOf('.'));
                outputFilePath = basePath + "_" + targetLanguage + ".srt";
            }
            
            // Read the subtitle file
            String subtitleContent = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            logger.infof("Read subtitle file, length: %d characters", subtitleContent.length());
            
            // Translate the content using optimized service
            logger.info("Starting translation with optimized service...");
            String translatedContent = translationService.translateSubtitle(
                    subtitleContent, sourceLanguage, targetLanguage);
            
            // Check if translation succeeded
            if (translatedContent.startsWith("Translation failed")) {
                logger.error("Translation failed: " + translatedContent);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of(
                            "error", "Translation failed",
                            "message", translatedContent
                        ))
                        .build();
            }
            
            // Save the translated content
            Files.writeString(Paths.get(outputFilePath), translatedContent, StandardCharsets.UTF_8);
            logger.infof("Translation successful, saved to: %s", outputFilePath);
            
            return Response.ok(Map.of(
                "status", "success",
                "message", "Translation completed successfully",
                "sourceFile", filePath,
                "translatedFile", outputFilePath,
                "translatedLength", translatedContent.length()
            )).build();
            
        } catch (Exception e) {
            logger.error("Error in test translation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to translate: " + e.getMessage()))
                    .build();
        }
    }
}
