package com.bubble.bubbleai.service.impl;

import com.bubble.bubbleai.service.ScreenshotService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Service
public class ScreenshotServiceImpl implements ScreenshotService {
    @Override
    public void captureHomePage(String url, String savePath) {
        try (Playwright playwright = createPlaywright()) {
            try (Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
            )) {

                Page page = browser.newPage(
                        new Browser.NewPageOptions()
                                .setViewportSize(1280, 720)
                );

                page.navigate(url);
                page.waitForLoadState();

                page.screenshot(
                        new Page.ScreenshotOptions()
                                .setPath(Paths.get(savePath))
                                .setFullPage(false)
                );
            }
        }
    }

    private Playwright createPlaywright() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader playwrightClassLoader = Playwright.class.getClassLoader();
        try {
            // Playwright's Java driver loads bundled resources through the thread context classloader.
            currentThread.setContextClassLoader(playwrightClassLoader);
            return Playwright.create();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
