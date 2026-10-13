package dev.poc;

import dev.poc.core.CursorCodec;
import dev.poc.model.Movie;
import dev.poc.query.QuerydslConnection;
import graphql.schema.DataFetchingEnvironment;
import java.util.*;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class MovieGraphql {
    private final QuerydslConnection<Movie> movies;

    public MovieGraphql(MovieRepository repository, Limits limits, CursorCodec cursors) {
        Map<String, String> fields = new HashMap<String, String>();
        for (String name : Arrays.asList("id", "title", "genre", "year", "rating"))
            fields.put(name, name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1));
        fields.put("yearFrom", "Year"); fields.put("yearTo", "Year");
        fields.put("ratingMin", "Rating"); fields.put("ratingMax", "Rating");
        movies = new QuerydslConnection<Movie>(repository, Movie.class, repository::bindingsFor, fields,
                CoreConfig.policy(limits), limits.core(), cursors);
    }

    @QueryMapping
    public QuerydslConnection.Page<Movie> movies(DataFetchingEnvironment env) {
        return withState(env, () -> movies.fetch(env.getArgument("filter"), env.getArgument("orderBy"),
                Boolean.TRUE.equals(env.getArgument("descending")), env.getArgument("first"), env.getArgument("after")));
    }

    @SchemaMapping(typeName = "MovieConnection", field = "totalCount")
    public int totalCount(QuerydslConnection.Page<Movie> page, DataFetchingEnvironment env) {
        return withState(env, () -> movies.count(page));
    }

    private static <T> T withState(DataFetchingEnvironment env, java.util.function.Supplier<T> work) {
        RequestState old = RequestState.CURRENT.get();
        RequestState state = env.getGraphQlContext().get("requestState");
        if (state != null) RequestState.CURRENT.set(state);
        try { return work.get(); }
        finally { if (old == null) RequestState.CURRENT.remove(); else RequestState.CURRENT.set(old); }
    }
}
