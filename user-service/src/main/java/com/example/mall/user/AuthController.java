package com.example.mall.user;

import com.example.mall.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Credentials(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,32}") String username,
                              @NotBlank @Size(min = 8, max = 64) String password) {}
    public record User(long id, String username) {}
    private record Account(long id, String username, String passwordHash) {}

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User register(@Valid @RequestBody Credentials request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "密码的 UTF-8 长度不能超过 72 字节");
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users(username,password_hash) VALUES(?,?)", new String[]{"id"});
            statement.setString(1, request.username());
            statement.setString(2, passwords.encode(request.password()));
            return statement;
        }, key);
        long id = key.getKey().longValue();
        log.info("User registered userId={}", id);
        return new User(id, request.username());
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Credentials request) {
        if (request.username() == null || request.password() == null || request.password().length() > 64
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(401, "用户名或密码错误");
        }
        List<Account> accounts = jdbc.query("SELECT id,username,password_hash FROM users WHERE username=?",
                (row, n) -> new Account(row.getLong("id"), row.getString("username"), row.getString("password_hash")),
                request.username());
        if (accounts.isEmpty() || !passwords.matches(request.password(), accounts.get(0).passwordHash())) {
            throw new ApiException(401, "用户名或密码错误");
        }
        Account account = accounts.get(0);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO user_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
                hash(token), account.id(), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        log.info("Login succeeded userId={}", account.id());
        return Map.of("token", token, "user", new User(account.id(), account.username()));
    }

    @GetMapping({"/api/auth/me", "/internal/users/me"})
    public User me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 200) {
            throw new ApiException(401, "请先登录");
        }
        List<User> users = jdbc.query("SELECT u.id,u.username FROM users u JOIN user_sessions s ON u.id=s.user_id "
                        + "WHERE s.token_hash=? AND s.expires_at>?", (row, n) -> new User(row.getLong("id"), row.getString("username")),
                hash(authorization.substring(7)), Timestamp.from(Instant.now()));
        if (users.isEmpty()) throw new ApiException(401, "登录已过期，请重新登录");
        return users.get(0);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
