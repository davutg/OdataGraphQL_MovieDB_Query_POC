package dev.poc.query;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.transaction.annotation.Transactional;

/** ORM executes bounded windows without the implicit count of Page requests. */
@Transactional(readOnly = true)
public class QueryRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID>  {
    private final EntityManager em;
    private final EntityPath<T> root;
    private final Querydsl querydsl;

    public QueryRepositoryImpl(JpaEntityInformation<T, ?> info, EntityManager em) {
        super(info, em);
        this.em = em;
        this.root = SimpleEntityPathResolver.INSTANCE.createPath(info.getJavaType());
        this.querydsl = new Querydsl(em, new PathBuilder<T>(root.getType(), root.getMetadata()));
    }

    public List<T> findWindow(Predicate predicate, Sort sort, int offset, int limit) {
        if (offset < 0 || limit < 0) throw new IllegalArgumentException("Negative window");
        if (limit == 0) return Collections.emptyList();
        return querydsl.applySorting(sort, new JPAQuery<T>(em).select(root).from(root).where(predicate))
                .offset(offset).limit(limit).fetch();
    }
}
