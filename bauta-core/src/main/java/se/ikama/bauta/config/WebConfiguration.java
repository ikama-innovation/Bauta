package se.ikama.bauta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.spring.SpringServlet;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

	@Autowired
	ApplicationContext applicationContext;

	@Value("${bauta.reportDir}")
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
		registry.addResourceHandler("/static/css/**").addResourceLocations("classpath:/static/css/");

	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE - 1)
	public ServletRegistrationBean<SpringServlet> vaadinServletBean() {

		SpringServlet springServlet = new SpringServlet(applicationContext, true);
		ServletRegistrationBean<SpringServlet> bean = new ServletRegistrationBean<>(springServlet, "/vaadin/*");
		bean.setAsyncSupported(true);
		bean.setName("springServlet");
		bean.addInitParameter("org.atmosphere.cpr.broadcaster.maxProcessingThreads", "10");
		bean.addInitParameter("org.atmosphere.cpr.broadcaster.maxAsyncWriteThreads", "10");

		return bean;
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	public ServletRegistrationBean<VaadinServlet> vaadinResourcesServletBean() {
		VaadinServlet vaadinServlet = new VaadinServlet();
		ServletRegistrationBean<VaadinServlet> bean = new ServletRegistrationBean<>(vaadinServlet, "/frontend/*");
		bean.setName("frontendServlet");
		bean.addInitParameter("org.atmosphere.cpr.broadcaster.maxProcessingThreads", "10");
		bean.addInitParameter("org.atmosphere.cpr.broadcaster.maxAsyncWriteThreads", "10");

		return bean;
	}

}
