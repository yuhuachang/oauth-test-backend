package com.example.authdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin("*/*")
@RestController
@RequestMapping("/v1")
public class MyController {

    private UserService userService;

    private NotifyService service;

    @Value("${frontend_server_uri}")
    private String frontendServerUri;

    @Value("${backend_server_uri}")
    private String backendServerUri;

    @Value("${line.client_id}")
    private String clientId;

    @Value("${line.client_secret}")
    private String clientSecret;

    @Value("${linebot.client_id}")
    private String botClientId;

    @Value("${linebot.client_secret}")
    private String botClientSecret;

    @Autowired
    public MyController(UserService userService, NotifyService service) {
        this.userService = userService;
        this.service = service;
    }

    @GetMapping("/linecallback")
    public ResponseEntity<String> linecallback(
            @RequestParam(value = "code") String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "friendship_status_changed", required = false) boolean friendshipStatusChanged,
            @RequestParam(value = "liffClientId", required = false) String liffClientId,
            @RequestParam(value = "liffRedirectUri", required = false) String liffRedirectUri,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription
    ) throws IOException, InterruptedException {
        log.info("Received LINE login auth code: {}", code);

        if (error != null) {
            log.info("callback endpoint receives an error: {} {}", error, errorDescription);
            Map<String, String> errorObject = new HashMap<>();
            errorObject.put("error", error);
            errorObject.put("error_description", errorDescription);
            return ResponseEntity.status(400)
                    .body(Utils.stringify(errorObject));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.line.me/oauth2/v2.1/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=authorization_code" +
                        "&code=" + code +
                        "&redirect_uri=" + Utils.urlEncoder(backendServerUri + "/v1/linecallback") +
                        "&client_id=" + clientId +
                        "&client_secret=" + clientSecret
                )).build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("token endpoint response: {}\n{}", response.statusCode(), response.body());
        if (response.statusCode() != 200) {
            return ResponseEntity.status(response.statusCode())
                    .body(response.body());
        }

        Map<String, String> data = Utils.parse(response.body());
        UserInfo user = new UserInfo();
        user.setTokenType(data.get("token_type"));
        user.setIdToken(data.get("id_token"));
        user.setAccessToken(data.get("access_token"));
        user.setRefreshToken(data.get("refresh_token"));
        log.info("user: {}", user);

        getUserProfile(user);
        extractJwt(user);

        userService.saveUserInfo(user);

        return ResponseEntity
                .status(HttpStatus.TEMPORARY_REDIRECT)
                .header("Location", frontendServerUri + "/#callback=line&userid=" + user.getUserId())
                .build();
    }

    private void getUserProfile(UserInfo user) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.line.me/v2/profile"))
                .header("Authorization", user.getTokenType() + " " + user.getAccessToken())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HttpStatus.OK.value()) {
            Map<String, String> data = Utils.parse(response.body());
            user.setUserId(data.get("userId"));
            user.setDisplayName(data.get("displayName"));
        }
    }

    private void extractJwt(UserInfo user) throws IOException {
        String idToken = user.getIdToken();
        if (idToken == null) {
            log.info("There is no id_token");
            return;
        }
        String[] parts = idToken.split("\\.");
        String headerPart = parts[0];
        String payloadPart = parts[1];
        String signaturePart = parts[2];

        byte[] header = Base64.getDecoder().decode(headerPart);
        byte[] payload = Base64.getDecoder().decode(payloadPart);

        Map<String, String> headerMap = Utils.parse(header);
        Map<String, String> payloadMap = Utils.parse(payload);

        log.debug("header: {}", headerMap);
        log.debug("payload: {}", payloadMap);
        log.debug("signature: {}", signaturePart);

        String type = headerMap.get("typ");
        String algorithm = headerMap.get("alg");

        //TODO: verify JWT
        // https://developers.line.biz/en/docs/line-login/verify-id-token/#get-profile-info-from-id-token
        String issuer = payloadMap.get("iss");
        String subject = payloadMap.get("sub");
        String name = payloadMap.get("name");
        user.setTokenIssuer(issuer);
    }

    @GetMapping("/username")
    public ResponseEntity<String> getUserDisplayName(@RequestParam("userId") String userId) throws IOException, InterruptedException {
        UserInfo user = userService.getUserInfo(userId);
        boolean exists = user != null;
        log.info("User {} exists? {}", userId, exists);
        return ResponseEntity.ok(exists ? user.getDisplayName() : "");
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> revokeAccessToken(@PathVariable String userId) throws IOException, InterruptedException {
        log.info("Logout LINE. UserID: {}", userId);

        UserInfo user = userService.getUserInfo(userId);
        if (user != null) {
            userService.revokeAccessToken(user);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/linebotcallback")
    public ResponseEntity<String> linebotcallback(
            @RequestParam(value = "code") String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription
    ) throws IOException, InterruptedException {
        log.info("Received LINE bot login auth code: {} state (userId): {}", code, state);

        if (error != null) {
            log.info("callback endpoint receives an error: {} {}", error, errorDescription);
            Map<String, String> errorObject = new HashMap<>();
            errorObject.put("error", error);
            errorObject.put("error_description", errorDescription);
            return ResponseEntity.status(400).body(Utils.stringify(errorObject));
        }

        String userId = state;
        UserInfo user = userService.getUserInfo(userId);
        if (user == null) {
            Map<String, String> errorObject = new HashMap<>();
            errorObject.put("error", "Not Authorized");
            errorObject.put("error_description", "Login with LINE first.");
            return ResponseEntity.status(401).body(Utils.stringify(errorObject));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://notify-bot.line.me/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=authorization_code" +
                                "&code=" + code +
                                "&redirect_uri=" + Utils.urlEncoder(backendServerUri + "/v1/linebotcallback") +
                                "&client_id=" + botClientId +
                                "&client_secret=" + botClientSecret
                )).build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("token endpoint response: {}\n{}", response.statusCode(), response.body());
        if (response.statusCode() != 200) {
            return ResponseEntity.status(response.statusCode())
                    .body(response.body());
        }

        // have access_token only
        Map<String, String> data = Utils.parse(response.body());
        String accessToken = data.get("access_token");

        user.setBotAccessToken(accessToken);
        log.info("user: {}", user);
        userService.saveUserInfo(user);

        return ResponseEntity
                .status(HttpStatus.TEMPORARY_REDIRECT)
                .header("Location", frontendServerUri + "/#callback=linebot")
                .build();
    }


    @GetMapping("/user/{userId}/status")
    public ResponseEntity<String> checkStatus(@PathVariable String userId) throws IOException, InterruptedException {
        log.info("Check login status for userID: {}", userId);

        UserInfo user = userService.getUserInfo(userId);
        if (user == null) {
            Map<String, String> errorObject = new HashMap<>();
            errorObject.put("error", "Not Authorized");
            errorObject.put("error_description", "Login with LINE first.");
            return ResponseEntity.status(401).body(Utils.stringify(errorObject));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://notify-api.line.me/api/status"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + user.getBotAccessToken())
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("status check response: {}\n{}", response.statusCode(), response.body());
        return ResponseEntity.status(response.statusCode()).body(response.body());
    }

    @PutMapping("/user/{userId}")
    public ResponseEntity<String> revokeService(@PathVariable String userId) throws IOException, InterruptedException {
        log.info("Unsubscribe LINE Notify service for userID: {}", userId);

        UserInfo user = userService.getUserInfo(userId);
        if (user == null) {
            Map<String, String> errorObject = new HashMap<>();
            errorObject.put("error", "Not Authorized");
            errorObject.put("error_description", "Login with LINE first.");
            return ResponseEntity.status(401).body(Utils.stringify(errorObject));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://notify-api.line.me/api/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + user.getBotAccessToken())
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("status check response: {} {}", response.statusCode(), response.body());
        return ResponseEntity.status(response.statusCode()).body(response.body());
    }

    @GetMapping("notify")
    public ResponseEntity<Void> sendNotifyAll() throws IOException, InterruptedException {
        log.info("Trigger LINE Notify");

        service.sendNotifyAll();
        return ResponseEntity.ok().build();
    }

    @GetMapping("history")
    public ResponseEntity<List<String>> getNotifyHistory() throws IOException, InterruptedException {
        log.info("Show Notify history");
        return ResponseEntity.ok(service.getHistory());
    }
}
