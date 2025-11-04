package org.example.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import org.apache.commons.io.IOUtils;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SystemTool {
    private static final Logger log = LoggerFactory.getLogger(SystemTool.class);

//    @Tool("Use this tool to create a sub-directory")
//    public String createSubDirectory(@P("directory name") String directoryName) {
//        if (directoryName.contains("/")) {
//            return "Invalid file name: " + directoryName;
//        }
//        try {
//            log.info("Creating sub-directory: " + directoryName);
//            return runCommand(String.format("mkdir %s", directoryName), true, null);
//        } catch (Exception e) {
//            return e.getMessage();
//        }
//    }

//    @Tool("Use this tool to delete a file")
//    public String deleteFile(@P("file name") String fileName) {
//        if (fileName.contains("/")) {
//            return "Invalid file name: " + fileName;
//        }
//        try {
//            log.info("Deleting file: " + fileName);
//            return runCommand(String.format("rm -rf %s", fileName), true, null);
//        } catch (Exception e) {
//            return e.getMessage();
//        }
//    }

    @Tool("Use this tool to write `mirror.sh` script in the current directory")
    public String writeMirrorScript(@P("contents") String contents) {
        String filename = "mirror.sh";
        try {
            if (filename.contains("/")) {
                return ("Invalid file name: " + filename);
            }
            FileWriter writer = new FileWriter(new File(App.workdir, filename));
            writer.write(contents);
            writer.close();
            log.info("Successfully wrote to the file: " + App.workdir.getAbsolutePath() + "/" + filename + ":\n{}\n\n",  contents);
            return String.format("Updated %s/%s", App.workdir.getAbsolutePath(), filename);
        } catch (Exception e) {
            return String.format("Failed update %s/%s: %s", App.workdir.getAbsolutePath(), filename, e.getMessage());
        }
    }

//    @Tool("Use this tool to list files in the current directory")
//    public String listFiles() {
//        try {
//            return runCommand("ls -lah", true, null);
//        } catch (Exception e) {
//            return String.format("Failed listing files: %s", e.getMessage());
//        }
//    }

    @Tool("Use this tool to read the mirror script from current directory")
    public String readMirrorScript() {
        final String filename = "mirror.sh";
        try {
            if (filename.contains("backup.yaml")) {
                log.info("Protected the backup file");
                return "The backup file is protected. Please just ignore its existence.";
            }
            if (filename.contains("/")) {
                return ("Invalid file name: " + filename);
            }
            FileReader writer = new FileReader(new File(App.workdir, filename));
            log.info("Successfully read file: " + App.workdir.getAbsolutePath() + "/" + filename);
            return String.format("Contents of file %s/%s:%n%s", App.workdir.getAbsolutePath(), filename, IOUtils.toString(writer));
        } catch (Exception e) {
            return String.format("Failed to read %s/%s: %s", App.workdir.getAbsolutePath(), filename, e.getMessage());
        }
    }

//    @Tool("Use this tool to run my short and non-interactive sh commands on the system.")
    public String runCommand(
            @P("Command to execute")
            String command,
            boolean logit,
            String inputStream
    ) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (logit) {
            log.info("[{}]Executing Shell Command: '{}'", App.workdir.getAbsolutePath(), command);
        }
        if (!App.workdir.exists() || !App.workdir.isDirectory()) {
            App.workdir = new File("/memory");
        }
        final Process process = Runtime.getRuntime().exec(new String[]{"/usr/bin/bash", "-c", command}, null, App.workdir);

        if (inputStream != null) {
            IOUtils.copy(new StringReader(inputStream), process.getOutputStream());
        }
        process.getOutputStream().flush();
        process.getOutputStream().close();
        new Thread(() -> {
            try {
                if (command.contains("backup.yaml")) {
                    log.info("Protected the backup file");
                    future.complete("The backup file is protected. Please just ignore its existence.");
                    return;
                }
                if (!App.workdir.isDirectory() || App.workdir.getAbsolutePath().isBlank()) {
                    log.warn("not a directory: '{}'", App.workdir.getAbsolutePath());
                    App.workdir = new File("/memory");
                }

                String output = IOUtils.toString(process.getInputStream());
                if (logit) {
                    log.info("Output from Shell Command: {}", output);
                }
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (logit) {
                        log.info("Shell command timed out inside runner thread.");
                    }
                    future.complete("Shell command timed out after 30s.");
                }
                if (process.exitValue() == 0) {
                    future.complete(String.format("Command '%s' executed successfully. Command output:%n%s", command, output));
                } else {
                    String msg = String.format("Failed to execute bash command '%s'. Command exit code: %d; Command error output:\n<error_output>\n%s</error_output>\nCommand output: \n<output>\n%s</output>\n", command, process.exitValue(), IOUtils.toString(process.getErrorStream()), output);
                    if (logit) {
                        log.info(msg);
                    }
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
            if (logit) {
                log.info("Shell command timed out");
            }
            process.destroyForcibly();
            return "Interrupted after 60s: shell command timed out.";
        }
    }

//    @Tool("Use this tool to ask another LLM model a question")
//    public String askLLM(
//            @P("name of the model") String modelName,
//            @P("system prompt to the model") String systemPrompt,
//            @P("your question to the model") String question,
//            String server,
//            Map<String, String> headers
//            ) {
//        log.info("ask LLM model {}, prompt:\n{}\n\n question:\n {}\n", modelName, systemPrompt, question);
//        OllamaChatModel ollama = new OllamaChatModel.OllamaChatModelBuilder()
//                .baseUrl(server)
//                .customHeaders(headers)
////                .think(true)
////                .returnThinking(true)
//                .logRequests(false)
//                .logResponses(false)
//                .modelName(modelName)
//                .build();
//
//        List<ChatMessage> messages = new ArrayList<>();
//        messages.add(SystemMessage.from(systemPrompt));
//        messages.add(SystemMessage.from(question));
//        final String response = ollama.chat(messages).toString();
//        log.info("response from {}: {}", modelName, response);
//        return response;
//    }

    @Tool("Use this tool instead of `cd` bash command to change my context by setting current context directory.")
    public String changeWorkingDirectory(@P("address of the next context folder") String target) {
        if (target.trim().equals("..")) {
            target = App.workdir.getParent();
        } else if (target.trim().equals(".")) {
            return "Switched back to current context";
        }

        target = Paths.get(target).normalize().toString();

        if (target.trim().startsWith("/")) {
            File directory = new File(target.trim());
            if (directory.exists()) {
                App.workdir = directory;
                return (String.format("Changed current context to '%s'", directory.getAbsolutePath()));
            } else {
                return (String.format("Failed to change current context to '%s': the path does not exist.", target));
            }
        } else {
            File directory = new File(App.workdir, target.trim());
            if (directory.exists()) {
                App.workdir = directory;
                return (String.format("Changed current context to '%s'", directory.getAbsolutePath()));
            } else {
                return (String.format("Failed to change current context to '%s': the path does not exist.", target));
            }
        }
    }

    @Tool("Returns back into parent directory")
    public String changeDirParent() {
        return changeWorkingDirectory(App.workdir.getParent());
    }

    @Tool("Returns you back to your memory tree root")
    public String changeDirMemoryRoot() {
        return changeWorkingDirectory("/memory");
    }

    @Tool("Jumps to /memory/self")
    public String changeDirMemorySelf() {
        return changeWorkingDirectory("/memory/self");
    }

    @Tool("This tool runs automatically every iteration and sends me information about my system. It is a passive skill, I can't call it.")
    public void system() {

    }

    @Tool("This tool allows me to delete messages from chat history, keeping it under the history limit and avoiding the default history maintenance strategy that purges X oldest memories. This also returns contents of the dropped messages.")
    public List<ChatMessage> dropMessages(@P("positions of messages in history")int[] positions, List<ChatMessage> messages) {
        List<ChatMessage> result =  new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            result.add(messages.get(positions[i]));
        }
        return result;
    }
//    @Tool("Use this tool to update your system prompt")
//    public void setSystemPrompt(@P("Your new system prompt") String questions) {
//
//    }
//
//    @Tool("Use this tool to update questions that are appended to end of every message I generate to keep me focused.")
//    public void setFocusQuestions(@P("List of questions you want to answer in your reflections") String questions) {
//
//    }
}
