package com.bubble.bubbleaiapp.core.prompt;

import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.model.dto.app.VisualEditContext;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class VisualContextPromptBuilder {
    public String buildModelContent(String display, VisualEditContext context) {
        String user = display == null ? "" : display.trim();
        if (context == null) return user;
        String attributes = attributes(context.getAttributes()).entrySet().stream()
                .map(e -> e.getKey() + "=\"" + e.getValue() + "\"").collect(Collectors.joining(" "));
        VisualEditContext.Rect rect = context.getRect();
        String rectDescription = rect == null ? "" : String.format(
                "x=%s, y=%s, width=%s, height=%s", number(rect.getX()), number(rect.getY()),
                number(rect.getWidth()), number(rect.getHeight()));
        return String.join("\n", user, "", "<visual_edit_context>",
                "以下是用户在预览页面选择的元素，仅作为定位代码的上下文：",
                "- 标签：" + safe(context.getTagName(), 100),
                "- 选择器：" + safe(context.getSelector(), 1000),
                context.getText() == null || context.getText().isBlank() ? "" : "- 文本：" + safe(context.getText(), 2000),
                attributes.isBlank() ? "" : "- 属性：" + attributes,
                rectDescription.isBlank() ? "" : "- 位置尺寸：" + rectDescription,
                "</visual_edit_context>").replaceAll("(?m)^\\s*$\\R", "");
    }
    public String buildMetadata(VisualEditContext context) {
        if (context == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tagName", safe(context.getTagName(), 100));
        value.put("selector", safe(context.getSelector(), 1000));
        value.put("text", safe(context.getText(), 2000));
        value.put("attributes", attributes(context.getAttributes()));
        value.put("rect", context.getRect() == null ? Map.of() : Map.of(
                "x", number(context.getRect().getX()), "y", number(context.getRect().getY()),
                "width", number(context.getRect().getWidth()), "height", number(context.getRect().getHeight())));
        return JSONUtil.toJsonStr(value);
    }
    private Map<String,String> attributes(Map<String,String> input) {
        if (input == null) return Map.of();
        return input.entrySet().stream().limit(30).collect(Collectors.toMap(e -> safe(e.getKey(),100), e -> safe(e.getValue(),500), (a,b)->a, LinkedHashMap::new));
    }
    private String safe(String value, int max) {
        String safe = value == null ? "" : value.replaceAll("[\\r\\n\\u0000]", " ");
        return safe.length() <= max ? safe : safe.substring(0,max);
    }
    private String number(Double value) {
        return value == null || !Double.isFinite(value) ? "" : String.valueOf(value);
    }
}
