package dev.poc;

import dev.poc.model.Movie;
import dev.poc.model.QMovie;
import dev.poc.query.QueryRepository;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;

/** The entity-specific query contract. JPA owns tables, joins and row materialization. */
public interface MovieRepository extends QueryRepository<Movie, Long>, QuerydslBinderCustomizer<QMovie> {
    @Override
    default void customize(QuerydslBindings bindings, QMovie movie) {
        bindings.excludeUnlistedProperties(true);
        bindings.including(movie.id, movie.title, movie.genre, movie.year, movie.rating);
    }
    /** Separate binding sets keep simultaneous equality/lower/upper bounds independent. */
    default QuerydslBindings bindingsFor(String input) {
        QMovie movie = QMovie.movie;
        QuerydslBindings bindings = new QuerydslBindings();
        customize(bindings, movie);
        switch (input) {
            case "yearFrom": bindings.bind(movie.year).first((path, value) -> path.goe(value)); break;
            case "yearTo": bindings.bind(movie.year).first((path, value) -> path.loe(value)); break;
            case "ratingMin": bindings.bind(movie.rating).first((path, value) -> path.goe(value)); break;
            case "ratingMax": bindings.bind(movie.rating).first((path, value) -> path.loe(value)); break;
            default: break;
        }
        return bindings;
    }
}
