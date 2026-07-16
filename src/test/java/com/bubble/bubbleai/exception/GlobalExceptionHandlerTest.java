package com.bubble.bubbleai.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    @Test
    void unauthenticatedBusinessErrorUsesHttp401() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/app/chat/gen/status");

        var body = handler.businessExceptionHandler(
                new BusinessException(ErrorCode.NOT_LOGIN_ERROR), request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), body.getCode());
    }
}
