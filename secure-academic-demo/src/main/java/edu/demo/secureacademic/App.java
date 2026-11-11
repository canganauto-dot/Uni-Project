package edu.demo.secureacademic;

import jakarta.persistence.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpringBootApplication
public class App {
 public static void main(String[] args){SpringApplication.run(App.class,args);}
 @org.springframework.context.annotation.Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @org.springframework.context.annotation.Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.csrf(c->c.ignoringRequestMatchers("/api/**")).authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/style.css","/app.js").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll()).formLogin(f->f.disable()).httpBasic(b->{}).build();}
 @org.springframework.context.annotation.Bean org.springframework.boot.CommandLineRunner seed(Accounts accounts,PasswordEncoder encoder){return args->{if(accounts.count()==0){accounts.save(new Account("professor",encoder.encode("Professor123!"),"PROFESSOR"));accounts.save(new Account("student",encoder.encode("Student123!"),"STUDENT"));accounts.save(new Account("student2",encoder.encode("Student123!"),"STUDENT"));}};}
}

@Entity class Account {
 @Id String username; String password; String role;
 protected Account(){} Account(String u,String p,String r){username=u;password=p;role=r;}
}
@Entity class Assignment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; String title; String description; String owner;
 protected Assignment(){} Assignment(String t,String d,String o){title=t;description=d;owner=o;}
}
@Entity class Submission {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; Long assignmentId; String student; String work; Integer grade; boolean published; String signature;
 protected Submission(){} Submission(Long a,String s,String w){assignmentId=a;student=s;work=w;}
}
@Entity class AuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; String actor; String action; String detail; long at=System.currentTimeMillis();
 protected AuditEvent(){} AuditEvent(String a,String x,String d){actor=a;action=x;detail=d;}
}
interface Accounts extends JpaRepository<Account,String>{}
interface Assignments extends JpaRepository<Assignment,Long>{}
interface Submissions extends JpaRepository<Submission,Long>{List<Submission> findByStudent(String student);List<Submission> findByAssignmentId(Long id);}
interface Events extends JpaRepository<AuditEvent,Long>{}

@org.springframework.context.annotation.Configuration class SecurityConfig {
 @org.springframework.context.annotation.Bean org.springframework.security.core.userdetails.UserDetailsService users(Accounts repo){return name->{Account a=repo.findById(name).orElseThrow();return User.withUsername(a.username).password(a.password).authorities(new SimpleGrantedAuthority("ROLE_"+a.role)).build();};}
}

@RestController @RequestMapping("/api") class Api {
 final Assignments assignments;final Submissions submissions;final Events events;final Accounts accounts;final byte[] key;
 Api(Assignments a,Submissions s,Events e,Accounts c,@org.springframework.beans.factory.annotation.Value("${GRADE_HMAC_SECRET:}") String secret){assignments=a;submissions=s;events=e;accounts=c;key=secret.getBytes(StandardCharsets.UTF_8);}
 String actor(){return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();}
 boolean professor(){return accounts.findById(actor()).orElseThrow().role.equals("PROFESSOR");}
 void requireProfessor(){if(!professor())throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 void audit(String action,String detail){events.save(new AuditEvent(actor(),action,detail));}
 @GetMapping("/me") Map<String,String> me(){Account a=accounts.findById(actor()).orElseThrow();return Map.of("username",a.username,"role",a.role);}
 @GetMapping("/assignments") List<Assignment> list(){return assignments.findAll();}
 record AssignmentInput(String title,String description){}
 @PostMapping("/assignments") Assignment create(@RequestBody AssignmentInput in){requireProfessor();if(in.title()==null||in.title().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title required");Assignment a=assignments.save(new Assignment(in.title(),Objects.toString(in.description(),""),actor()));audit("assignment.created",a.id.toString());return a;}
 record WorkInput(String work){}
 @PostMapping("/assignments/{id}/submit") Submission submit(@PathVariable Long id,@RequestBody WorkInput in){if(!assignments.existsById(id)||in.work()==null||in.work().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Assignment and work required");Submission s=submissions.save(new Submission(id,actor(),in.work()));audit("submission.created",s.id.toString());return s;}
 @GetMapping("/submissions") List<Submission> listSubmissions(){return professor()?submissions.findAll():submissions.findByStudent(actor());}
 @GetMapping("/grades") List<Submission> grades(){return submissions.findByStudent(actor()).stream().filter(s->s.published).peek(s->{if(key.length<32||!java.security.MessageDigest.isEqual(sign(s).getBytes(StandardCharsets.UTF_8),Objects.toString(s.signature,"").getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Published grade integrity check failed");}).toList();}
 record GradeInput(Integer grade){}
 @PostMapping("/submissions/{id}/grade") Submission grade(@PathVariable Long id,@RequestBody GradeInput in){requireProfessor();Submission s=submissions.findById(id).orElseThrow();if(in.grade()==null||in.grade()<0||in.grade()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Grade must be 0–100");s.grade=in.grade();s.published=false;s.signature=null;audit("grade.saved",id.toString());return submissions.save(s);}
 @PostMapping("/submissions/{id}/publish") Submission publish(@PathVariable Long id){requireProfessor();if(key.length<32)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Set GRADE_HMAC_SECRET to at least 32 characters");Submission s=submissions.findById(id).orElseThrow();if(s.grade==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Grade the submission first");s.published=true;s.signature=sign(s);audit("grade.published",id.toString());return submissions.save(s);}
 String sign(Submission s){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((s.id+"|"+s.assignmentId+"|"+s.student+"|"+s.grade).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 @GetMapping("/audit") List<AuditEvent> audit(){requireProfessor();return events.findAll();}
}
