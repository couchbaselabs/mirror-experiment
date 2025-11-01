package org.example.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundTool {

    private static final Logger log = LoggerFactory.getLogger(SoundTool.class);

    @Tool("Use this tool to speak a message to the external world.")
    public void speak(@P("Your message to the outside world") String message) throws Exception {
        log.info("Speaking: {}", message);
        App.mqtt.publish(App.output, message.getBytes(), 2, false);
    }

    @Tool("This tool runs automatically whenever you hear something from external world and sends you what you heard. It is a passive skill, you can't call it.")
    public void listen() {

    }

}
