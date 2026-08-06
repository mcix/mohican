package nl.bytesoflife.mohican.spring;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
public class SpringWebSecurity {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods("*")
                        .allowedOriginPatterns(
                                nl.bytesoflife.Configuration.getInstance().getAllowedOrigins()
                        );
            }

            /**
             * Give Spring MVC a real executor for async request handling.
             * <p>
             * Without this it falls back to SimpleAsyncTaskExecutor, which creates
             * an unbounded new thread per request and warns loudly that it "is not
             * suitable under load". The live view MJPEG endpoint returns a
             * StreamingResponseBody that occupies its thread for the whole life of
             * the stream, so every browser tab watching the camera pinned a thread
             * that was never pooled or capped.
             * <p>
             * Small on purpose: these are long-lived streams, not short requests,
             * and the camera can only feed a handful of viewers anyway. The queue
             * makes an excess of viewers wait rather than exhaust the machine.
             */
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setThreadNamePrefix("mvc-async-");
                executor.setCorePoolSize(4);
                executor.setMaxPoolSize(16);
                executor.setQueueCapacity(16);
                executor.setDaemon(true);
                executor.initialize();
                configurer.setTaskExecutor(executor);
            }
        };
    }

    @Value("${server.http.port}")
    private int httpPort;

    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        //factory.addErrorPages(new ErrorPage(HttpStatus.UNAUTHORIZED, "/unauthenticated"));

        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(httpPort);
        factory.addAdditionalTomcatConnectors(connector);

        return factory;
    }
}
