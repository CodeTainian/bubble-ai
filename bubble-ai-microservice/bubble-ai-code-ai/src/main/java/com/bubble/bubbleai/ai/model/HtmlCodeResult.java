package com.bubble.bubbleai.ai.model;

import jdk.jfr.Description;
import lombok.Data;

@Data
@Description("生成 HTML 带文件结果")
public class HtmlCodeResult {
    @Description("HTML 代码")
    private String HtmlCode;
    @Description("生成代码的描述")
    private String description;
}


