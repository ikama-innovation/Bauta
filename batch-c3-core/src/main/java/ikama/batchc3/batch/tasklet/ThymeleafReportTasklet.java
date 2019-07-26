package ikama.batchc3.batch.tasklet;

import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;

/**
 * Report tasklet based on Thymeleaf templating
 */
public abstract class ThymeleafReportTasklet extends ReportTasklet {
    @Autowired
    protected TemplateEngine templateEngine;

    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

}
