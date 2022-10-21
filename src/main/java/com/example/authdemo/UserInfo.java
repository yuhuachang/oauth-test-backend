package com.example.authdemo;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class UserInfo {

    private String userId;
    private String displayName;
    private String tokenIssuer;
    private String idToken;
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private String botAccessToken;

    @Override
    public String toString() {
        return new StringBuilder().append("UserInfo(")
                .append("userId=").append(userId).append(", ")
                .append("displayName=").append(displayName).append(", ")
                .append("idToken=").append(idToken != null).append(", ")
                .append("accessToken=").append(accessToken != null).append(", ")
                .append("refreshToken=").append(refreshToken != null).append(", ")
                .append("botAccessToken=").append(botAccessToken != null).append(")")
                .toString();
    }
}
