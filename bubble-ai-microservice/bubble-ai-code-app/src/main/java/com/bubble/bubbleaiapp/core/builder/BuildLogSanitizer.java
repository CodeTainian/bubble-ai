package com.bubble.bubbleaiapp.core.builder;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class BuildLogSanitizer {
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+"),
            Pattern.compile("(?i)((?:api[_-]?key|token|secret|password|passwd|cookie|session(?:id)?)\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)((?:redis|mysql|postgres(?:ql)?|mongodb)://[^:/\\s]+:)[^@\\s]+@"),
            Pattern.compile("(?i)(\\b(?:sk|sk-proj)-)[A-Za-z0-9_-]{12,}")
    );
    public String sanitize(String text, String projectPath, int limit) {
        if (text == null) return "";
        String safe = text.replaceAll("\\u001B\\[[;\\d]*m", "");
        for (Pattern pattern : SECRETS) safe = pattern.matcher(safe).replaceAll("$1[REDACTED]");
        if (projectPath != null && !projectPath.isBlank())
            safe = safe.replace(Path.of(projectPath).toAbsolutePath().normalize().toString(), "[PROJECT_ROOT]");
        safe = safe.replaceAll("(?<![\\w.])/(?:Users|home|var|opt|private|tmp)/[^\\s:]+", "[SERVER_PATH]");
        if (limit > 0 && safe.length() > limit) {
            int head = Math.min(2000, limit / 4);
            safe = safe.substring(0, head) + "\n... 日志已截断 ...\n" + safe.substring(safe.length() - (limit - head));
        }
        return safe;
    }
}
