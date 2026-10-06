package dev.poc;
import dev.poc.core.PolicyException;
import java.util.*;
import graphql.ExecutionInput;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.web.bind.annotation.*;
@RestController
public class GraphqlHttpController {
 private final GraphQlSource source;
 public GraphqlHttpController(GraphQlSource source){this.source=source;}
 public record Request(String query,String operationName,Map<String,Object> variables) {}
 @PostMapping("/graphql")
 public Map<String,Object> graphql(@RequestBody Request request) {
  if(request.query()==null || request.query().length()>8192)throw new PolicyException("QUERY_SIZE","Query is missing or exceeds 8192 characters");
  RequestState state=RequestState.CURRENT.get();
  var builder=ExecutionInput.newExecutionInput().query(request.query()).variables(request.variables()==null?Map.of():request.variables());
  if(request.operationName()!=null)builder.operationName(request.operationName());
  builder.graphQLContext(c->c.put("requestState",state));
  Map<String,Object> result=new LinkedHashMap<>(source.graphQl().execute(builder.build()).toSpecification());
  result.put("extensions",Map.of("requestId",state.id,"indexPolicy",Map.of("mode",state.mode,"plans",state.plans)));
  return result;
 }
}
