package org.acme.service.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.acme.service.translation.SubtitleExtractor.SubtitleEntry;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 优化的字幕翻译服务 - 只翻译文本部分，保留时间码和编号以节省API Token
 */
@ApplicationScoped
public class OptimizedSubtitleTranslationService {
    
    private static final Logger logger = Logger.getLogger(OptimizedSubtitleTranslationService.class);
    private static final int MAX_CHUNK_SIZE = 2000; // 由于添加了ID标识符，将块大小减小，确保不超出API限制
    private static final int MAX_PARALLEL_TASKS = 3; // 并行翻译任务数量
    
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_TASKS);
    
    @Inject
    AzureOpenAITranslationClient translationClient;
    
    /**
     * 优化的字幕翻译 - 仅翻译文本部分
     * @param subtitleContent 原始字幕内容
     * @param sourceLanguage 源语言
     * @param targetLanguage 目标语言
     * @return 翻译后的字幕内容
     */
    public String translateSubtitle(String subtitleContent, String sourceLanguage, String targetLanguage) {
        try {
            logger.infof("开始优化的字幕翻译，源语言: %s, 目标语言: %s", sourceLanguage, targetLanguage);
            
            // 1. 提取字幕条目
            List<SubtitleEntry> entries = SubtitleExtractor.extractSubtitles(subtitleContent);
            logger.infof("提取了 %d 个字幕条目", entries.size());
            
            // 2. 仅提取文本部分
            List<String> textOnly = SubtitleExtractor.extractTextOnly(entries);
            
            // 3. 将文本分块，确保每块不超过token限制
            List<List<String>> textChunks = SubtitleExtractor.chunkSubtitleTexts(textOnly, MAX_CHUNK_SIZE);
            logger.infof("文本被分成 %d 个块进行翻译", textChunks.size());
            
            // 4. 翻译每个文本块
            List<String> allTranslatedTexts = new ArrayList<>();
            
            if (textChunks.size() <= MAX_PARALLEL_TASKS) {
                // 并行翻译所有块
                List<CompletableFuture<List<String>>> futures = new ArrayList<>();
                
                for (List<String> chunk : textChunks) {
                    futures.add(CompletableFuture.supplyAsync(() -> 
                        translateTextChunk(chunk, sourceLanguage, targetLanguage), executor));
                }
                
                // 等待所有翻译完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // 收集所有翻译结果
                for (CompletableFuture<List<String>> future : futures) {
                    allTranslatedTexts.addAll(future.join());
                }
            } else {
                // 顺序翻译每个块
                for (int i = 0; i < textChunks.size(); i++) {
                    List<String> chunk = textChunks.get(i);
                    logger.infof("翻译文本块 %d/%d (包含 %d 个条目)", i + 1, textChunks.size(), chunk.size());
                    
                    List<String> translatedChunk = translateTextChunk(chunk, sourceLanguage, targetLanguage);
                    allTranslatedTexts.addAll(translatedChunk);
                }
            }
            
            // 5. 将翻译后的文本重新合并到字幕条目中
            if (allTranslatedTexts.size() != textOnly.size()) {
                logger.errorf("翻译后文本数量不匹配: 原始 %d, 翻译后 %d", textOnly.size(), allTranslatedTexts.size());
                return "翻译错误: 翻译后文本数量与原始不匹配";
            }
            
            List<SubtitleEntry> translatedEntries = SubtitleExtractor.mergeTranslatedText(entries, allTranslatedTexts);
            
            // 6. 重建字幕内容
            String translatedContent = SubtitleExtractor.rebuildSubtitleContent(translatedEntries);
            
            logger.info("字幕翻译完成");
            return translatedContent;
            
        } catch (Exception e) {
            logger.error("字幕翻译过程中出错", e);
            return "翻译失败: " + e.getMessage();
        }
    }
    
    /**
     * 翻译一个文本块，将多个条目组合在一起发送以减少API调用
     * 添加ID标识符确保能匹配原始条目
     */
    private List<String> translateTextChunk(List<String> textChunk, String sourceLanguage, String targetLanguage) {
        try {
            // 使用一个映射来跟踪ID和文本的关系
            Map<Integer, String> idToText = new HashMap<>();
            List<String> textWithIds = new ArrayList<>();
            
            // 为每个条目添加唯一ID
            for (int i = 0; i < textChunk.size(); i++) {
                String text = textChunk.get(i);
                String textWithId = String.format("[ID:%d] %s", i, text);
                textWithIds.add(textWithId);
                idToText.put(i, text);
            }
            
            // 使用分隔符连接所有带ID的文本条目
            String delimiter = "\n---\n";
            String combinedText = String.join(delimiter, textWithIds);
            
            logger.infof("组合后文本长度: %d 字符", combinedText.length());
            
            // 翻译组合后的文本
            String translatedCombined = translationClient.translateText(combinedText, sourceLanguage, targetLanguage);
            
            // 检查是否成功翻译
            if (translatedCombined.startsWith("Translation failed")) {
                logger.error("文本块翻译失败: " + translatedCombined);
                
                // 如果整个块翻译失败，尝试逐条翻译
                logger.info("尝试逐条翻译，每次请求间隔3秒...");
                List<String> results = new ArrayList<>();
                for (String text : textChunk) {
                    results.add(translationClient.translateText(text, sourceLanguage, targetLanguage));
                    try {
                        // 添加3秒延迟，避免API限制
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        logger.warn("Sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
                return results;
            }
            
            // 将翻译后的文本分割回单独的条目
            String[] translatedArray = translatedCombined.split(delimiter);
            
            // 使用ID来正确匹配翻译结果
            List<String> translatedTexts = new ArrayList<>(Collections.nCopies(textChunk.size(), null));
            List<Integer> missingIds = new ArrayList<>();
            
            // 从翻译结果中提取ID和对应的翻译文本
            for (String translatedItem : translatedArray) {
                String trimmed = translatedItem.trim();
                // 使用正则表达式匹配ID
                Pattern pattern = Pattern.compile("\\[ID:(\\d+)\\]\\s(.+)", Pattern.DOTALL);
                Matcher matcher = pattern.matcher(trimmed);
                
                if (matcher.find()) {
                    int id = Integer.parseInt(matcher.group(1));
                    // 提取ID后的翻译文本部分
                    String translatedText = matcher.group(2).trim();
                    
                    if (id >= 0 && id < textChunk.size()) {
                        translatedTexts.set(id, translatedText);
                    }
                }
            }
            
            // 检查是否有缺失的翻译
            for (int i = 0; i < translatedTexts.size(); i++) {
                if (translatedTexts.get(i) == null) {
                    missingIds.add(i);
                }
            }
            
            // 如果有缺失的翻译，只对缺失的条目进行单独翻译
            if (!missingIds.isEmpty()) {
                logger.warnf("检测到 %d 个缺失的翻译条目，正在单独翻译这些条目...", missingIds.size());
                
                for (int id : missingIds) {
                    String text = idToText.get(id);
                    String translated = translationClient.translateText(text, sourceLanguage, targetLanguage);
                    translatedTexts.set(id, translated);
                    
                    try {
                        // 添加3秒延迟，避免API限制
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        logger.warn("Sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            // 确保所有条目都已翻译
            boolean allTranslated = true;
            for (String text : translatedTexts) {
                if (text == null) {
                    allTranslated = false;
                    break;
                }
            }
            
            if (!allTranslated) {
                logger.error("即使在尝试修复后，仍有条目未能翻译");
                // 最后的应急方案：逐条翻译所有条目
                List<String> fallbackResults = new ArrayList<>();
                for (String text : textChunk) {
                    fallbackResults.add(translationClient.translateText(text, sourceLanguage, targetLanguage));
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        logger.warn("Sleep interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
                return fallbackResults;
            }
            
            return translatedTexts;
            
        } catch (Exception e) {
            logger.error("翻译文本块时出错", e);
            // 返回错误消息作为翻译结果
            List<String> errorResults = new ArrayList<>();
            for (int i = 0; i < textChunk.size(); i++) {
                errorResults.add("Translation error: " + e.getMessage());
            }
            return errorResults;
        }
    }
}
