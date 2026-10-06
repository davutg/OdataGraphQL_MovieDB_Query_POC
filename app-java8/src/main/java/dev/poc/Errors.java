package dev.poc;
import dev.poc.core.PolicyException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(PolicyException.class) public ResponseEntity<?> policy(PolicyException e){return ResponseEntity.unprocessableEntity().body(Compat.map("error",Compat.map("code",e.code,"message",e.getMessage())));}

}
