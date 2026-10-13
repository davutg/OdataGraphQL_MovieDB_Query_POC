package dev.poc.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.Predicate;
import dev.poc.core.CursorCodec;
import dev.poc.core.PolicyException;
import dev.poc.core.QueryPolicy;
import java.util.*;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.querydsl.binding.QuerydslPredicateBuilder;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.util.LinkedMultiValueMap;

/** Reusable bridge from a GraphQL connection contract to Spring Data Querydsl/JPA. */
public final class QuerydslConnection<T> {
    private final QueryRepository<T, ?> repository;
    private final Class<T> type;
    private final java.util.function.Function<String, QuerydslBindings> bindings;
    private final Map<String, String> policyFields;
    private final QueryPolicy policy;
    private final dev.poc.core.Limits limits;
    private final CursorCodec cursors;
    private final ObjectMapper json = new ObjectMapper();
    private final QuerydslPredicateBuilder predicates = new QuerydslPredicateBuilder(
            DefaultConversionService.getSharedInstance(), SimpleEntityPathResolver.INSTANCE);

    public QuerydslConnection(QueryRepository<T, ?> repository, Class<T> type, java.util.function.Function<String, QuerydslBindings> bindings,
            Map<String, String> policyFields, QueryPolicy policy, dev.poc.core.Limits limits, CursorCodec cursors) {
        this.repository = repository;
        this.type = type;
        this.bindings = bindings;
        this.policyFields = Collections.unmodifiableMap(new HashMap<String, String>(policyFields));
        this.policy = policy;
        this.limits = limits;
        this.cursors = cursors;
    }

    public Page<T> fetch(Map<String, Object> filter, String order, boolean descending, Integer first, String after) {
        int budget = limits.top(first);
        String property = order == null ? "id" : order.toLowerCase(Locale.ROOT);
        String field = property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
        policy.orderField(field);
        SortedMap<String, Object> normalized = new TreeMap<String, Object>();
        com.querydsl.core.BooleanBuilder combined = new com.querydsl.core.BooleanBuilder();
        if (filter != null) filter.forEach((key, value) -> {
            if (value == null) return;
            String allowed = policyFields.get(key);
            if (allowed == null) throw new PolicyException("FILTER_FIELD", "Unknown filter field");
            policy.filterField(allowed);
            if (value instanceof String && ((String) value).length() > 200)
                throw new PolicyException("FILTER_VALUE", "Filter string exceeds 200 characters");
            // Spring's predicate binder treats an empty string as absent. Keep filtering fail-closed.
            if (value instanceof String && ((String) value).isEmpty())
                throw new PolicyException("FILTER_VALUE", "Empty filters are not supported");
            normalized.put(key, value);
            LinkedMultiValueMap<String, Object> values = new LinkedMultiValueMap<String, Object>();
            values.add(allowed.substring(0, 1).toLowerCase(Locale.ROOT) + allowed.substring(1), value);
            try { combined.and(predicates.getPredicate(ClassTypeInformation.from(type), values, bindings.apply(key))); }
            catch (RuntimeException ex) { throw new PolicyException("FILTER_VALUE", "Invalid filter value"); }
        });
        Predicate predicate = combined;
        String fingerprint;
        try {
            fingerprint = cursors.fingerprint(json.writeValueAsString(Arrays.asList(
                    "querydsl-v1", type.getName(), normalized, property, descending)));
        } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        int offset = 0;
        if (after != null) {
            CursorCodec.Cursor cursor = cursors.decode(after, fingerprint);
            offset = limits.offset(cursor.offset());
            budget = Math.min(budget, cursor.remaining());
        }
        QueryPolicy.Page page = policy.page(budget, offset);
        Sort sort = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property);
        if (!property.equals("id")) sort = sort.and(Sort.by("id"));
        int lookahead = page.size() == 0 ? 0 : Math.min(budget, page.size() + 1);
        List<T> rows = repository.findWindow(predicate, sort, offset, lookahead);
        boolean more = rows.size() > page.size();
        if (more) rows = new ArrayList<T>(rows.subList(0, page.size()));
        Integer next = more ? page.nextOffset(rows.size()) : null;
        String cursor = next == null ? null : cursors.encode(next, page.remaining(rows.size()), fingerprint);
        return new Page<T>(rows, new PageInfo(next != null, cursor), predicate);
    }

    public int count(Page<T> page) { return Math.toIntExact(repository.count(page.predicate)); }

    public static final class Page<T> {
        private final List<T> nodes;
        private final PageInfo pageInfo;
        private final Predicate predicate;
        Page(List<T> nodes, PageInfo pageInfo, Predicate predicate) {
            this.nodes = Collections.unmodifiableList(new ArrayList<T>(nodes));
            this.pageInfo = pageInfo;
            this.predicate = predicate;
        }
        public List<T> getNodes() { return nodes; }
        public PageInfo getPageInfo() { return pageInfo; }
    }

    public static final class PageInfo {
        private final boolean hasNextPage;
        private final String endCursor;
        PageInfo(boolean hasNextPage, String endCursor) { this.hasNextPage = hasNextPage; this.endCursor = endCursor; }
        public boolean isHasNextPage() { return hasNextPage; }
        public String getEndCursor() { return endCursor; }
    }
}
