package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.TNMUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {
    @Autowired
    TNMUserDetailsService userDetailsService;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Never save static asset requests as the post-login redirect target
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(new OrRequestMatcher(
            new AntPathRequestMatcher("/**/*.js"),
            new AntPathRequestMatcher("/**/*.css"),
            new AntPathRequestMatcher("/**/*.ico"),
            new AntPathRequestMatcher("/**/*.png"),
            new AntPathRequestMatcher("/**/*.svg"),
            new AntPathRequestMatcher("/**/*.woff"),
            new AntPathRequestMatcher("/**/*.woff2")
        )));

        http
            .requestCache(cache -> cache.requestCache(requestCache))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/zmanim/**", "/orgs/**", "/org/**", "/admin/login", "/admin/logout",
                                "/webjars/**", "/**/*.css", "/**/*.js", "/static/**", "/db/**",
                                "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin", "/admin/dashboard", "/admin/organization", "/admin/account", 
                                "/admin/update-organization", "/admin/update-account", 
                                "/admin/*/locations", "/admin/*/locations/**",
                                "/admin/create-location", "/admin/update-location", "/admin/delete-location", 
                                "/admin/*/minyanim", "/admin/*/minyanim/**", 
                                "/admin/*/calendar-entries", "/admin/*/calendar-entries/**",
                                "/admin/settings", "/admin/update-settings").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                .requestMatchers("/{slug}", "/{slug}/next", "/{slug}/last").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/j_spring_security_check")
                .loginPage("/admin/login")
                .defaultSuccessUrl("/admin")
                .failureUrl("/admin/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout=true")
                .permitAll()
            )
            .rememberMe(rememberMe -> rememberMe
                .key("uniqueAndSecret")
            );

        return http.build();
    }
}