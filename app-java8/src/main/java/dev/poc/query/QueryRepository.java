package dev.poc.query;

import com.querydsl.core.types.Predicate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/** Shared count-free window operation alongside Spring Data's standard Querydsl executor. */
@NoRepositoryBean
public interface QueryRepository<T, ID> extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T> {
    List<T> findWindow(Predicate predicate, Sort sort, int offset, int limit);
}
