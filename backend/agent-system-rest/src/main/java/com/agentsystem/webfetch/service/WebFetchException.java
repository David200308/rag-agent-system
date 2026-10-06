package com.agentsystem.webfetch.service;

/** The page could not be loaded (navigation error, timeout, browser unreachable). */
public class WebFetchException extends RuntimeException {

    public WebFetchException(String message) {
        super(message);
    }

    public WebFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
