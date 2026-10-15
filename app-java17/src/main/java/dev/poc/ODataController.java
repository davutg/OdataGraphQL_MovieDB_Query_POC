package dev.poc;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import com.sap.olingo.jpa.processor.core.api.*;
@RestController
public class ODataController {
 private final JPAODataSessionContextAccess context;private final ODataPolicy policy;private final EntityManagerFactory emf;private final ODataContinuation continuation;
 public ODataController(JPAODataSessionContextAccess context,ODataPolicy policy,EntityManagerFactory emf,ODataContinuation continuation){this.context=context;this.policy=policy;this.emf=emf;this.continuation=continuation;}
 @GetMapping({"/odata","/odata/","/odata/**"})
 public void get(HttpServletRequest req,HttpServletResponse res) throws Exception {
  HttpServletRequest safe=policy.validate(continuation.restore(policy.validate(req)));
  continuation.bind(safe);
  try(var em=emf.createEntityManager()) {
   var request=JPAODataRequestContext.with().setEntityManager(em).build();
   new JPAODataRequestHandler(context,request).process(safe,res);
  }
 }
}
