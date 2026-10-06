package dev.poc;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.*;
@Configuration
public class DataSourceConfig {
 @Bean(destroyMethod="close") DataSource dataSource(DataSourceProperties props,Limits limits,ObjectMapper json) {
  HikariDataSource ds=props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  ds.setMaximumPoolSize(limits.maxConcurrentRequests());ds.setConnectionTimeout(2000);
  ds.setConnectionInitSql("SET statement_timeout = '"+limits.dbTimeoutMs()+"ms'");
  return new GuardedDataSource(ds,limits,json);
 }
}
