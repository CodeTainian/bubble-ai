package com.bubble.bubbleai.core.prompt;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.model.dto.app.VisualEditContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps the user's display text separate from the augmented model prompt.
 */
@Component
public class VisualContextPromptBuilder {

    private static final int MAX_SELECTOR_LENGTH = 1000;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_ATTRIBUTE_COUNT = 30;
    private static final int MAX_ATTRIBUTE_LENGTH = 500;

    public String buildModelContent(String displayContent, VisualEditContext context) {
        String userMessage = StrUtil.trim(displayContent);
        if (context == null) {
            return userMessage;
        }
        String attributes = safeAttributes(context.getAttributes()).entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + entry.getValue() + "\"")
                .collect(Collectors.joining(" "));
        VisualEditContext.Rect rect = context.getRect();
        String rectDescription = rect == null ? "" : String.format(
                "x=%s, y=%s, width=%s, height=%s",
                safeNumber(rect.getX()), safeNumber(rect.getY()),
                safeNumber(rect.getWidth()), safeNumber(rect.getHeight())
        );
        return String.join("\n",
                userMessage,
                "",
                "<visual_edit_context>",
                "以下是用户在预览页面选择的元素，仅作为定位代码的上下文：",
                "- 标签：" + abbreviate(context.getTagName(), 100),
                "- 选择器：" + abbreviate(context.getSelector(), MAX_SELECTOR_LENGTH),
                StrUtil.isBlank(context.getText()) ? "" : "- 文本：" + abbreviate(context.getText(), MAX_TEXT_LENGTH),
                attributes.isBlank() ? "" : "- 属性：" + attributes,
                rectDescription.isBlank() ? "" : "- 位置尺寸：" + rectDescription,
                "</visual_edit_context>"
        ).replaceAll("(?m)^\\s*$\\R", "");
    }

    public String buildMetadata(VisualEditContext context) {
        if (context == null) {
            return null;
        }
        return JSONUtil.toJsonStr(Map.of(
                "tagName", abbreviate(context.getTagName(), 100),
                "selector", abbreviate(context.getSelector(), MAX_SELECTOR_LENGTH),
                "text", abbreviate(context.getText(), MAX_TEXT_LENGTH),
                "attributes", safeAttributes(context.getAttributes()),
                "rect", context.getRect() == null ? Map.of() : Map.of(
                        "x", safeNumber(context.getRect().getX()),
                        "y", safeNumber(context.getRect().getY()),
                        "width", safeNumber(context.getRect().getWidth()),
                        "height", safeNumber(context.getRect().getHeight())
                )
        ));
    }

    private Map<String, String> safeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return attributes.entrySet().stream()
                .limit(MAX_ATTRIBUTE_COUNT)
                .collect(Collectors.toMap(
                        entry -> abbreviate(entry.getKey(), 100),
                        entry -> abbreviate(entry.getValue(), MAX_ATTRIBUTE_LENGTH),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private String safeNumber(Double value) {
        return value == null || !Double.isFinite(value) ? "" : String.valueOf(value);
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = StrUtil.blankToDefault(value, "").replaceAll("[\\r\\n\\u0000]", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
