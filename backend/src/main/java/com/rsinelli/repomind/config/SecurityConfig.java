package com.rsinelli.repomind.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String frontendOrigin;
  private final GitHubOAuth2UserService gitHubOAuth2UserService;

  SecurityConfig(
      @Value("${repomind.frontend-origin}") String frontendOrigin,
      GitHubOAuth2UserService gitHubOAuth2UserService) {
    this.frontendOrigin = frontendOrigin;
    this.gitHubOAuth2UserService = gitHubOAuth2UserService;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .cors(Customizer.withDefaults())
        .csrf(
            csrf ->
                csrf
                    // Cookie legivel por JS para o SPA reenviar em X-XSRF-TOKEN.
                    // withHttpOnlyFalse e proposital: com HttpOnly o SPA nao le o valor.
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    // Sem isso o token so e resolvido de forma preguicosa e o cookie
                    // pode nao ser emitido no primeiro GET.
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .oauth2Login(
            oauth ->
                oauth
                    .userInfoEndpoint(info -> info.userService(gitHubOAuth2UserService))
                    // Apos o callback do GitHub, devolve o usuario para o SPA.
                    .successHandler(
                        new SimpleUrlAuthenticationSuccessHandler(frontendOrigin + "/repositories"))
                    .failureHandler(
                        new SimpleUrlAuthenticationFailureHandler(
                            frontendOrigin + "/login?error=oauth")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    // 204 em vez de redirect: quem chama e o SPA, via fetch.
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .exceptionHandling(
            ex ->
                // Chamada de API sem sessao recebe 401, nao um 302 para uma pagina de
                // login que o SPA nao sabe renderizar.
                ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendOrigin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // Necessario para o cookie de sessao atravessar a origem em dev.
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
