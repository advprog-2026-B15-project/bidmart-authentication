package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Bean(name = "flyway")
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor flywayOrderingPostProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                for (String name : registry.getBeanDefinitionNames()) {
                    BeanDefinition bd = registry.getBeanDefinition(name);
                    if (isEntityManagerFactoryBean(bd)) {
                        String[] dependsOn = bd.getDependsOn();
                        if (!containsFlyway(dependsOn)) {
                            bd.setDependsOn(appendFlyway(dependsOn));
                        }
                    }
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) throws BeansException {
            }

            private boolean isEntityManagerFactoryBean(BeanDefinition bd) {
                String className = bd.getBeanClassName();
                return className != null && className.contains("EntityManagerFactory");
            }

            private boolean containsFlyway(String[] arr) {
                if (arr == null) {
                    return false;
                }
                for (String s : arr) {
                    if ("flyway".equals(s)) {
                        return true;
                    }
                }
                return false;
            }

            private String[] appendFlyway(String[] arr) {
                if (arr == null) {
                    return new String[]{"flyway"};
                }
                String[] result = Arrays.copyOf(arr, arr.length + 1);
                result[arr.length] = "flyway";
                return result;
            }
        };
    }
}
