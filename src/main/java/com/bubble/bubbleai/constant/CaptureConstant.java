package com.bubble.bubbleai.constant;

public interface CaptureConstant {

    /**
     * application's output path
     */
    String CAPTURE_OUTPUT_COVER = System.getProperty("user.dir") + "/tmp/output_covers";

    /**
     * application's deployment path
     */
    String CAPTURE_DEPLOY_COVER = System.getProperty("user.dir") + "/tmp/deploy_covers";

    /**
     * donate for application
     */
    String CAPTURE_HOST = "http://localhost:8080";

    String CAPTURE_PREVIEW_COVER = "http://localhost:8123/api/static";
}
