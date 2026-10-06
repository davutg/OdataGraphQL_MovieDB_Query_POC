package dev.poc;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import com.sap.olingo.jpa.processor.core.api.*;
@Configuration
public class JpaConfig {
 @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
  var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("dev.poc.model");
  f.setPersistenceUnitName("Movies");f.setJpaVendorAdapter(new EclipseLinkJpaVendorAdapter());
  f.setJpaPropertyMap(Map.of("eclipselink.weaving","false","eclipselink.logging.level","WARNING",
   "eclipselink.cache.shared.default","false"));return f;
 }
 @Bean JPAODataSessionContextAccess odataContext(EntityManagerFactory emf,ODataPaging pages) throws Exception {
  return JPAODataServiceContext.with().setPUnit("Movies").setEntityManagerFactory(emf)
   .setTypePackage("dev.poc.model").setRequestMappingPath("odata")
   .setPagingProvider(pages).build();
 }
}
