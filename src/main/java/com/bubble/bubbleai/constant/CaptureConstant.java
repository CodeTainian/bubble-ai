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
     * 应用封面访问前缀。
     */
    String CAPTURE_HOST = "/api";

}
