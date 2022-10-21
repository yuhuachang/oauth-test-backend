package com.example.authdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotifyService {

    private static ConcurrentHashMap<LocalDateTime, String> history = new ConcurrentHashMap<>();

    private UserService userService;

    @Autowired
    public NotifyService(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(fixedDelay = 10000)
    public void sendNotifyAll() throws IOException, InterruptedException {

        List<String> recipientList = new LinkedList<>();
        for (UserInfo user : userService.getAllUsers()) {
            if (sendNotify(user)) {
                recipientList.add(user.getDisplayName());
            }
        }
        String s = LocalDateTime.now() + " - Notify to: " + String.join(", ", recipientList);
        log.info(s);
        history.put(LocalDateTime.now(), s);
        //TODO: only keep history for one day...
    }

    private boolean sendNotify(UserInfo user) throws IOException, InterruptedException {
        String accessToken = user.getBotAccessToken();
        String message = "Test Message " + LocalDateTime.now();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://notify-api.line.me/api/notify"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofMinutes(1))
                .POST(HttpRequest.BodyPublishers.ofString("message=" + Utils.urlEncoder(message)))
                .build();

        HttpResponse<String> response = Utils.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("notify response: {}\n{}", response.statusCode(), response.body());

        if (response.statusCode() == HttpStatus.OK.value()) {
            return true;
        }
        return userService.verify(user);
    }

    public List<String> getHistory() {
        List<String> result = new LinkedList<>();
        for (Entry<LocalDateTime, String> e : history.entrySet()) {
            result.add(e.getKey() + " - " + e.getValue());
        }
        return result;
    }
}
