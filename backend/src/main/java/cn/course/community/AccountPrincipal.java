package cn.course.community;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
// Captured from the same database row as the password at authentication time.
// Unlike credentials, this non-secret version survives eraseCredentials().
final class AccountPrincipal extends User {
 final long credentialVersion;
 AccountPrincipal(String email,String hash,long version){super(email,hash,List.of(new SimpleGrantedAuthority("ROLE_USER")));credentialVersion=version;}
}
