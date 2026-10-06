package dev.poc;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import graphql.analysis.*;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;
import java.util.List;
@Configuration
public class GraphqlConfig {
 @Bean GraphQlSourceBuilderCustomizer safeguards(Limits limits) {
  return builder->builder.configureRuntimeWiring(w->w.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY))
   .instrumentation(List.of(new MaxQueryDepthInstrumentation(limits.maxExpandDepth()+4),
    new MaxQueryComplexityInstrumentation(limits.maxQueryComplexity(),(env,childComplexity)->{
     long cost=1L+childComplexity;
     if(env.getFieldDefinition().getName().equals("movies")) {
      Object first=env.getArguments().get("first");int top=limits.top(first instanceof Integer i?i:null);
      cost=20L+Math.max(1,Math.min(top,limits.pageSize()))*cost;
     }
     CoreConfig.policy(limits).budget(0,cost);
     return (int)cost;
    })));
 }
}
