package com.bankflow.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void optimisticLockingFailure_shouldReturn409Conflict()
            throws Exception {

        mockMvc.perform(get("/test/optimistic-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Resource was modified concurrently. Please retry."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/optimistic-conflict")
        public void triggerConflict() {
            throw new ObjectOptimisticLockingFailureException(
                    "OptimisticLabEntity",
                    1L
            );
        }
    }
}
