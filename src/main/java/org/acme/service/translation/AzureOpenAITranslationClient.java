package org.acme.service.translation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.enterprise.context.ApplicationScoped;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Direct client for Azure OpenAI API for translation purposes.
 * This provides a reliable implementation for Azure OpenAI integration.
 */
@ApplicationScoped
public class AzureOpenAITranslationClient {
    
    private static final Logger logger = Logger.getLogger(AzureOpenAITranslationClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Longer read timeout for large responses
        .build();
    
    // Azure OpenAI configuration
    private final String endpoint = "https://yut-oai-eu.openai.azure.com/";
    private final String apiKey = "67972d39fcfa49098bf45ecd4e1daaa2";
    private final String deploymentName = "yut-gpt4o-eu";
    private final String apiVersion = "2024-02-01";
    
    /**
     * Translates text using Azure OpenAI directly
     * 
     * @param text Text to translate
     * @param sourceLanguage Source language
     * @param targetLanguage Target language
     * @param isSubtitle Whether this is a subtitle translation (requiring format preservation)
     * @param isAVSubtitle Whether this is an adult video subtitle translation (requiring specialized handling)
     * @return Translated text
     */
    public String translate(String text, String sourceLanguage, String targetLanguage, boolean isSubtitle, boolean isAVSubtitle) {
        try {
            // Build system and user messages
            List<Map<String, String>> messages = new ArrayList<>();
            
            // System message - instructions for the model
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            
            if (isSubtitle) {
                if (isAVSubtitle) {
                    // Specialized system message for adult video subtitles
                    systemMessage.put("content",
                        "You are a professional subtitle translator, specializing in colloquial " + sourceLanguage + " and adult-oriented content. " +
                        "Your primary task is to translate the " + sourceLanguage + " subtitle text from adult videos into " + targetLanguage + ". " +
                        
                        // Strict Formatting Constraints
                        "You MUST strictly maintain the original subtitle format. This includes: " +
                        "1. DO NOT alter any timestamps, subtitle sequence numbers, or other technical formatting cues. " +
                        "2. If the original subtitles contain formatting tags (e.g., <i>italics</i>, <b>bold</b>, <font color=\"#FFFF00\">color tags</font>, positioning tags), replicate these tags around the correspondingly translated text. " +
                        "3. Translate ONLY the actual textual content of the subtitles. " +
                        "4. Preserve the original line breaks and the distribution of text across lines precisely as they appear in the source. Each translated line must correspond to an original line. " +
                        
                        // Content-Specific Translation Requirements for AV
                        "For this adult video content, it is crucial that your translation accurately captures and conveys: " +
                        "A. The original nuance, subtlety, and any suggestive or double meanings in the dialogue. " +
                        "B. The emotional tone (e.g., seductive, playful, demanding, surprised, expressions of pleasure or pain, including moans or other character sounds if they are transcribed as text within the subtitle). " +
                        "C. Any specific slang, colloquialisms, or idiomatic expressions common in adult entertainment contexts, translating them into natural-sounding equivalents in " + targetLanguage + ". " +
                        
                        // Output Style and Fidelity
                        "The " + targetLanguage + " translation must sound natural, befitting the scene's context, and reflect how the dialogue or transcribed sounds would be understood by a native " + targetLanguage + " speaker familiar with such content. " +
                        "Maintain utmost fidelity to the source text's intended meaning and impact. " +
                        
                        // Final Instruction
                        "Return ONLY the translated text for each subtitle segment. Do not add any explanations, notes, headers, or any text whatsoever beyond the direct translation of the subtitle content itself, fitting within the original formatting structure."
                    );
                } else {
                    // Standard subtitle translation system message
                    systemMessage.put("content", 
                        "You are a professional subtitle translator. " +
                        "Your task is to translate subtitle text while strictly maintaining the original format. " +
                        "Do not change any timestamps, subtitle numbers, or formatting. " +
                        "Translate only the actual text content. " +
                        "Maintain the same line breaks and text distribution as in the original."
                    );
                }
            } else {
                systemMessage.put("content", 
                    "You are a professional translator with expertise in multiple languages. " +
                    "Translate the text with accuracy while preserving the original formatting. " +
                    "Do not add any explanations or notes - return only the translated text."
                );
            }
            messages.add(systemMessage);
            
            // User message - the text to translate
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            
            if (isSubtitle) {
                userMessage.put("content", 
                    "Here is a subtitle file section in " + sourceLanguage + " that I need translated to " + 
                    targetLanguage + ". Translate only the text, keeping all numbers, timestamps, and formatting intact:\n\n" +
                    text
                );
            } else {
                userMessage.put("content", 
                    "Translate the following text from " + sourceLanguage + " to " + targetLanguage + ":\n\n" + 
                    text
                );
            }
            messages.add(userMessage);
            
            // Create the request body
            ObjectNode requestBody = mapper.createObjectNode();
            ArrayNode messagesNode = requestBody.putArray("messages");
            
            for (Map<String, String> message : messages) {
                ObjectNode messageNode = messagesNode.addObject();
                message.forEach(messageNode::put);
            }
            
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.1);
            
            // Build URL for Azure OpenAI
            String url = String.format("%sopenai/deployments/%s/chat/completions?api-version=%s",
                    endpoint, deploymentName, apiVersion);
            
            logger.infof("Sending translation request to: %s", url);
            
            // Create and execute request
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("api-key", apiKey)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    logger.errorf("Translation API request failed with code %d: %s", 
                            response.code(), responseBody);
                    return "Translation failed with error code: " + response.code() + ", details: " + responseBody;
                }
                
                String responseBody = response.body().string();
                JsonNode rootNode = mapper.readTree(responseBody);
                
                // Extract the response content
                if (rootNode.has("choices") && rootNode.get("choices").isArray() && rootNode.get("choices").size() > 0) {
                    JsonNode firstChoice = rootNode.get("choices").get(0);
                    if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                        return firstChoice.get("message").get("content").asText();
                    }
                }
                
                logger.error("Unexpected response format: " + responseBody);
                return "Translation failed: Unexpected response format";
            }
            
        } catch (IOException e) {
            logger.error("Error during translation request", e);
            return "Translation failed: " + e.getMessage();
        }
    }
    
    /**
     * Translates subtitle text preserving formatting
     */
    public String translateSubtitle(String text, String sourceLanguage, String targetLanguage) {
        // Handle large subtitle files by chunking if needed
        if (text.length() > 4000) {
            return translateLargeSubtitle(text, sourceLanguage, targetLanguage);
        }
        return translate(text, sourceLanguage, targetLanguage, true, true);
    }
    
    /**
     * Translates large subtitle files by breaking them into chunks
     * based on subtitle entries, then combining the results
     */
    private String translateLargeSubtitle(String text, String sourceLanguage, String targetLanguage) {
        logger.info("Breaking large subtitle into chunks for translation - Total length: " + text.length());
        
        // Use a more reliable pattern to split subtitle entries
        // This regex looks for number patterns that typically start subtitle entries
        String[] entries = text.split("(\\n|^)(\\d+)(\\n|$)");
        
        // Filter out empty entries
        List<String> validEntries = new ArrayList<>();
        for (String entry : entries) {
            if (entry != null && !entry.trim().isEmpty()) {
                validEntries.add(entry.trim());
            }
        }
        
        logger.infof("Found %d subtitle entries to translate", validEntries.size());
        StringBuilder result = new StringBuilder();
        
        // Start with smaller chunks for more reliable translation
        int entriesPerChunk = 5; 
        int maxChunkSize = 2000; // characters
        
        for (int i = 0; i < validEntries.size();) {
            StringBuilder chunk = new StringBuilder();
            int startIndex = i;
            int entryCount = 0;
            
            // Build a chunk that doesn't exceed maxChunkSize
            while (i < validEntries.size() && 
                   chunk.length() < maxChunkSize && 
                   entryCount < entriesPerChunk) {
                
                // Add the subtitle entry number
                chunk.append(i + 1).append("\n");
                
                // Add the subtitle content
                chunk.append(validEntries.get(i)).append("\n\n");
                
                i++;
                entryCount++;
            }
            
            // If we created a valid chunk
            if (chunk.length() > 0) {
                logger.infof("Translating subtitle chunk %d to %d (of %d) - Size: %d chars", 
                         startIndex + 1, i, validEntries.size(), chunk.length());
                
                // Translate this chunk
                try {
                    String translatedChunk = translate(chunk.toString(), sourceLanguage, targetLanguage, true, true);
                    
                    // Check if translation failed
                    if (translatedChunk.startsWith("Translation failed")) {
                        logger.warn("Translation failed for chunk. Reducing chunk size and retrying.");
                        
                        // If a chunk fails, retry with a smaller size
                        if (entriesPerChunk > 1) {
                            entriesPerChunk = Math.max(1, entriesPerChunk / 2);
                            maxChunkSize = Math.max(500, maxChunkSize / 2);
                            i = startIndex; // Retry from the beginning of this chunk
                            logger.infof("Reduced chunk size to %d entries, max %d chars", entriesPerChunk, maxChunkSize);
                        } else {
                            // If we're already at minimum size and still failing
                            logger.error("Failed to translate even with minimum chunk size");
                            return translatedChunk;
                        }
                    } else {
                        // Successfully translated this chunk
                        result.append(translatedChunk);
                        logger.infof("Successfully translated chunk %d-%d", startIndex + 1, i);
                    }
                } catch (Exception e) {
                    logger.error("Exception during translation: " + e.getMessage(), e);
                    return "Translation failed: " + e.getMessage();
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Translates normal text
     */
    public String translateText(String text, String sourceLanguage, String targetLanguage) {
        return translate(text, sourceLanguage, targetLanguage, false, true);
    }
}
