package ikama.batchc3.config;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.spring.SpringServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@PropertySource("c3_default.properties")
public class WebConfiguration implements WebMvcConfigurer {

    @Autowired
    ApplicationContext applicationContext;

    @Value("${batchc3.reportDir}")
    String reportDir;

    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Giving access to report files
        String reportURI = reportDir.startsWith("/") ? ("file://" + reportDir) : ("file:///" + reportDir);
        if (!reportURI.endsWith("/")) {
            reportURI = reportURI + "/";
        }
        log.info("Mapping /reports/** to '{}'", reportURI);
        registry.addResourceHandler("/reports/**").addResourceLocations(reportURI);
        registry.addResourceHandler("/static/images/**").addResourceLocations("classpath:/static/images/");


    }
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ServletRegistrationBean<SpringServlet> vaadinServletBean() {


        SpringServlet springServlet = new SpringServlet(applicationContext, true);
        ServletRegistrationBean<SpringServlet> bean = new ServletRegistrationBean<>(
                springServlet, "/vaadin/*");
        bean.setAsyncSupported(true);
        bean.setName("springServlet");
        return bean;
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ServletRegistrationBean<VaadinServlet> vaadinResourcesServletBean() {
        VaadinServlet vaadinServlet = new VaadinServlet();
        ServletRegistrationBean<VaadinServlet> bean = new ServletRegistrationBean<>(
                vaadinServlet, "/frontend/*");
        bean.setName("frontendServlet");
        return bean;
    }
/*
    @Bean
    public ServletRegistrationBean frontendServletBean() {
        ServletRegistrationBean bean = new ServletRegistrationBean<>(new VaadinServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                if (!serveStaticOrWebJarRequest(req, resp)) {
                    resp.sendError(405);
                }
            }
        }, "/frontend/*");
        bean.setLoadOnStartup(1);
        return bean;
    }
    */


}
