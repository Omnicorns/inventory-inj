package com.sarinah.product_bundling.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Akun admin UI. Username dan password wajib dari environment (INJ_ADMIN_USERNAME / INJ_ADMIN_PASSWORD);
     * tidak ada nilai bawaan di source code.
     */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.security.admin-username}") String username,
            @Value("${app.security.admin-password}") String password
    ) {
        if (username == null || username.isBlank() || password == null || password.length() < 12) {
            throw new IllegalStateException("INJ_ADMIN_USERNAME wajib diisi dan INJ_ADMIN_PASSWORD minimal 12 karakter");
        }
        UserDetails admin = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("INJ") // otoritas ROLE_INJ
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, @Value("${api.key}") String apiKey) throws Exception {

        OrRequestMatcher apiPaths = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/sarinah-forwarder/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")
        );

        http
                // CSRF aktif untuk aksi dari browser (token disisipkan form Thymeleaf dan header X-CSRF-TOKEN
                // pada fetch). Klien sistem yang memakai X-API-KEY tidak memakai cookie, jadi dikecualikan.
                .csrf(csrf -> csrf.ignoringRequestMatchers(request -> request.getHeader(ApiKeyInjFilter.HEADER) != null))

                .addFilterBefore(new ApiKeyInjFilter(apiKey), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // resource umum & halaman login tidak perlu login
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // gambar katalog hanya dibaca (GET)
                        .requestMatchers(HttpMethod.GET, "/api/catalogue/*/image").permitAll()

                        // semua halaman UI dan API Inventory INJ wajib role INJ (login atau X-API-KEY)
                        .requestMatchers("/catalogue-ui/**", "/sarinah-forwarder/**", "/api/**").hasRole("INJ")

                        // default: apa pun yang belum disebut di atas wajib login
                        .anyRequest().authenticated()
                )

                // klien API mendapat 401 (bukan redirect ke halaman login)
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiPaths))

                .formLogin(form -> form
                        .loginPage("/login")                  // GET view login
                        .loginProcessingUrl("/login")         // POST form login
                        .defaultSuccessUrl("/catalogue-ui/inj-spec-list", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}
