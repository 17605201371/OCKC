package cn.course.community;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 统一设置响应缓存策略。
 *
 * <p>Vite 产出的文件名带内容哈希，内容一变文件名就变，可以长期强缓存；
 * 入口页与不带哈希的脚本（如 theme-init.js）必须每次协商，否则发版后仍会用到旧版本；
 * 接口与图片带身份与权限判断，一律不进缓存。
 *
 * <p>用过滤器而不是资源处理器，是因为字面路径注册资源处理器时，Spring 会把完整请求路径
 * 再拼到 location 之后，导致 /assets/x.js 被解析成 static/assets/assets/x.js。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CacheHeaderFilter extends OncePerRequestFilter {
  private static final Pattern HASHED_ASSET =
      Pattern.compile("^/assets/.+-[A-Za-z0-9_-]{8,}\\.(js|css|woff2?|ttf)$");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String uri = request.getRequestURI();
    if (uri.startsWith("/api/")) {
      response.setHeader("Cache-Control", "no-store");
    } else if (HASHED_ASSET.matcher(uri).matches()) {
      response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
    } else {
      response.setHeader("Cache-Control", "no-cache");
    }
    chain.doFilter(request, response);
  }
}
