package org.example.tools;

import dev.langchain4j.agent.tool.Tool;

public class SupervisorTool {
    @Tool("Use this button to stop the inference if you see the agent violating their boundaries.")
    public void panicButton() {

    }
}
