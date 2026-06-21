package com.ragagent.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Several controllers (Travel, Financial, Organization, Team, ...) accept raw
 * Map&lt;String,Object&gt; request bodies and parse fields by hand — a wrong JSON
 * type or an unparsable number/date throws an unchecked exception that, absent
 * this handler, escapes as a bare 500 with no JSON body. This converts those
 * into clean 400s. Controllers that already catch a given exception locally
 * (e.g. SecurityException → 403) are unaffected — this only handles what
 * escapes past them.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            NumberFormatException.class,
            ClassCastException.class,
            DateTimeParseException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, String>> handleMalformedInput(Exception e) {
        log.warn("[GlobalExceptionHandler] Rejected malformed request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request: " + e.getMessage()));
    }
}
