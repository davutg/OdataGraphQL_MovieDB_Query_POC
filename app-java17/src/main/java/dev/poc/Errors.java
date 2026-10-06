package dev.poc;
import dev.poc.core.PolicyException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(PolicyException.class) public ResponseEntity<?> policy(PolicyException e){return ResponseEntity.unprocessableEntity().body(Map.of("error",Map.of("code",e.code,"message",e.getMessage())));}
 @GraphQlExceptionHandler public GraphQLError graphql(PolicyException e){return GraphqlErrorBuilder.newError().message(e.getMessage()).extensions(Map.of("code",e.code)).build();}
}
