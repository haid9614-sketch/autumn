package com.duong.autumn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Token cũ do client gửi kèm không được chặn đăng nhập/đăng ký.
        return "/api/auth/login".equals(path) || "/api/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Một lần parse kiểm tra chữ ký + hạn dùng trước khi lấy subject.
            Claims claims = jwtService.parseValidatedClaims(header.substring(7).trim());
            UserDetails details = userDetailsService.loadUserByUsername(claims.getSubject());
            if (!claims.getSubject().equals(details.getUsername()) || !details.isEnabled()
                    || !details.isAccountNonLocked() || !details.isAccountNonExpired()
                    || !details.isCredentialsNonExpired()
                    || !jwtService.isTokenVersionValid(claims, details)) {
                reject(response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | AuthenticationException | IllegalArgumentException exception) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.writeError(response, HttpStatus.UNAUTHORIZED,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại.");
    }
}
