package com.bubble.bubbleai.service.impl;

import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenshotServiceImplTest {

    @Test
    void acceptsSuccessfulNavigation() {
        Response response = mock(Response.class);
        when(response.ok()).thenReturn(true);

        assertDoesNotThrow(() -> ScreenshotServiceImpl.ensureSuccessfulNavigation(
                response, "http://127.0.0.1:8123/api/static/html_1/"
        ));
    }

    @Test
    void rejectsHttpErrorPage() {
        Response response = mock(Response.class);
        when(response.ok()).thenReturn(false);
        when(response.status()).thenReturn(404);
        when(response.statusText()).thenReturn("Not Found");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ScreenshotServiceImpl.ensureSuccessfulNavigation(
                        response, "http://127.0.0.1:8123/api/static/html_1/"
                )
        );

        assertTrue(error.getMessage().contains("HTTP 404 Not Found"));
    }

    @Test
    void rejectsMissingHttpResponse() {
        assertThrows(
                IllegalStateException.class,
                () -> ScreenshotServiceImpl.ensureSuccessfulNavigation(
                        null, "http://127.0.0.1:8123/api/static/html_1/"
                )
        );
    }
}
