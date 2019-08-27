/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ikama.bauta.demo;

import org.springframework.batch.item.ItemProcessor;

public class DemoProcessor implements ItemProcessor<DemoEntity, DemoEntity> {

    @Override
    public DemoEntity process(DemoEntity item) throws Exception {
        item.setMessage(item.getMessage() + "_processed");
        return item;
    }

}
