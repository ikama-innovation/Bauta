package se.ikama.bauta.rest;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.ikama.bauta.scheduling.JobTrigger;
import se.ikama.bauta.scheduling.JobTriggerDao;

import java.util.List;

@RestController()
@RequestMapping("api")
public class RestApi {
    @Autowired
    JobTriggerDao triggerDao;

    Logger log = LoggerFactory.getLogger(this.getClass());

    public RestApi() {
        log.info("Rest API init");
    }

    @RequestMapping("ping")
    public String ping() {
        return "ping";
    }

    @RequestMapping("triggers")
    public String triggers() {

        List<JobTrigger> triggers= triggerDao.loadTriggers();
        String jobNamesStr = StringUtils.join(triggers);

        return jobNamesStr;
    }

}
