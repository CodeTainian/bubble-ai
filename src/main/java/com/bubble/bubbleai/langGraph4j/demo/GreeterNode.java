package com.bubble.bubbleai.langGraph4j.demo;

import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

public class GreeterNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) throws Exception {
        System.out.println("GreeterNode executing. Current messages: " + state.messages());
        return Map.of(SimpleState.MESSAGES_KEY, "Hello from GreeterNode!");
    }

}
