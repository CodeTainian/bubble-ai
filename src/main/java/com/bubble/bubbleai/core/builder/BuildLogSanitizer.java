package com.bubble.bubbleai.core.builder;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Removes credentials and server paths before logs leave the build subsystem. */
@Component
public class BuildLogSanitizer {

    private static final String REDACTED = "$1[REDACTED]";
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+"),
            Pattern.compile("(?i)((?:api[_-]?key|token|secret|password|passwd|cookie|session(?:id)?)\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)((?:redis|mysql|postgres(?:ql)?|mongodb)://[^:/\\s]+:)[^@\\s]+@"),
            Pattern.compile("(?i)(\\b(?:sk|sk-proj)-)[A-Za-z0-9_-]{12,}")
    );

    public String sanitize(String log, String projectPath, int maxLength) {
        if (StrUtil.isBlank(log)) {
            return "";
        }
        String sanitized = log.replaceAll("\\u001B\\[[;\\d]*m", "");
        for (Pattern pattern : SECRET_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(REDACTED);
        }
        if (StrUtil.isNotBlank(projectPath)) {
            String absoluteProjectPath = Path.of(projectPath).toAbsolutePath().normalize().toString();
            sanitized = sanitized.replace(absoluteProjectPath, "[PROJECT_ROOT]");
        }
        // Hide other common Unix/macOS server paths without altering relative source paths.
        sanitized = sanitized.replaceAll("(?<![\\w.])/(?:Users|home|var|opt|private|tmp)/[^\\s:]+", "[SERVER_PATH]");
        if (maxLength > 0 && sanitized.length() > maxLength) {
            int headLength = Math.min(maxLength / 4, 2000);
            int tailLength = maxLength - headLength;
            sanitized = sanitized.substring(0, headLength)
                    + "\n... 日志已截断 ...\n"
                    + sanitized.substring(sanitized.length() - tailLength);
        }
        return sanitized;
    }
}
