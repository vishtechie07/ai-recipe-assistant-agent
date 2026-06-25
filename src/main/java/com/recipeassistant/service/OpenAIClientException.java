package com.recipeassistant.service;

import org.springframework.http.HttpStatusCode;

public class OpenAIClientException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public OpenAIClientException(HttpStatusCode statusCode) {
        super("OpenAI API error");
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public boolean isUnauthorized() {
        return statusCode != null && statusCode.value() == 401;
    }

    public boolean isBadRequest() {
        return statusCode != null && statusCode.value() == 400;
    }
}
