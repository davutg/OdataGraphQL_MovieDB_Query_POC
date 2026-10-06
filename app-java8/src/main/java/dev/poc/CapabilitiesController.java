package dev.poc;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
public class CapabilitiesController {
 private final Limits limits;private final RequestGate gate;
 public CapabilitiesController(Limits limits,RequestGate gate){this.limits=limits;this.gate=gate;}
 @GetMapping("/api/capabilities") public Map<String,Object> capabilities(){return Compat.map("runtime",Compat.map("javaVersion",System.getProperty("java.version"),"javaVendor",System.getProperty("java.vendor")),"limits",limits,"filterFields",Compat.list("Id","Title","Year","Rating","Genre"),"orderFields",Compat.list("Id","Year","Rating","Genre"),"expand",Compat.list("Director"));}
 @GetMapping("/api/requests/{id}") public ResponseEntity<?> trace(@PathVariable String id){Map<String,Object> trace=gate.trace(id);return trace==null?ResponseEntity.notFound().build():ResponseEntity.ok(trace);}
}
