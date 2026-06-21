package com.agentsystem.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.format.DateTimeParseException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMalformedInput_numberFormatException_returns400() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleMalformedInput(new NumberFormatException("For input string: \"abc\""));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsKey("error");
        assertThat(resp.getBody().get("error")).contains("For input string");
    }

    @Test
    void handleMalformedInput_classCastException_returns400() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleMalformedInput(new ClassCastException("Integer cannot be cast to String"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMalformedInput_dateTimeParseException_returns400() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleMalformedInput(new DateTimeParseException("bad date", "not-a-date", 0));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMalformedInput_httpMessageNotReadable_returns400() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleMalformedInput(new HttpMessageNotReadableException("malformed JSON"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMalformedInput_illegalArgumentException_returns400() {
        ResponseEntity<Map<String, String>> resp =
                handler.handleMalformedInput(new IllegalArgumentException("bad input"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).contains("bad input");
    }
}
