package com.bubble.bubbleai.core.builder;

import com.bubble.bubbleai.core.handler.GenerationStateService;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.GenerationState;
import com.bubble.bubbleai.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactProjectBuildRepairServiceTest {

    @Mock
    private ReactProjectBuilder projectBuilder;
    @Mock
    private ReactProjectRepairAgent repairAgent;
    @Mock
    private GenerationStateService stateService;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private ReactGenerationProperties properties;
    private ReactProjectBuildRepairService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new ReactGenerationProperties();
        properties.setMaxRepairAttempts(3);
        properties.setStopOnRepeatedError(true);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        service = new ReactProjectBuildRepairService(
                projectBuilder,
                repairAgent,
                properties,
                new BuildLogSanitizer(),
                stateService,
                chatHistoryService,
                redissonClient
        );
    }

    @Test
    void repairsOnceAndStopsAfterSuccessfulBuild() throws Exception {
        BuildResult initial = failure("missing import");
        when(repairAgent.repair(anyLong(), anyString(), anyString()))
                .thenReturn(new ReactProjectRepairAgent.RepairOutcome("fixed import", Set.of("modifyFile")));
        when(projectBuilder.buildProjectWithResult(anyString()))
                .thenReturn(BuildResult.success("npm_build", "npm run build", "ok", "", 10));

        BuildResult result = service.repairAndBuild(
                1L, "generation-1", "build a page", user(), initial, ignored -> { });

        assertTrue(result.success());
        verify(repairAgent, times(1)).repair(eq(1L), anyString(), contains("原始用户需求"));
        verify(projectBuilder, times(1)).buildProjectWithResult(anyString());
        verify(stateService).transition(1L, "generation-1", GenerationState.SUCCESS, 1);
    }

    @Test
    void stopsAtConfiguredMaximumAttempts() throws Exception {
        properties.setStopOnRepeatedError(false);
        when(repairAgent.repair(anyLong(), anyString(), anyString()))
                .thenReturn(new ReactProjectRepairAgent.RepairOutcome("attempted", Set.of("modifyFile")));
        when(projectBuilder.buildProjectWithResult(anyString()))
                .thenReturn(failure("error-1"), failure("error-2"), failure("error-3"));

        BuildResult result = service.repairAndBuild(
                2L, "generation-2", "build a page", user(), failure("initial"), ignored -> { });

        assertFalse(result.success());
        verify(repairAgent, times(3)).repair(eq(2L), anyString(), anyString());
        verify(projectBuilder, times(3)).buildProjectWithResult(anyString());
        verify(stateService).transition(2L, "generation-2", GenerationState.FAILED_MAX_ATTEMPTS, 3);
    }

    @Test
    void warnsOnRepeatedErrorThenStopsIneffectiveLoop() throws Exception {
        properties.setMaxRepairAttempts(5);
        BuildResult sameFailure = failure("same typescript error");
        when(repairAgent.repair(anyLong(), anyString(), anyString()))
                .thenReturn(new ReactProjectRepairAgent.RepairOutcome("attempted", Set.of("modifyFile")));
        when(projectBuilder.buildProjectWithResult(anyString())).thenReturn(sameFailure);

        BuildResult result = service.repairAndBuild(
                3L, "generation-3", "build a page", user(), sameFailure, ignored -> { });

        assertFalse(result.success());
        verify(repairAgent, times(2)).repair(eq(3L), anyString(), anyString());
        verify(repairAgent).repair(eq(3L), anyString(), contains("上一次修改后构建错误 hash 未变化"));
        verify(projectBuilder, times(2)).buildProjectWithResult(anyString());
    }

    private BuildResult failure(String output) {
        return BuildResult.failure("npm_build", "npm run build", 1,
                "", output, "build failed", 10, false);
    }

    private User user() {
        User user = new User();
        user.setId(10L);
        return user;
    }
}
