package com.duong.autumn.security;

import com.duong.autumn.dto.response.ErrorResponse;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED,
                "Bạn cần đăng nhập hoặc đăng nhập lại.");
    }

    public void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder().timestamp(LocalDateTime.now())
                .status(status.value()).error(status.getReasonPhrase()).message(message).build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

