package dev.poc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"poc.rate-per-minute=1000"})
class SharedRequestStoreIT {
 @Autowired DataSource source;
 @Autowired ObjectMapper json;
 @Test void independentInstancesShareAtomicQuotaAndReports() throws Exception {
  String namespace="test-"+UUID.randomUUID();
  SharedRequestStore first=new SharedRequestStore(source,json,namespace),second=new SharedRequestStore(source,json,namespace);
  ExecutorService workers=Executors.newFixedThreadPool(8);
  try {
   List<Future<Boolean>> attempts=new ArrayList<Future<Boolean>>();
   for(int i=0;i<20;i++){final SharedRequestStore store=i%2==0?first:second;attempts.add(workers.submit(()->store.permit("client",7)));}
   int accepted=0;for(Future<Boolean> attempt:attempts)if(attempt.get())accepted++;
   assertThat(accepted).isEqualTo(7);
   Map<String,Object> report=new LinkedHashMap<String,Object>();String id=UUID.randomUUID().toString();
   report.put("requestId",id);report.put("mode","AUDIT");report.put("plans",Collections.emptyList());first.save(report);
   assertThat(second.trace(id)).isEqualTo(report);
   assertThat(new SharedRequestStore(source,json,namespace).trace(id)).isEqualTo(report);
   assertThat(new SharedRequestStore(source,json,namespace+"-isolated").trace(id)).isNull();
  }finally{workers.shutdownNow();}
 }
}
