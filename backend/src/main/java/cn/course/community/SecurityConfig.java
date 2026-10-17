package cn.course.community;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService users(JdbcTemplate db){return email->{var rows=db.queryForList("SELECT email,password_hash FROM app_user WHERE email=?",email);if(rows.isEmpty())throw new UsernameNotFoundException("Account not found");var u=rows.getFirst();return User.withUsername((String)u.get("email")).password((String)u.get("password_hash")).roles("USER").build();};}
 @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
  http.authorizeHttpRequests(a->a.requestMatchers("/api/auth/csrf","/api/auth/login","/","/index.html","/assets/**","/favicon.svg","/error").permitAll().requestMatchers("/api/**").authenticated().anyRequest().denyAll())
   .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
   .addFilterBefore(new LoginThrottle(),org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
   .formLogin(f->f.loginProcessingUrl("/api/auth/login").successHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"ok\":true}");}).failureHandler((req,res,e)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"账号或密码不正确\"}");}))
   .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"ok\":true}");}))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"请先登录\"}");}).accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"无权限或安全令牌已过期，请刷新重试\"}");}))
   .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
  return http.build();
 }
}
