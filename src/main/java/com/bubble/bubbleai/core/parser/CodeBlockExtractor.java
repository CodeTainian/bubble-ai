package com.bubble.bubbleai.core.parser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 AI 回复的 Markdown 内容中提取真正的代码块。
 */
public final class CodeBlockExtractor {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```\\s*([\\w+-]*)[^\\n`]*\\R([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[/!]?[a-z][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> HTML_FILE_NAMES = Set.of("index.html", "index.htm");
    private static final Set<String> CSS_FILE_NAMES = Set.of("style.css", "styles.css");
    private static final Set<String> JS_FILE_NAMES = Set.of("script.js", "main.js", "index.js");

    private CodeBlockExtractor() {
    }

    public static String extractHtmlCode(String content) {
        String htmlCode = extractBestCode(content, "html", HTML_FILE_NAMES);
        if (htmlCode != null) {
            return htmlCode;
        }
        return extractHtmlFromRawText(content);
    }

    public static String extractCssCode(String content) {
        return extractBestCode(content, "css", CSS_FILE_NAMES);
    }

    public static String extractJsCode(String content) {
        return extractBestCode(content, "javascript", JS_FILE_NAMES);
    }

    public static String extractSummaryAfterLastCodeBlock(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        int lastCodeBlockEndIndex = -1;
        while (matcher.find()) {
            lastCodeBlockEndIndex = matcher.end();
        }
        if (lastCodeBlockEndIndex == -1 || lastCodeBlockEndIndex >= content.length()) {
            return null;
        }
        return content.substring(lastCodeBlockEndIndex).trim();
    }

    private static String extractBestCode(String content, String targetLanguage, Set<String> fileNames) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        String bestCode = null;
        int bestScore = Integer.MIN_VALUE;
        while (matcher.find()) {
            String language = normalizeLanguage(matcher.group(1));
            if (!languageMatches(language, targetLanguage)) {
                continue;
            }
            String code = stripLeadingFileName(matcher.group(2), fileNames);
            int score = scoreCode(code, targetLanguage);
            if (score > bestScore) {
                bestScore = score;
                bestCode = code;
            }
        }
        return bestScore > 0 ? bestCode : null;
    }

    private static String extractHtmlFromRawText(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int docTypeIndex = lowerContent.indexOf("<!doctype");
        int htmlIndex = lowerContent.indexOf("<html");
        int startIndex = firstExistingIndex(docTypeIndex, htmlIndex);
        if (startIndex < 0) {
            return null;
        }
        int htmlEndIndex = lowerContent.lastIndexOf("</html>");
        if (htmlEndIndex >= startIndex) {
            return content.substring(startIndex, htmlEndIndex + "</html>".length()).trim();
        }
        return content.substring(startIndex).trim();
    }

    private static int firstExistingIndex(int... indexes) {
        return Arrays.stream(indexes)
                .filter(index -> index >= 0)
                .min()
                .orElse(-1);
    }

    private static String stripLeadingFileName(String code, Set<String> fileNames) {
        if (code == null) {
            return null;
        }
        String normalizedCode = code.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedCode.split("\n", -1);
        int index = 0;
        while (index < lines.length && lines[index].trim().isEmpty()) {
            index++;
        }
        if (index >= lines.length || !isFileNameLine(lines[index], fileNames)) {
            return normalizedCode.trim();
        }
        return String.join("\n", Arrays.copyOfRange(lines, index + 1, lines.length)).trim();
    }

    private static boolean isFileNameLine(String line, Set<String> fileNames) {
        String normalizedLine = normalizeFileNameLine(line);
        return fileNames.contains(normalizedLine);
    }

    private static String normalizeFileNameLine(String line) {
        String normalized = line.trim()
                .replace("`", "")
                .replace("：", ":")
                .toLowerCase(Locale.ROOT);
        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex >= 0) {
            normalized = normalized.substring(colonIndex + 1).trim();
        }
        return normalized;
    }

    private static boolean languageMatches(String language, String targetLanguage) {
        if ("javascript".equals(targetLanguage)) {
            return "javascript".equals(language) || "js".equals(language);
        }
        if ("html".equals(targetLanguage)) {
            return "html".equals(language) || "htm".equals(language);
        }
        return targetLanguage.equals(language);
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private static int scoreCode(String code, String language) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        String trimmedCode = code.trim();
        if (isOnlyKnownFileName(trimmedCode)) {
            return 0;
        }
        String lowerCode = trimmedCode.toLowerCase(Locale.ROOT);
        int score = Math.min(trimmedCode.length() / 120, 20);
        switch (language) {
            case "html" -> {
                if (!lowerCode.contains("<")) {
                    return 0;
                }
                if (HTML_TAG_PATTERN.matcher(trimmedCode).find()) {
                    score += 3;
                }
                if (lowerCode.contains("<!doctype")) {
                    score += 40;
                }
                if (lowerCode.contains("<html")) {
                    score += 36;
                }
                if (lowerCode.contains("</html>")) {
                    score += 24;
                }
                if (lowerCode.contains("<head")) {
                    score += 12;
                }
                if (lowerCode.contains("<body")) {
                    score += 12;
                }
                if (lowerCode.contains("<script")) {
                    score += 5;
                }
                if (lowerCode.contains("<style")) {
                    score += 5;
                }
                if (lowerCode.contains("<div") || lowerCode.contains("<section") || lowerCode.contains("<main")) {
                    score += 4;
                }
                if (lowerCode.contains("<footer") || lowerCode.contains("<header") || lowerCode.contains("</")) {
                    score += 3;
                }
            }
            case "css" -> {
                if (!lowerCode.contains("{") || !lowerCode.contains("}")) {
                    return 0;
                }
                score += 20;
                if (lowerCode.contains(":") && lowerCode.contains(";")) {
                    score += 10;
                }
                if (lowerCode.contains("@media")) {
                    score += 5;
                }
            }
            case "javascript" -> {
                if (lowerCode.length() < 8) {
                    return 0;
                }
                if (lowerCode.contains("function") || lowerCode.contains("=>")) {
                    score += 12;
                }
                if (lowerCode.contains("const ") || lowerCode.contains("let ") || lowerCode.contains("var ")) {
                    score += 10;
                }
                if (lowerCode.contains("document.") || lowerCode.contains("window.")) {
                    score += 8;
                }
                if (lowerCode.contains(";") || lowerCode.contains("{")) {
                    score += 5;
                }
            }
            default -> score += 1;
        }
        return score;
    }

    private static boolean isOnlyKnownFileName(String code) {
        Set<String> fileNames = new HashSet<>();
        fileNames.addAll(HTML_FILE_NAMES);
        fileNames.addAll(CSS_FILE_NAMES);
        fileNames.addAll(JS_FILE_NAMES);
        return fileNames.contains(normalizeFileNameLine(code));
    }
}
