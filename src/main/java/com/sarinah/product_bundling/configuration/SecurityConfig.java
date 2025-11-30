package com.sarinah.product_bundling.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // user simpel: injadmin / inj123
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.withUsername("injadmin")
                .password(encoder.encode("inj123"))
                .roles("INJ") // jadi otoritasnya ROLE_INJ
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // Biar PUT/POST dari JS (fetch) ke API kamu nggak ke-block CSRF
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // resource umum & halaman login tidak perlu login
                        .requestMatchers(
                                "/css/**", "/js/**", "/images/**",
                                "/login", "/error"
                        ).permitAll()

                        // 🔐 semua halaman UI & API Inventory INJ wajib role INJ
                        .requestMatchers(
                                "/catalogue-ui/**"
                        ).hasRole("INJ")

                        // request lain bebas (kalau mau disekat juga boleh)
                        .anyRequest().permitAll()
                )

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
