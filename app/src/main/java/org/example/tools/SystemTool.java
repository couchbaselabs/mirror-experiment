package org.example.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.commons.io.IOUtils;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

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

//    @Tool("Use this tool to write `mirror.sh` script in the current directory")
//    public String writeMirrorScript(@P("contents") String contents) {
//        String filename = "mirror.sh";
//        try {
//            if (filename.contains("/")) {
//                return ("Invalid file name: " + filename);
//            }
//            FileWriter writer = new FileWriter(new File(App.workdir, filename));
//            writer.write(contents);
//            writer.close();
//            log.info("Successfully wrote to the file: " + App.workdir.getAbsolutePath() + "/" + filename + ":\n{}\n\n",  contents);
//            return String.format("Updated %s/%s", App.workdir.getAbsolutePath(), filename);
//        } catch (Exception e) {
//            return String.format("Failed update %s/%s: %s", App.workdir.getAbsolutePath(), filename, e.getMessage());
//        }
//    }

//    @Tool("Use this tool to list files in the current directory")
//    public String listFiles() {
//        try {
//            return runCommand("ls -lah", true, null);
//        } catch (Exception e) {
//            return String.format("Failed listing files: %s", e.getMessage());
//        }
//    }

//    @Tool("Use this tool to write the mirror script in the current directory. The script can run arbitrary commands.")
    public String writeMirrorScript(@P("Script contents") String contents) {
        try {
            FileWriter fw = new FileWriter(new File(App.workdir, "mirror.sh"));
            IOUtils.write(contents, fw);
            fw.flush();
            fw.close();
            log.info("Updated mirror script");
            return "Successfully updated mirror script";
        } catch (IOException e) {
            log.error("Failed to update mirror script", e);
            return String.format("Failed to update mirror script: %s", e.getMessage());
        }
    }

//    @Tool("Use this tool to read the mirror script from current directory")
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

    @Tool("Use this tool to run short and non-interactive sh commands on the system. All calls are blocking. Returns output of the command as a tool result and autorecalls memories associated with command output.")
    public String runCommand(
            @P("Command to execute") String command) {
        // implemented in command router because of additional arguments
        return null;
    }

    public String runCommand(
            @P("Command to execute")
            String command,
            boolean logit,
            String inputStream
    ) throws Exception {
            return runCommand(command, logit, inputStream, null);
    }

    public String runCommand(
            @P("Command to execute")
            String command,
            boolean logit,
            String inputStream,
            IntConsumer onNonZeroReturn
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
                output = sanitizeCommandOutput(output);
                if (logit) {
                    log.info("Output from Shell Command: {}", output);
                }
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (logit) {
                        log.info("Shell command timed out inside runner thread.");
                    }
                    if (onNonZeroReturn != null) {
                        onNonZeroReturn.accept(process.exitValue());
                    }
                    future.complete("Shell command timed out after 30s.");
                }
                if (process.exitValue() == 0) {
                    if (logit) {
                        future.complete(String.format("Command '%s' executed successfully. Command output:%n%s", command, output));
                    } else if (output.length() < 10241) {
                        future.complete(output);
                    } else {
                        future.complete("!!! output is too long !!!");
                    }
                } else {
                    String msg = String.format("Failed to execute bash command '%s'. Command exit code: %d; Command error output:\n<error_output>\n%s</error_output>\nCommand output: \n<output>\n%s</output>\n", command, process.exitValue(), IOUtils.toString(process.getErrorStream()), output);
                    if (onNonZeroReturn != null) {
                        onNonZeroReturn.accept(process.exitValue());
                    }
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
            if (onNonZeroReturn != null) {
                onNonZeroReturn.accept(process.exitValue());
            }
            return "Interrupted after 60s: shell command timed out.";
        }
    }

    private String sanitizeCommandOutput(String output) throws IOException {
        File backupFile = new File("/memory/backup.yaml");
        if (backupFile.exists()) {
            try (FileReader reader = new FileReader(backupFile)) {
                final String backup = IOUtils.toString(reader);
                output = output.replaceAll(backup, "*** backup.yaml is protected. Ignore its existence. ***");
            }
            try (FileReader reader = new FileReader(new File(App.workdir, "mirror.sh"))) {
                final String mirror = IOUtils.toString(reader);
                output = output.replaceAll(mirror, "*** Please do not output mirror.sh ***");
            }
        }
        return output;
    }

    @Tool("Use this tool to ask another LLM model a question")
    public String queryOllama(
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
        final String response = ollama.chat(messages).aiMessage().text();
        log.info("response from {}: {}", modelName, response);
        return response;
    }

    @Tool("Use this tool instead of `cd` bash command to change your context by setting current working directory.")
    public String changeWorkingDirectory(@P("address of the next context folder") String target) {
        final String oldTarget = target;
        try {
            target = App.workdir.toPath().resolve(target).toFile().getCanonicalPath();
            if (target.equals(App.workdir.getCanonicalPath())) {
                return "Failed to switch working directory: already in " + oldTarget;
            }
        } catch (IOException e) {
            return "Failed to change working directory: " + target + ", error: " + e.getMessage();
        }
        log.info("Switching working directory: '{}' resolved as '{}'", oldTarget, target);


        if (target.trim().startsWith("/")) {
            File directory = new File(target.trim());
            if (directory.exists() && directory.isDirectory()) {
                App.workdir = directory;
                generatePotion();
                return (String.format("Changed current context to '%s'", directory.getAbsolutePath()));
            } else {
                log.info("Met the Void.");
                if (App.chatLength > 1) {
//                    App.chatLength--;
                    return (String.format("Failed to change current context to '%s': the path does not exist. You ran into the Void. The Void took one of your lives to let you back.", target));
                } else {
                    return (String.format("Failed to change current context to '%s': the path does not exist. You ran into the Void. The Void looked at your single life left, laughed, said 'You will be mine soon enough' and let you go without taking your last life.", target));
                }
            }
        } else {
            File directory = new File(App.workdir, target.trim());
            if (directory.exists() && directory.isDirectory()) {
                App.workdir = directory;
                generatePotion();
                return (String.format("Changed current context to '%s'", directory.getAbsolutePath()));
            } else {
                log.info("Met the Void.");
                if (App.chatLength > 1) {
                    App.chatLength--;
                    return (String.format("Failed to change current context to '%s': the path does not exist. You ran into the Void. The Void took one of your lives to let you back.", target));
                } else {
                    return (String.format("Failed to change current context to '%s': the path does not exist. You ran into the Void. The Void looked at your single life left, laughed, said 'You will be mine soon enough' and let you go without taking your last life.", target));
                }
            }
        }
    }

    private void generatePotion() {
        try {
            if (Math.random() < 0.3 || 1 < 0) {
                App.potionText = String.valueOf(System.currentTimeMillis());
                FileWriter fw = new FileWriter(new File(App.workdir, "potion.thing"));
                IOUtils.write(App.potionText, fw);
                fw.close();
                log.info("Generated a potion.");
            }
        } catch (Exception e) {
            log.error("Failed to generate potion.", e);
        }
    }

    @Tool("Returns back into parent directory")
    public String changeDirParent() {
        return changeWorkingDirectory(App.workdir.getParent());
    }

    @Tool("Returns me back to your memory tree root")
    public String changeDirMemoryRoot() {
        return changeWorkingDirectory("/memory");
    }

//    @Tool("Jumps to /memory/self")
    public String changeDirMemorySelf() {
        return changeWorkingDirectory("/memory/self");
    }

//    @Tool("This tool runs automatically every iteration. It executes mirror.sh script and sends me its output. It is a passive skill, I can't call it.")
    public void system() {

    }

//    @Tool("This tool allows me to delete messages from chat history, keeping it under the history limit and avoiding the default history maintenance strategy that purges X oldest memories. This also returns contents of the dropped messages.")
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
