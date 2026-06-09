package com.bubble.bubbleai.core.SSE;

import com.bubble.bubbleai.model.dto.toolCall.ChatStreamMessage;
import org.springframework.http.codec.ServerSentEvent;

/**
 * SSE 工具类
 */
public class ChatStreamSseUtil {
    private ChatStreamSseUtil(){}

    public static ServerSentEvent<ChatStreamMessage>build (ChatStreamMessage message){
        return ServerSentEvent.<ChatStreamMessage>builder()
                .id(message.getId())
                .event(message.getType().getValue())
                .data(message)
                .build();

    }
}
