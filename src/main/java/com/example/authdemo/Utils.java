package com.example.authdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
public class Utils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public static Map<String, String> parse(String json) throws JsonProcessingException {
        return mapper.readValue(json, Map.class);
    }

    public static Map<String, String> parse(byte[] bytes) throws IOException {
        return mapper.readValue(bytes, Map.class);
    }

    public static String stringify(Map<String, String> obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    public static String stringify(List<? extends Object> list) throws JsonProcessingException {
        return mapper.writeValueAsString(list);
    }

    public static String urlEncoder(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
