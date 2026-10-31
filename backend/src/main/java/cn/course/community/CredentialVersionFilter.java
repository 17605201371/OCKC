package cn.course.community;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
final class CredentialVersionFilter extends OncePerRequestFilter {
 final JdbcTemplate db;CredentialVersionFilter(JdbcTemplate db){this.db=db;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getRequestURI().substring(r.getContextPath().length());return !path.startsWith("/api/")||java.util.Set.of("/api/auth/login","/api/auth/csrf","/api/auth/logout").contains(path);}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.getPrincipal() instanceof AccountPrincipal p){
   var versions=db.queryForList("SELECT credential_version FROM app_user WHERE email=?",Long.class,p.getUsername());
   if(versions.isEmpty()||versions.getFirst()!=p.credentialVersion){
    var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
    res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");res.getWriter().write("{\"message\":\"账号安全信息已变化，请重新登录；本机草稿仍保留\"}");return;
   }
  }
  chain.doFilter(req,res);
 }
}
