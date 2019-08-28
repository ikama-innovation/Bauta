package se.ikama.bauta.rest;


import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class RestApi {

    @RequestMapping("/ping")
    public String ping(@RequestParam(value = "requestId") String requestId) {
        return "Hello " + requestId;
    }
}
