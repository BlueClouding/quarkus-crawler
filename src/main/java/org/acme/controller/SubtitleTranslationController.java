package org.acme.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.acme.service.translation.OptimizedSubtitleTranslationService;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * Controller to handle subtitle file translations using Azure OpenAI API
 */
@Path("/subtitle-translate")
@Produces(MediaType.APPLICATION_JSON)
public class SubtitleTranslationController {
    
    private static final Logger logger = Logger.getLogger(SubtitleTranslationController.class);
    private static final String UPLOAD_DIR = "subtitle-uploads";
    private static final String OUTPUT_DIR = "subtitle-translations";
    private ExecutorService executor;
    
    @Inject
    OptimizedSubtitleTranslationService translationService;
    
    @PostConstruct
    void init() {
        executor = Executors.newFixedThreadPool(5);
        
        // Create directories if they don't exist
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }
    }
    
    @PreDestroy
    void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    // Define multipart form bean
    public static class SubtitleFormData {
        @RestForm("file")
        public FileUpload file;
        
        @RestForm("sourceLanguage")
        @PartType(MediaType.TEXT_PLAIN)
        public String sourceLanguage;
        
        @RestForm("targetLanguage")
        @PartType(MediaType.TEXT_PLAIN)
        public String targetLanguage;
        
        @RestForm("fileName")
        @PartType(MediaType.TEXT_PLAIN)
        public String fileName;
    }

    @POST
    @Path("/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response translateSubtitleFile(SubtitleFormData formData) {
        try {
            // Check if required fields are present
            if (formData.file == null || formData.sourceLanguage == null || formData.targetLanguage == null) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required parameters: file, sourceLanguage, or targetLanguage"))
                        .build();
            }

            // Use the provided filename or get it from the upload
            String fileName = (formData.fileName != null && !formData.fileName.isEmpty()) 
                ? formData.fileName 
                : formData.file.fileName();
                
            if (fileName == null || fileName.isEmpty()) {
                fileName = "subtitle";
            }

            // Generate unique file names
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String originalFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            String inputFilePath = UPLOAD_DIR + "/" + uniqueId + "_" + originalFileName;
            
            // Create file extension based on target language for the output
            String fileExtension = originalFileName.contains(".") 
                    ? originalFileName.substring(originalFileName.lastIndexOf(".")) 
                    : ".srt";
            String outputFileName = originalFileName.contains(".") 
                    ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                    : originalFileName;
            String outputFilePath = OUTPUT_DIR + "/" + outputFileName + "_" + formData.targetLanguage + fileExtension;
            
            // Copy uploaded file to our storage
            java.nio.file.Path uploadedFilePath = formData.file.filePath();
            byte[] subtitleBytes = Files.readAllBytes(uploadedFilePath);
            
            // Save a copy of the uploaded subtitle file
            Files.write(Paths.get(inputFilePath), subtitleBytes, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Read the file content
            String subtitleContent = new String(subtitleBytes, StandardCharsets.UTF_8);
            
            // Submit translation task to background thread to avoid timeouts
            executor.submit(() -> {
                try {
                    // Translate with optimized service that extracts only text content
                    logger.info("Starting background translation with optimized service...");
                    
                    String translatedSubtitle;
                    
                    // 大文件的特殊处理，如果文件超过20KB，将其分块翻译
                    if (subtitleContent.length() > 20000) {
                        logger.info("Large subtitle file detected ("+subtitleContent.length()+" chars), using chunked translation");
                        
                        // 将大文件按行分割
                        String[] lines = subtitleContent.split("\n");
                        StringBuilder resultBuilder = new StringBuilder();
                        
                        // 每次翻译5000个字符
                        StringBuilder chunkBuilder = new StringBuilder();
                        for (String line : lines) {
                            chunkBuilder.append(line).append("\n");
                            
                            // 当块大小超过5000字符时，翻译并清空
                            if (chunkBuilder.length() > 5000) {
                                String chunk = chunkBuilder.toString();
                                String translatedChunk = translationService.translateSubtitle(
                                        chunk, formData.sourceLanguage, formData.targetLanguage);
                                resultBuilder.append(translatedChunk);
                                
                                // 清空块
                                chunkBuilder.setLength(0);
                                
                                // 添加3秒延迟避免API限制
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    logger.warn("Sleep interrupted", e);
                                }
                            }
                        }
                        
                        // 翻译最后一个块
                        if (chunkBuilder.length() > 0) {
                            String chunk = chunkBuilder.toString();
                            String translatedChunk = translationService.translateSubtitle(
                                    chunk, formData.sourceLanguage, formData.targetLanguage);
                            resultBuilder.append(translatedChunk);
                        }
                        
                        translatedSubtitle = resultBuilder.toString();
                    } else {
                        // 小文件使用标准翻译
                        translatedSubtitle = translationService.translateSubtitle(
                                subtitleContent, formData.sourceLanguage, formData.targetLanguage);
                    }
                        
                        // Write translated content to output file
                        Files.writeString(Paths.get(outputFilePath), translatedSubtitle, StandardCharsets.UTF_8);
                        logger.infof("Translation complete, saved to: %s", outputFilePath);
                        
                } catch (Exception e) {
                    logger.error("Error in background translation task", e);
                }
            });
            
            // Build the response
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Subtitle translated successfully");
            response.put("originalFile", inputFilePath);
            response.put("translatedFile", outputFilePath);
            response.put("downloadUrl", "/subtitle-translate/download?file=" + outputFilePath);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Error translating subtitle file", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to translate subtitle: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response translateSubtitleText(SubtitleTranslationRequest request) {
        try {
            if (request.getSubtitleContent() == null || request.getSourceLanguage() == null || 
                    request.getTargetLanguage() == null) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required parameters"))
                        .build();
            }
            
            // Use optimized translation service
            String translatedContent = translationService.translateSubtitle(
                    request.getSubtitleContent(), 
                    request.getSourceLanguage(), 
                    request.getTargetLanguage());
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("translatedContent", translatedContent);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Error translating subtitle text", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to translate subtitle: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Download a translated subtitle file
     */
    @GET
    @Path("/download")
    public Response downloadSubtitleFile(@QueryParam("file") String filePath) {
        try {
            // Validate the filePath
            if (filePath == null || filePath.isEmpty()) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing file parameter"))
                        .build();
            }
            
            // Security check: Only allow access to files in the output directory
            if (!filePath.startsWith(OUTPUT_DIR)) {
                return Response.status(Status.FORBIDDEN)
                        .entity(Map.of("error", "Access denied"))
                        .build();
            }
            
            java.nio.file.Path path = Paths.get(filePath);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                return Response.status(Status.NOT_FOUND)
                        .entity(Map.of("error", "File not found: " + filePath))
                        .build();
            }
            
            // Get the file name from the path
            String fileName = path.getFileName().toString();
            
            // Create streaming output to serve the file
            StreamingOutput stream = (output) -> {
                try (var inputStream = Files.newInputStream(path)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
            };
            
            // Send the file as response
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .build();
            
        } catch (Exception e) {
            logger.error("Error serving subtitle file", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to download subtitle file: " + e.getMessage()))
                    .build();
        }
    }

    public static class SubtitleTranslationRequest {
        private String subtitleContent;
        private String sourceLanguage;
        private String targetLanguage;
        
        public String getSubtitleContent() {
            return subtitleContent;
        }
        
        public void setSubtitleContent(String subtitleContent) {
            this.subtitleContent = subtitleContent;
        }
        
        public String getSourceLanguage() {
            return sourceLanguage;
        }
        
        public void setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
        }
        
        public String getTargetLanguage() {
            return targetLanguage;
        }
        
        public void setTargetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
        }
    }
}
