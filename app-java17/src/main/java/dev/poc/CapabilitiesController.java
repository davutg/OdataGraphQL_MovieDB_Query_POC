package dev.poc;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
public class CapabilitiesController {
 private final Limits limits;private final RequestGate gate;
 public CapabilitiesController(Limits limits,RequestGate gate){this.limits=limits;this.gate=gate;}
 @GetMapping("/api/capabilities") public Map<String,Object> capabilities(){return Map.of("runtime",Map.of("javaVersion",System.getProperty("java.version"),"javaVendor",System.getProperty("java.vendor")),"limits",limits,"filterFields",List.of("Id","Title","Year","Rating","Genre"),"orderFields",List.of("Id","Year","Rating","Genre"),"expand",List.of("Director"));}
 @GetMapping("/api/requests/{id}") public ResponseEntity<?> trace(@PathVariable String id){var trace=gate.trace(id);return trace==null?ResponseEntity.notFound().build():ResponseEntity.ok(trace);}
}
