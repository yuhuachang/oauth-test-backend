package com.example.authdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserService {

    @Value("${line.client_id}")
    private String clientId;

    @Value("${line.client_secret}")
    private String clientSecret;

    private static Map<String, UserInfo> db = new ConcurrentHashMap<>();

    public void saveUserInfo(UserInfo user) {
        log.info("save user: {}", user);
        db.put(user.getUserId(), user);
    }

    public UserInfo getUserInfo(String userId) {
        log.info("get user: {}", userId);
        return db.get(userId);
    }

    public List<UserInfo> getAllUsers() {
        return db.values().stream().toList();
    }

    public void removeUserInfo(String userId) {
        log.info("remove user: {}", userId);
        db.remove(userId);
    }

    public boolean verify(UserInfo user) throws IOException, InterruptedException {
        log.info("Verify access token for user {}", user.getUserId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.line.me/v2/oauth/verify"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "access_token=" + Utils.urlEncoder(user.getAccessToken())))
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("verify response: {}\n{}", response.statusCode(), response.body());

        if (response.statusCode() == HttpStatus.OK.value()) {

            // Read response data
            Map<String, String> data = Utils.parse(response.body());

            // Permissions granted to the access token.
            // P: You have permission to access the user's profile information.
            if (!"P".equals(data.get("scope"))) {
                log.info("scope is wrong!");
                return false;
            }

            if (!clientId.equals(data.get("client_id"))) {
                log.info("client_id mismatch!");
                return false;
            }

            // Number of seconds until the access token expires.
            long expiresIn = Long.parseLong(data.get("expires_in"));
            if (expiresIn < 3600) {
                log.info("This token is almost expired. Refresh the token.");
                return refreshAccessToken(user);
            }
            return true;
        } else if (response.statusCode() == 400) {
            log.info("This token is expired. Try to refresh the token.");
            return refreshAccessToken(user);
        }
        return false;
    }

    private boolean refreshAccessToken(UserInfo user) throws IOException, InterruptedException {
        log.info("Refresh access token for userId {}", user.getUserId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.line.me/v2/oauth/accessToken"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=refresh_token" +
                                "&refresh_token=" + Utils.urlEncoder(user.getRefreshToken()) +
                                "&client_id=" + clientId +
                                "&client_secret=" + clientSecret))
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Refresh response: {}\n{}", response.statusCode(), response.body());

        if (response.statusCode() == HttpStatus.OK.value()) {

            // Read response data
            Map<String, String> data = Utils.parse(response.body());

            user.setTokenType(data.get("token_type"));
            user.setAccessToken(data.get("access_token"));
            user.setRefreshToken(data.get("refresh_token"));

            return true;
        } else {

        }
        return false;
    }

    public boolean revokeAccessToken(UserInfo user) throws IOException, InterruptedException {
        log.info("Revoke access token for userId {}", user.getUserId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.line.me/v2/oauth/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "refresh_token=" + Utils.urlEncoder(user.getRefreshToken())))
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("logout response: {}\n{}", response.statusCode(), response.body());

        db.remove(user.getUserId());

        return response.statusCode() == HttpStatus.OK.value();
    }
}
