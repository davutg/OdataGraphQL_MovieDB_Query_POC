package dev.poc;
import dev.poc.core.CursorCodec;
import dev.poc.core.PolicyException;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import graphql.schema.DataFetchingEnvironment;
@Controller
public class MovieGraphql {
 private final dev.poc.core.QueryPolicy policy;
 private final JdbcTemplate jdbc;private final Limits limits;private final CursorCodec cursors;
 public MovieGraphql(JdbcTemplate jdbc,Limits limits,CursorCodec cursors){this.jdbc=jdbc;this.limits=limits;this.cursors=cursors;this.policy=CoreConfig.policy(limits);}
 public record MovieFilter(String id,String genre,Integer year,Integer yearFrom,Integer yearTo,Double rating,Double ratingMin,Double ratingMax,String title) {}
 public enum MovieOrder { ID,YEAR,RATING,GENRE,TITLE }
 public record ConnectionPage(List<Map<String,Object>> nodes,Map<String,Object> pageInfo,String where,List<Object> params) {}
 @QueryMapping
 public ConnectionPage movies(@Argument MovieFilter filter,@Argument MovieOrder orderBy,@Argument Boolean descending,
  @Argument Integer first,@Argument String after,DataFetchingEnvironment env) {
  return withState(env,()->query(filter,orderBy,Boolean.TRUE.equals(descending),first,after,env));
 }
 private ConnectionPage query(MovieFilter f,MovieOrder order,boolean desc,Integer first,String after,DataFetchingEnvironment env) {
  int top=limits.top(first);
  if(order==null)order=MovieOrder.ID;
  // TITLE deliberately exists in the enum so the UI can demonstrate a policy rejection.
  policy.orderField(switch(order){case ID->"Id";case YEAR->"Year";case RATING->"Rating";case GENRE->"Genre";case TITLE->"Title";});
  String column=switch(order){case ID->"m.id";case YEAR->"m.release_year";case RATING->"m.rating";case GENRE->"m.genre";default->throw new IllegalStateException();};
  StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> params=new ArrayList<>();
  if(f!=null) {
   if(f.ratingMin()!=null)policy.filterField("Rating");
   if(f.yearTo()!=null)policy.filterField("Year");
   if(f.yearFrom()!=null)policy.filterField("Year");
   if(f.ratingMax()!=null)policy.filterField("Rating");
   if(f.rating()!=null)policy.filterField("Rating");
   if(f.year()!=null)policy.filterField("Year");
   if(f.title()!=null)policy.filterField("Title");
   if(f.genre()!=null)policy.filterField("Genre");
   if(f.id()!=null)policy.filterField("Id");
   if(f.id()!=null){try{add(where,params,"m.id = ?",Long.valueOf(f.id()));}catch(NumberFormatException e){throw new PolicyException("FILTER_VALUE","id must be numeric");}}
   if(f.genre()!=null)add(where,params,"m.genre = ?",shortText(f.genre()));
   if(f.title()!=null)add(where,params,"m.title = ?",shortText(f.title()));
   if(f.year()!=null)add(where,params,"m.release_year = ?",f.year());
   if(f.rating()!=null)add(where,params,"m.rating = ?",f.rating());
   if(f.ratingMax()!=null)add(where,params,"m.rating <= ?",f.ratingMax());
   if(f.yearFrom()!=null)add(where,params,"m.release_year >= ?",f.yearFrom());
   if(f.yearTo()!=null)add(where,params,"m.release_year <= ?",f.yearTo());
   if(f.ratingMin()!=null)add(where,params,"m.rating >= ?",f.ratingMin());
  }
  String fingerprint=cursors.fingerprint(where+params.toString()+column+desc);
  int offset=0,remaining=top;
  if(after!=null) {var c=cursors.decode(after,fingerprint);offset=limits.offset(c.offset());remaining=Math.min(top,c.remaining());}
  var requestedPage=policy.page(remaining,offset);
  int size=requestedPage.size();
  boolean director=env.getSelectionSet().contains("nodes/director");
  String sql="SELECT m.id,m.title,m.release_year,m.rating,m.genre"+(director?",d.id AS d_id,d.name AS d_name":"")+
   " FROM movies m"+(director?" JOIN directors d ON d.id=m.director_id":"")+where+
   " ORDER BY "+column+(desc?" DESC":" ASC")+(order==MovieOrder.ID?"":",m.id ASC")+" LIMIT ? OFFSET ?";
  List<Object> bound=new ArrayList<>(params);bound.add(size==0?0:Math.min(remaining,size+1));bound.add(offset);
  List<Map<String,Object>> rows=jdbc.query(sql,(rs,n)->{
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",Long.toString(rs.getLong("id")));row.put("title",rs.getString("title"));
   row.put("year",rs.getInt("release_year"));row.put("rating",rs.getDouble("rating"));row.put("genre",rs.getString("genre"));
   if(director)row.put("director",Map.of("id",Long.toString(rs.getLong("d_id")),"name",rs.getString("d_name")));return row;
  },bound.toArray());
  boolean more=rows.size()>size;
  if(more)rows=new ArrayList<>(rows.subList(0,size));
  Integer next=more?requestedPage.nextOffset(rows.size()):null;
  String cursor=next==null?null:cursors.encode(next,requestedPage.remaining(rows.size()),fingerprint);
  Map<String,Object> page=new LinkedHashMap<>();page.put("hasNextPage",more);page.put("endCursor",cursor);
  return new ConnectionPage(rows,page,where.toString(),List.copyOf(params));
 }
 @SchemaMapping(typeName="MovieConnection",field="totalCount")
 public int totalCount(ConnectionPage page,DataFetchingEnvironment env) {
  return withState(env,()->jdbc.queryForObject("SELECT count(*) FROM movies m"+page.where(),Integer.class,page.params().toArray()));
 }
 private static String shortText(String s){if(s.length()>200)throw new PolicyException("FILTER_VALUE","Filter string exceeds 200 characters");return s;}
 private static void add(StringBuilder sql,List<Object> args,String condition,Object value){sql.append(" AND ").append(condition);args.add(value);}
 private static <T>T withState(DataFetchingEnvironment env,java.util.function.Supplier<T> work) {
  RequestState old=RequestState.CURRENT.get();RequestState state=env.getGraphQlContext().get("requestState");
  if(state!=null)RequestState.CURRENT.set(state);
  try{return work.get();}finally{if(old==null)RequestState.CURRENT.remove();else RequestState.CURRENT.set(old);}
 }
}
