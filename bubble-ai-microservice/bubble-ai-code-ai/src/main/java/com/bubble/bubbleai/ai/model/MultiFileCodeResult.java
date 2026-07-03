package com.bubble.bubbleai.ai.model;

import jdk.jfr.Description;
import lombok.Data;

@Data
@Description("生成多个文件的描述")
public class MultiFileCodeResult {
    @Description("HTML代码")
    private String htmlCode;
    @Description("Css代码")
    private String cssCode;
    @Description("JS代码")
    private String jsCode;
    @Description("生成代码的描述")
    private String description;
}
