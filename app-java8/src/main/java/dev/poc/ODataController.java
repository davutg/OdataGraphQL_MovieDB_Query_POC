package dev.poc;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import com.sap.olingo.jpa.processor.core.api.*;
@RestController
public class ODataController {
 private final JPAODataSessionContextAccess context;private final ODataPolicy policy;private final EntityManagerFactory emf;
 public ODataController(JPAODataSessionContextAccess context,ODataPolicy policy,EntityManagerFactory emf){this.context=context;this.policy=policy;this.emf=emf;}
 @GetMapping({"/odata","/odata/","/odata/**"})
 public void get(HttpServletRequest req,HttpServletResponse res) throws Exception {
  HttpServletRequest safe=policy.validate(req);
  javax.persistence.EntityManager em=emf.createEntityManager(); try {
   JPAODataRequestContext request=JPAODataRequestContext.with().setEntityManager(em).build();
   new JPAODataRequestHandler(context,request).process(safe,res);
  } finally {em.close();}
 }
}
