package com.bankflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_shouldReturn200_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/security/public"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_shouldReturn401_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/security/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_shouldReturn200_withAuthentication() throws Exception {
        mockMvc.perform(get("/security/me")
                        .with(httpBasic("bankflow", "123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("bankflow"))
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void protectedEndpoint_shouldReturn401_withWrongPassword() throws Exception {
        mockMvc.perform(get("/security/me")
                .with(httpBasic("bankflow", "12345")))
                .andExpect(status().isUnauthorized());
    }
}
