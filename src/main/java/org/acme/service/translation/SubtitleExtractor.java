package org.acme.service.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕文件提取器 - 用于提取字幕文件中的文本内容，保留时间戳和编号
 */
public class SubtitleExtractor {

    /**
     * 字幕条目 - 表示单个字幕的结构
     */
    public static class SubtitleEntry {
        private String index;        // 字幕编号
        private String timeCode;     // 时间戳
        private String text;         // 文本内容
        private boolean isTextLine;  // 是否是文本行（用于区分时间戳和文本）
        
        public SubtitleEntry(String index, String timeCode, String text) {
            this.index = index;
            this.timeCode = timeCode;
            this.text = text;
            this.isTextLine = false;
        }
        
        public SubtitleEntry(String text, boolean isTextLine) {
            this.text = text;
            this.isTextLine = isTextLine;
        }
        
        public String getIndex() {
            return index;
        }
        
        public String getTimeCode() {
            return timeCode;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public boolean isTextLine() {
            return isTextLine;
        }
        
        @Override
        public String toString() {
            if (index != null && timeCode != null) {
                return index + "\n" + timeCode + "\n" + text;
            } else {
                return text;
            }
        }
    }
    
    /**
     * 从字幕文件内容中提取文本部分
     * @param content 字幕文件内容
     * @return 提取的字幕条目列表
     */
    public static List<SubtitleEntry> extractSubtitles(String content) {
        List<SubtitleEntry> entries = new ArrayList<>();
        
        // 先检查文件是否为空
        if (content == null || content.trim().isEmpty()) {
            return entries;
        }
        
        // 处理WebVTT格式文件，移除标头
        if (content.trim().toLowerCase().startsWith("webvtt")) {
            content = content.substring(content.toLowerCase().indexOf("webvtt") + "webvtt".length()).trim();
        }
        
        // 特殊处理日语字幕格式的冒号和箭头
        // 替换日语冒号为标准冒号
        content = content.replace("：", ":");
        // 标准化箭头格式
        content = content.replaceAll("(\\d+)[\\s:]*[-–—]>[\\s:]*", "$1 --> ");
        content = content.replaceAll("(\\d+)[\\s:]*->[\\s:]*", "$1 --> ");
        content = content.replaceAll("(\\d+)[\\s:]*→[\\s:]*", "$1 --> ");
        
        // 复杂时间码模式，涵盖多种不同格式
        String timeCodePattern = "(\\d+[:\\.]\\d+[:\\.]\\d+[.\\d]*\\s*-->\\s*\\d+[:\\.]\\d+[:\\.]\\d+[.\\d]*)";
        
        // 先尝试匹配常规SRT格式，上面有编号
        Pattern pattern1 = Pattern.compile("(\\d+)\\s*\\n" + timeCodePattern + "\\s*\\n(.+?)(?=\\n\\s*\\d+\\s*\\n|$)", Pattern.DOTALL);
        Matcher matcher1 = pattern1.matcher(content);
        
        boolean foundMatches = false;
        while (matcher1.find()) {
            foundMatches = true;
            String index = matcher1.group(1).trim();
            String timeCode = matcher1.group(2).trim();
            String text = matcher1.group(3).trim();
            
            entries.add(new SubtitleEntry(index, timeCode, text));
        }
        
        // 如果上面没有匹配，尝试匹配日语字幕常见格式（无编号，就是时间归 + 文本）
        if (!foundMatches) {
            // 匹配模式 2: 纯时间码格式
            Pattern pattern2 = Pattern.compile(timeCodePattern + "\\s*\\n(.*?)(?=\\n\\s*" + timeCodePattern + "|$)", Pattern.DOTALL);
            Matcher matcher2 = pattern2.matcher(content);
            
            int index = 1;
            while (matcher2.find()) {
                String timeCode = matcher2.group(1).trim();
                String text = matcher2.group(2).trim();
                
                // 如果文本不为空，添加条目
                if (!text.isEmpty()) {
                    entries.add(new SubtitleEntry(String.valueOf(index++), timeCode, text));
                }
            }
        }
        
        // 尝试匹配非标准时间格式，例如 00：01：12.820-> 00：01：13.550
        if (entries.isEmpty()) {
            Pattern pattern3 = Pattern.compile("(\\d+[:：]+\\d+[:：]+\\d+[.\\d]*\\s*[->－＞→]+\\s*\\d+[:：]+\\d+[:：]+\\d+[.\\d]*)\\s*\\n(.*?)(?=\\n\\s*\\d+|$)", Pattern.DOTALL);
            Matcher matcher3 = pattern3.matcher(content);
            
            int index = 1;
            while (matcher3.find()) {
                String timeCode = matcher3.group(1).trim();
                String text = matcher3.group(2).trim();
                
                // 对时间码进行标准化
                timeCode = timeCode.replace("：", ":").replaceAll("[->－＞→]+", " --> ");
                
                // 如果文本不为空，添加条目
                if (!text.isEmpty()) {
                    entries.add(new SubtitleEntry(String.valueOf(index++), timeCode, text));
                }
            }
        }
        
        // 特殊处理WebVTT格式，如果前面所有尝试都失败
        if (entries.isEmpty()) {
            // 可能是非标准VTT格式，特别处理
            // 使用非常宽松的模式来匹配时间码和文本
            Pattern pattern4 = Pattern.compile("(\\d+.+?\\d+)\\s*\\n(.+?)(?=\\n\\s*\\d+|$)", Pattern.DOTALL);
            Matcher matcher4 = pattern4.matcher(content);
            
            int index = 1;
            while (matcher4.find()) {
                String potentialTimeCode = matcher4.group(1).trim();
                String text = matcher4.group(2).trim();
                
                // 如果匹配出的可能是时间码且包含数字和分隔符
                if (potentialTimeCode.matches(".*\\d+.*[\\:\\-\\>：－＞→].*\\d+.*")) {
                    // 对时间码进行标准化
                    String timeCode = potentialTimeCode.replace("：", ":").replaceAll("[->－＞→]+", " --> ");
                    
                    if (!text.isEmpty()) {
                        entries.add(new SubtitleEntry(String.valueOf(index++), timeCode, text));
                    }
                }
            }
        }
        
        // 如果还是没有匹配，尝试将整个文件当作一个字幕处理
        if (entries.isEmpty() && !content.trim().isEmpty()) {
            entries.add(new SubtitleEntry("1", "00:00:00 --> 00:05:00", content.trim()));
        }
        
        return entries;
    }
    
    /**
     * 仅提取所有文本内容，用于批量翻译
     * @param entries 字幕条目列表
     * @return 提取的文本内容列表
     */
    public static List<String> extractTextOnly(List<SubtitleEntry> entries) {
        List<String> textOnly = new ArrayList<>();
        for (SubtitleEntry entry : entries) {
            textOnly.add(entry.getText());
        }
        return textOnly;
    }
    
    /**
     * 将翻译后的文本重新合并到字幕条目中
     * @param entries 原始字幕条目
     * @param translatedTexts 翻译后的文本
     * @return 更新后的字幕条目
     */
    public static List<SubtitleEntry> mergeTranslatedText(List<SubtitleEntry> entries, List<String> translatedTexts) {
        if (entries.size() != translatedTexts.size()) {
            throw new IllegalArgumentException("原始条目数量与翻译后文本数量不匹配");
        }
        
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setText(translatedTexts.get(i));
        }
        
        return entries;
    }
    
    /**
     * 将字幕条目列表转换回字幕文件内容
     * @param entries 字幕条目列表
     * @return 字幕文件内容
     */
    public static String rebuildSubtitleContent(List<SubtitleEntry> entries) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < entries.size(); i++) {
            SubtitleEntry entry = entries.get(i);
            sb.append(entry.toString());
            
            // 在每个条目后添加两个换行（除了最后一个条目）
            if (i < entries.size() - 1) {
                sb.append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 将字幕文本分块，每块固定50个条目
     * @param texts 字幕文本列表
     * @param maxChunkSize 此参数在新的实现中不再使用，但保留以维持向后兼容性
     * @return 分块后的文本列表
     */
    public static List<List<String>> chunkSubtitleTexts(List<String> texts, int maxChunkSize) {
        List<List<String>> chunks = new ArrayList<>();
        
        // 每组50个条目一组
        final int ENTRIES_PER_CHUNK = 50;
        
        // 如果没有条目，返回空列表
        if (texts == null || texts.isEmpty()) {
            return chunks;
        }
        
        List<String> currentChunk = new ArrayList<>();
        int count = 0;
        
        for (String text : texts) {
            currentChunk.add(text);
            count++;
            
            // 每放入ENTRIES_PER_CHUNK个条目，创建一个新块
            if (count >= ENTRIES_PER_CHUNK) {
                chunks.add(currentChunk);
                currentChunk = new ArrayList<>();
                count = 0;
            }
        }
        
        // 添加最后一个不满ENTRIES_PER_CHUNK的块
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }
        
        return chunks;
    }
    
    /**
     * 粗略估计文本的token数量 - 支持所有语言
     * @param text 文本
     * @return 估计的token数量
     */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 对于多语言文本的通用粗略估计
        // - CJK字符（中日韩）：每个字符约1.5个token
        // - 拉丁文（英语等）：每4个字符约1个token
        // - 其他语言：视为介于两者之间，每2个字符约1个token
        
        int cjkCount = 0;
        int latinCount = 0;
        int otherCount = 0;
        
        for (char c : text.toCharArray()) {
            if (isCJK(c)) {
                cjkCount++;
            } else if (isLatin(c)) {
                latinCount++;
            } else {
                otherCount++;
            }
        }
        
        return (int)(cjkCount * 1.5 + latinCount * 0.25 + otherCount * 0.5);
    }
    
    /**
     * 判断字符是否是CJK（中日韩）
     * @param c 字符
     * @return 是否是CJK字符
     */
    private static boolean isCJK(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HIRAGANA
                || ub == Character.UnicodeBlock.KATAKANA
                || ub == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
    
    /**
     * 判断字符是否是拉丁字母（英文等）
     * @param c 字符
     * @return 是否是拉丁字母
     */
    private static boolean isLatin(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.BASIC_LATIN
                || ub == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || ub == Character.UnicodeBlock.LATIN_EXTENDED_A
                || ub == Character.UnicodeBlock.LATIN_EXTENDED_B;
    }
}
