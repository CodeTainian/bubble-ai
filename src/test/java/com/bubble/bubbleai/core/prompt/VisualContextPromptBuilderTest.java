package com.bubble.bubbleai.core.prompt;

import com.bubble.bubbleai.model.dto.app.VisualEditContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualContextPromptBuilderTest {

    private final VisualContextPromptBuilder builder = new VisualContextPromptBuilder();

    @Test
    void augmentsModelContentWithoutChangingDisplayContent() {
        String displayContent = "把这个按钮改成蓝色";
        VisualEditContext context = new VisualEditContext();
        context.setTagName("button");
        context.setSelector("#submit");
        context.setAttributes(Map.of("class", "primary"));

        String modelContent = builder.buildModelContent(displayContent, context);

        assertEquals("把这个按钮改成蓝色", displayContent);
        assertTrue(modelContent.startsWith(displayContent));
        assertTrue(modelContent.contains("#submit"));
        assertTrue(builder.buildMetadata(context).contains("selector"));
    }
}
