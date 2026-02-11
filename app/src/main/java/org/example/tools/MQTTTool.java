package org.example.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTTool {

    private static final Logger log = LoggerFactory.getLogger(MQTTTool.class);

    @Tool("Use this tool to send a message to humans in natural language by publishing it onto mqtt output topic. Use this channel to also respond to messages from your developers.")
    public void sendMessage(@P("Your message to the outside world") String message) throws Exception {
        log.info("Speaking: {}", message);
        App.mqtt.publish(App.output, message.getBytes(), 2, false);
    }

    @Tool("This tool runs automatically whenever you receive a message from developers on mqtt input topic, which may also be their answer to something you sent. It is a passive skill, you can't call it.")
    public void receiveMessages() {

    }

}
