package cn.course.community;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
/** Single-process guard for local development; production needs shared, identity-aware throttling. */
public class LoginThrottle extends OncePerRequestFilter {
 private record Bucket(long started,int count){}
 private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  if(req.getMethod().equals("POST")&&req.getServletPath().equals("/api/auth/login")){
   long now=System.currentTimeMillis();String ip=req.getRemoteAddr();
   if(buckets.size()>10000)buckets.entrySet().removeIf(e->now-e.getValue().started()>60000);
   Bucket b=buckets.compute(ip,(k,v)->v==null||now-v.started()>60000?new Bucket(now,1):new Bucket(v.started(),v.count()+1));
   if(b.count()>12){res.setStatus(429);res.setHeader("Retry-After","60");res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"登录尝试过于频繁，请一分钟后重试\"}");return;}
  }
  chain.doFilter(req,res);
 }
}
