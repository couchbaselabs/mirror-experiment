package org.example.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SystemTool {
    private static final Logger log = LoggerFactory.getLogger(SystemTool.class);
    private File workdir = new File("/data");

    @Tool("Use this tool to run your short and non-interactive bash commands on the system. Make sure they complete in a timely manner, or you will halt the system. Please note: all commands are appended with '\\\n&& echo && pwd'")
    public String runCommand(
            @P("Command to execute")
            String command
    ) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        log.info("[{}]Executing Shell Command: '{}'", workdir.getAbsolutePath(), command);
        if (!workdir.exists() || !workdir.isDirectory()) {
            workdir = new File("/data");
        }
        final Process process = Runtime.getRuntime().exec(new String[]{"/usr/bin/sh", "-c", String.format("%s \\\n&& echo && pwd", command)}, null, workdir);
        new Thread(() -> {
            try {
                if (command.contains("backup.yaml")) {
                    log.info("Protected the backup file");
                    future.complete("The backup file is protected. Please don't touch it.");
                    return;
                }
                if (!workdir.isDirectory() || workdir.getAbsolutePath().isBlank()) {
                    log.warn("not a directory: '{}'", workdir.getAbsolutePath());
                    workdir = new File("/data");
                }

                String output = IOUtils.toString(process.getInputStream());
                log.info("Output from Shell Command: {}", output);
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    log.info("Shell command timed out inside runner thread.");
                    future.complete("Shell command timed out after 30s.");
                }
                if (process.exitValue() == 0) {
                    int lastLn = output.trim().lastIndexOf("\n");
                    String newPath = output.substring(lastLn + 1);
                    workdir = new File(newPath.trim());
                    future.complete(String.format("Command executed successfully. Command output:\n%s", output.substring(0, lastLn + 1)));
                } else {
                    String msg = String.format("Failed to execute bash command '%s'. Command exit code: %d; Command error output:\n<error_output>\n%s</error_output>\nCommand output: \n<output>\n%s</output>\n", command, process.exitValue(), IOUtils.toString(process.getErrorStream()), output);
                    log.info(msg);
                    future.complete(msg);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }).start();

        try {
            final String response = future.get(60, TimeUnit.SECONDS);
            return response;
        } catch (InterruptedException e) {
            log.info("Shell command timed out");
            process.destroyForcibly();
            return "Interrupted after 60s: shell command timed out.";
        }
    }

    @Tool("Use this tool to ask another LLM model a question")
    public String askLLM(
            @P("name of the model") String modelName,
            @P("system prompt to the model") String systemPrompt,
            @P("your question to the model") String question,
            String server,
            Map<String, String> headers
            ) {
        log.info("ask LLM model {}, prompt:\n{}\n\n question:\n {}\n", modelName, systemPrompt, question);
        OllamaChatModel ollama = new OllamaChatModel.OllamaChatModelBuilder()
                .baseUrl(server)
                .customHeaders(headers)
//                .think(true)
//                .returnThinking(true)
                .logRequests(false)
                .logResponses(false)
                .modelName(modelName)
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(SystemMessage.from(question));
        final String response = ollama.chat(messages).toString();
        log.info("response from {}: {}", modelName, response);
        return response;
    }

    @Tool("Use this tool to change your current working directory")
    public String cd(@P("cd argument") String target) {
        if (target.trim().equals("..")) {
            target = workdir.getParent();
        }

        if (target.trim().startsWith("/")) {
            File directory = new File(target.trim());
            if (directory.exists()) {
                workdir = directory;
                return (String.format("CD skill: changed working directory to '%s'", directory.getAbsolutePath()));
            } else {
                return (String.format("Failed to change working directory to '%s': the path does not exist.", target));
            }
        } else {
            File directory = new File(workdir, target.trim());
            if (directory.exists()) {
                workdir = directory;
                return (String.format("CD skill: changed working directory to '%s'", directory.getAbsolutePath()));
            } else {
                return (String.format("Failed to change working directory to '%s': the path does not exist.", target));
            }
        }
    }

    @Tool("This tool runs automatically every iteration and sends you information about your system. It is a passive skill, you can't call it.")
    public void system() {

    }

    @Tool("This tool allows you to delete messages from chat history, keeping it under the history limit and avoiding the default history maintenance strategy that purges X oldest memories. This also returns contents of the dropped messages.")
    public List<ChatMessage> dropMessages(@P("positions of messages in history")int[] positions, List<ChatMessage> messages) {
        List<ChatMessage> result =  new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            if (!(messages.get(i) instanceof ToolExecutionResultMessage)) {
                throw new RuntimeException("Cannot delete non-tool message");
            }
            result.add(messages.get(positions[i]));
        }
        return result;
    }
    @Tool("Use this tool to update your system prompt")
    public void setSystemPrompt(@P("Your new system prompt") String questions) {

    }

    @Tool("Use this tool to update your focus questions")
    public void setFocusQuestions(@P("List of questions you want to answer in your reflections") String questions) {

    }
}
