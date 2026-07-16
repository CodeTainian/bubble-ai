package com.bubble.bubbleai.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorContext implements Serializable {

    private String userId;

    private String appId;

    private String generationId;

    private String requestId;

    private String actorType;

    public MonitorContext snapshot() {
        return MonitorContext.builder()
                .userId(userId)
                .appId(appId)
                .generationId(generationId)
                .requestId(requestId)
                .actorType(actorType)
                .build();
    }

    public static MonitorContext system() {
        return MonitorContext.builder()
                .userId("system")
                .appId("unknown")
                .requestId(java.util.UUID.randomUUID().toString())
                .actorType("SYSTEM")
                .build();
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
