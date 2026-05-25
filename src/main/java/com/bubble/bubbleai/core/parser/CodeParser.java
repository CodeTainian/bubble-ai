package com.bubble.bubbleai.core.parser;


/**
 * 代码解析器
 */
public interface CodeParser<T> {

    /**
     * 定义一个范型描述解析器
     * @param userMessage
     * @return 解析的代码类型
     */
    T parserCode(String userMessage);

}
