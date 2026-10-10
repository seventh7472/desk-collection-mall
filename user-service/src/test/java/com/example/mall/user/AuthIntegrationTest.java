package com.example.mall.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:users;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always",
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test void registrationLoginAndTokenValidationProtectAccount() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"LabPass123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("username").value("student"));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"LabPass123!\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"LabPass123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("student"));
        mvc.perform(get("/internal/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/users/me").header("Authorization", "Bearer " + token)
                .header("X-Internal-Key", "mall-lab-internal-key-change-me"))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("student"));
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username='student'", String.class))
                .startsWith("$2").isNotEqualTo("LabPass123!");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM user_sessions", String.class))
                .hasSize(64).isNotEqualTo(token);
        jdbc.update("UPDATE user_sessions SET expires_at='2000-01-01 00:00:00'");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void invalidRegistrationRejectedBeforePersistence() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"x\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
