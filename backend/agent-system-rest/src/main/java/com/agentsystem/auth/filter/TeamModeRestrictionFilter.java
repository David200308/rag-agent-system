package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Blocks access to features that are unavailable in team mode.
 * Runs after AuthFilter (Order=2) so the mode attribute is already set.
 *
 * Team-mode blocked paths: /api/v1/financial/**
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class TeamModeRestrictionFilter extends OncePerRequestFilter {

    private static final String TEAM = "TEAM";

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/financial/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String mode = (String) request.getAttribute("authenticatedMode");
        if (TEAM.equals(mode)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            Map.of("error", "Financial is not available in team mode.")));
            return;
        }
        chain.doFilter(request, response);
    }
}
