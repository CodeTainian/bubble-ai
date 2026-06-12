package com.bubble.bubbleai.ai.model.message;


import dev.langchain4j.service.tool.ToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolExecutedMessage extends StreamMessage{
    private String id;
    private String name;
    private String argument;
    private String result;

    public ToolExecutedMessage (ToolExecution toolExecution){
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        this.id = toolExecution.request().id();
        this.name = toolExecution.request().name();
        this.argument = toolExecution.request().arguments();
        this.result = toolExecution.result();
    }

}
