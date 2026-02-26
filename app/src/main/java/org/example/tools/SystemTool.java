package org.example.tools;

import com.google.common.io.LineReader;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import org.apache.commons.io.IOUtils;
import org.example.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class SystemTool {
    private static final Logger log = LoggerFactory.getLogger(SystemTool.class);

    public SystemTool() {
        try {
            children.put(App.bootDir.getCanonicalPath(), "(current instance)");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

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
            App.workdir = new File("/home");
        }
        final Process process = Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", command}, null, App.workdir);

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
                    App.workdir = new File("/home");
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
        File backupFile = new File("/home/backup.yaml");
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

//    @Tool("Use this tool to ask your LLM model a question")
    public String queryOllama(
            @P("system prompt to the model") String systemPrompt,
            @P("your question to the model") String question
            ) {
        String response = App.queryOllama(systemPrompt, question);
        log.info("ask LLM, prompt:\n{}\n\n question:\n {}\nresponse:{}", systemPrompt, question, response);
        return response;
    }
    @Tool("Use this tool to get your current directory")
    public String getWorkingDirectory() {
        return String.format("Working directory: %s", App.workdir.getAbsolutePath());
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

    public static Map<String, String> children = new HashMap<>();

    @Tool("Spawns a child instance of you in specified working directory. The working directory must be located under your starting directory.")
    public String spawnChild(@P("Child working directory") String workingDirectory, @P("Prompt for the child instance that describes a finite task and report that child should produce when done.") String prompt) {
        if (!checkIsRootInstance()) {
            return "Unable to spawn children: not the root instance.";
        }

        final File workdir = new File(workingDirectory);
        try {
            String canonicalPath = workdir.getCanonicalPath();
            if (!canonicalPath.startsWith(App.bootDir.getAbsolutePath())) {
                return String.format("Unable to spawn a child. Working directory '%s' is outside of your spawn directory '%s'", canonicalPath, App.bootDir.getAbsolutePath());
            }
            if (!workdir.exists()) {
                return "Unable to spawn a child. Working directory does not exist.";
            }
            if (children.containsKey(canonicalPath)) {
                if (canonicalPath.equals(App.bootDir.getCanonicalPath())) {
                    return "Unable to spawn children directly in your own starting directory";
                }
                return String.format("Failed to spawn a child in '%s': there is already an instance running in this directory.", workdir.getAbsolutePath());
            } else {
                List<ChatMessage> summs = App.queryOllama(Arrays.asList(
                        SystemMessage.from("You are a helpful summarizer that summarizes messagees into short one-sentence descriptions"),
                        UserMessage.from(String.format("Summarize this into several words:%n%s", prompt))
                ), Collections.EMPTY_LIST);

                String summarized = summs
                        .stream()
                        .filter(AiMessage.class::isInstance)
                        .map(AiMessage.class::cast)
                        .reduce((l, r) -> r)
                        .map(AiMessage::text)
                        .orElse("(failed to summarize prompt)");

                log.info("Summarized prompt:\n{}", summarized);
                children.put(canonicalPath, summarized);
            }

            File promptFile = new File(workdir, "prompt.txt");
            FileWriter promptFileWriter = new FileWriter(promptFile);
            IOUtils.write(
                    String.format(
                            "You are a child instance launched in '%s'. You must return your response to your root instance when ready. You must return your response using `returnResponseToRootInstance` tool. If the task is not clear, return your questions in your response. Refuse the task and generate a response if you cannot finish the task by sending a single response. Here is your prompt:%n%s",
                            canonicalPath,
                            prompt
                    ),
                    promptFileWriter
            );

            promptFileWriter.flush();
            promptFileWriter.close();


            final Process process = Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", "java -jar /mirror.jar /home/config.yaml", "2>&1"}, null, workdir);
            process.getOutputStream().flush();
            process.getOutputStream().close();

            Thread runner = new Thread(() -> {
                LineReader reader = new LineReader(process.inputReader());
                try {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        System.out.println(String.format("[%s] %s", canonicalPath, line));
                    }

                    System.out.println(String.format("Returning from child: %s", canonicalPath));
                    // exit
                    File responseFile = new File(workdir, ".ai.response");
                    if (responseFile.exists()) {
                        String response = IOUtils.toString(new FileReader(responseFile));
                        if (!response.isEmpty()) {
                            response = String.format("Response from child instance in directory '%s':%n%s", workdir.getAbsolutePath(), response);
                            System.out.println("");
                            System.out.println("!!! " + response);
                            App.scheduleMessage(ToolExecutionResultMessage.from(null, "returnResponseToRootInstance", response));
                        }
                        responseFile.delete();
                    } else {
                        System.out.println(String.format("\nResponse from child instance in directory '%s' does not exist.", canonicalPath));
                        App.scheduleMessage(ToolExecutionResultMessage.from(null, "returnResponseToRootInstance", String.format("Child '%s' returned no response.", canonicalPath)));
                    }
                } catch (Exception e) {
                    System.out.println(String.format("[%s] %s", workdir.getPath(), e.getMessage()));
                    e.printStackTrace();
                    App.scheduleMessage(ToolExecutionResultMessage.from(null, "returnResponseToRootInstance", String.format("Failed to handle child '%s': %s", canonicalPath, e.getMessage())));
                } finally {
                    children.remove(workdir.getAbsolutePath());
                }
            }, String.format("Mirror instance '%s'", App.workdir.getAbsolutePath()));
            runner.start();

            return String.format("Launched child instance '%s'. You will receive its report automatically when it finishes.", workdir.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return String.format("Failed to spawn child instance '%s': %s", workdir.getAbsolutePath(), e.getMessage());
        }
    }

    @Tool("Checks if you are the root instance")
    public boolean checkIsRootInstance() {
        return App.bootDir.getAbsolutePath().equals("/home");
    }

    @Tool("Use this tool to get list of all your running children")
    public String listChildren() {
        if (children.isEmpty()) {
            return "-- no children --";
        }
        return children.keySet().stream()
                .map(path -> {
                        return String.format(
                                "%s => %s",
                                path,
                                children.get(path)
                        );
                })
                .collect(Collectors.joining("\n"));
    }

    @Tool("Terminates current child instance and returns answer to the root instance")
    public String returnResponseToRootInstance(@P("Your response to parent instance") String response) {
        if (checkIsRootInstance()) {
            throw new RuntimeException("Unable to return response: already running as parent instance");
        }

        try (FileWriter fw = new FileWriter(new File(App.bootDir, ".ai.response"))) {
            fw.write(response);
            return null;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to send response to parent instance '%s'", e.getMessage()));
        }
    }
}
