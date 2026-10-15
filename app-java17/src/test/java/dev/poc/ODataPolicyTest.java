package dev.poc;
import dev.poc.core.PolicyException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
class ODataPolicyTest {
 final ODataPolicy policy=new ODataPolicy(TestLimits.defaults());
 MockHttpServletRequest req(){var r=new MockHttpServletRequest("GET","/odata/Movies");return r;}
 @Test void appliesDefaultsAndStableKey(){var r=req();r.addParameter("$orderby","Rating desc");var safe=policy.validate(r);assertThat(safe.getParameter("$top")).isEqualTo("100");assertThat(safe.getParameter("$orderby")).isEqualTo("Rating desc,Id asc");}
 @Test void rejectsTopOverLimit(){var r=req();r.addParameter("$top","501");assertThatThrownBy(()->policy.validate(r)).isInstanceOf(PolicyException.class).hasMessageContaining("500");}
 @Test void permitsZeroTop(){var r=req();r.addParameter("$top","0");assertThat(policy.validate(r).getParameter("$top")).isEqualTo("0");}
 @Test void rejectsNegativeOffset(){var r=req();r.addParameter("$skip","-1");assertThatThrownBy(()->policy.validate(r)).isInstanceOf(PolicyException.class);}
 @Test void rejectsDeepOffset(){var r=req();r.addParameter("$skip","10001");assertThatThrownBy(()->policy.validate(r)).isInstanceOf(PolicyException.class);}
 @Test void rejectsOrderExpression(){var r=req();r.addParameter("$orderby","Title asc");assertThatThrownBy(()->policy.validate(r)).hasMessageContaining("Order");}
 @Test void rejectsFunctionsAndNavigationFilter(){assertThatThrownBy(()->policy.filter("contains(Title,'x')")).isInstanceOf(PolicyException.class);assertThatThrownBy(()->policy.filter("Director/Name eq 'x'")).isInstanceOf(PolicyException.class);}
 @Test void permitsQuotedBooleanWords(){assertThat(policy.filter("Title eq 'and or Year' and (Year ge 2000 or Rating gt 8.5)")).isPositive();}
 @Test void permitsEscapedQuotes(){assertThat(policy.filter("Title eq 'Director''s film'")).isPositive();}
 @Test void rejectsSqlLikeInjection(){assertThatThrownBy(()->policy.filter("Title eq 'x'; DROP TABLE movies")).isInstanceOf(PolicyException.class);}
 @Test void rejectsIncompleteGrammar(){assertThatThrownBy(()->policy.filter("Year ge 2000 and")).isInstanceOf(PolicyException.class);}
 @Test void rejectsRepeatedQueryOptions(){var r=req();r.addParameter("$top","1","2");assertThatThrownBy(()->policy.validate(r)).hasMessageContaining("repeated");}
 @Test void rejectsBatchAndUnknownOptions(){var r=new MockHttpServletRequest("GET","/odata/$batch");assertThatThrownBy(()->policy.validate(r)).isInstanceOf(PolicyException.class);var q=req();q.addParameter("$apply","aggregate(Id with sum as Total)");assertThatThrownBy(()->policy.validate(q)).isInstanceOf(PolicyException.class);}
 @Test void acceptsBoundedToOneExpand(){var r=req();r.addParameter("$expand","Director($select=Id,Name)");assertThat(policy.validate(r).getParameter("$expand")).isEqualTo("Director($select=Id,Name)");}
 @Test void rejectsRecursiveExpand(){var r=req();r.addParameter("$expand","Director($expand=Movies($expand=Director))");assertThatThrownBy(()->policy.validate(r)).hasMessageContaining("depth");}
 @Test void rejectsExpandWildcard(){var r=req();r.addParameter("$expand","*");assertThatThrownBy(()->policy.validate(r)).isInstanceOf(PolicyException.class);}
 @Test void continuationCannotChangeQuery(){var r=req();r.addParameter("$skiptoken","od1.cGF5bG9hZA.c2lnbmF0dXJl");r.addParameter("$filter","Year gt 2000");assertThatThrownBy(()->policy.validate(r)).hasMessageContaining("alone");}
 @Test void filterComplexityIsBounded(){String filter=java.util.stream.IntStream.range(0,40).mapToObj(n->"Year ge 2000").collect(java.util.stream.Collectors.joining(" and "));assertThatThrownBy(()->policy.filter(filter)).isInstanceOf(PolicyException.class);}
}
