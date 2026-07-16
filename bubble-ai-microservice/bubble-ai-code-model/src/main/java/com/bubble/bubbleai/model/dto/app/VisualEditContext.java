package com.bubble.bubbleai.model.dto.app;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
public class VisualEditContext implements Serializable {
    private String tagName;
    private String selector;
    private String text;
    private Map<String, String> attributes;
    private Rect rect;

    @Data
    public static class Rect implements Serializable {
        private Double x;
        private Double y;
        private Double width;
        private Double height;
        @Serial private static final long serialVersionUID = 1L;
    }

    @Serial private static final long serialVersionUID = 1L;
}
