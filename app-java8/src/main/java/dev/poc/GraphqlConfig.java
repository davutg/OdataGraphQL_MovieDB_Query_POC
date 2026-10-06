package dev.poc;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import graphql.analysis.*;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;
import java.util.List;
@Configuration
public class GraphqlConfig {
 @Bean org.springframework.graphql.execution.DataFetcherExceptionResolver policyErrors() {
  return new org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter() {
   @Override protected graphql.GraphQLError resolveToSingleError(Throwable e,graphql.schema.DataFetchingEnvironment env) {
    if(!(e instanceof dev.poc.core.PolicyException))return null;
    dev.poc.core.PolicyException p=(dev.poc.core.PolicyException)e;
    return graphql.GraphqlErrorBuilder.newError(env).message(p.getMessage()).extensions(Compat.map("code",p.code)).build();
   }
  };
 }
 @Bean GraphQlSourceBuilderCustomizer safeguards(Limits limits) {
  return builder->builder.configureRuntimeWiring(w->w.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY))
   .instrumentation(Compat.list(new MaxQueryDepthInstrumentation(limits.maxExpandDepth()+4),
    new MaxQueryComplexityInstrumentation(limits.maxQueryComplexity(),(env,childComplexity)->{
     long cost=1L+childComplexity;
     if(env.getFieldDefinition().getName().equals("movies")) {
      Object first=env.getArguments().get("first");int top=limits.top(first instanceof Integer?(Integer)first:null);
      cost=20L+Math.max(1,Math.min(top,limits.pageSize()))*cost;
     }
     CoreConfig.policy(limits).budget(0,cost);
     return (int)cost;
    })));
 }
}
