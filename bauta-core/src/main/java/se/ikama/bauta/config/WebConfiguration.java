package se.ikama.bauta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

	/**
	 * To support the use of X-Forwarded-* headers in a reverse proxy setup
	 *
	 * @return
	 */
	@Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
