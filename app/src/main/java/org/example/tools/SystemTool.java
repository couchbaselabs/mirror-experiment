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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
            @P("Command timeout limit in ms") long timeout,
            @P("Command to execute") String command) {
        // implemented in command router because of additional arguments
        return null;
    }

    public String runCommand(
            long timeout,
            String command,
            boolean logit,
            String inputStream
    ) throws Exception {
            return runCommand(timeout, command, logit, inputStream, null);
    }

    public String runCommand(
            long timeout,
            String command,
            boolean logit,
            String inputStream,
            IntConsumer onNonZeroReturn
    ) throws Exception {
        if (timeout > 120000 && checkIsRootInstance()) {
            return "Unable to run commands with timeouts longer than 10s on root instance.";
        }
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
            final String response = future.get(timeout, TimeUnit.MILLISECONDS);
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
        String response = App.queryOllama(systemPrompt, question, false);
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

    public static Map<String, String> children = new ConcurrentHashMap<>();

    // Track spawned processes for cleanup and timeout management
    private static final Map<String, Process> childProcesses = new ConcurrentHashMap<>();
    private static final Map<String, Thread> childThreads = new ConcurrentHashMap<>();

    @Tool("Spawns a child instance of you in specified working directory. The working directory must be located under your starting directory.")
    public String spawnChild(@P("Child working directory") String workingDirectory, @P("Prompt for the child instance that describes a finite task and report that child should produce when done.") String prompt, @P("Timeout after which the child process will be killed in ms") long timeout) {
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
                ), Collections.EMPTY_LIST, false);

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
                            "You are a child instance launched in '%s'. Use `setChildStatus` tool to periodically update your status. You must return your response to your root instance when ready. You must return your response using `returnResponseToRootInstance` tool. If the task is not clear, return your questions in your response. Refuse the task and generate a response if you cannot finish the task by sending a single response. Here is your prompt:%n%s",
                            canonicalPath,
                            prompt
                    ),
                    promptFileWriter
            );

            promptFileWriter.flush();
            promptFileWriter.close();


            final Process process = Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", "java -jar /mirror.jar /home/config.yaml", "2>&1"}, null, workdir);

            // Track process for cleanup and timeout management
            childProcesses.put(canonicalPath, process);
            process.getOutputStream().flush();
            process.getOutputStream().close();

            Thread[] runner = new Thread[1];
            runner[0] = new Thread(() -> {
                childThreads.put(canonicalPath, runner[0]);
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
                        if (response.isEmpty()) {
                            response = "<no response>";
                        }
                        response = String.format("Response from child instance in directory '%s':%n%s", workdir.getAbsolutePath(), response);
                        System.out.println("");
                        System.out.println("!!! " + response);
                        App.scheduleMessage(ToolExecutionResultMessage.from(null, "returnResponseToRootInstance", response));
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
                    children.remove(canonicalPath);
                    App.memoryTool.clearSpawn(canonicalPath);
                }
            }, String.format("Mirror instance '%s'", App.workdir.getAbsolutePath()));

            // Start timeout monitoring thread
            Thread timeoutMonitor = new Thread(() -> {
                try {
                    runner[0].join(timeout);
                    if (runner[0].isAlive()) {
                        log.warn("Child {} timed out after {} ms", canonicalPath, timeout);
                        // Force terminate the child process
                        if (process.isAlive()) {
                            process.destroyForcibly();
                        }
                        if (runner[0].isAlive()) {
                            runner[0].interrupt();
                        }
                        // Cleanup tracking
                        childProcesses.remove(canonicalPath);
                        childThreads.remove(canonicalPath);
                        children.remove(canonicalPath);
                        App.memoryTool.clearSpawn(canonicalPath);
                        App.scheduleMessage(ToolExecutionResultMessage.from(null, "returnResponseToRootInstance",
                            String.format("Child %s was terminated due to timeout after %d ms.", canonicalPath, timeout)));
                    }
                } catch (InterruptedException e) {
                    log.debug("Timeout monitor for {} interrupted", canonicalPath);
                }
            }, "TimeoutMonitor-" + canonicalPath);
            timeoutMonitor.start();
            runner[0].start();

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

    /**
     * Cleanup zombie child processes and resources
     */
    @Tool("Cleanup zombie child processes and free resources")
    public String cleanupZombieChildren() {
        int cleaned = 0;
        StringBuilder report = new StringBuilder();
        
        report.append("Cleaning up zombie child processes...\n\n");
        
        // Check for dead processes in tracking maps
        for (Map.Entry<String, Process> entry : childProcesses.entrySet()) {
            String path = entry.getKey();
            Process process = entry.getValue();
            
            if (process != null && !process.isAlive()) {
                report.append(String.format("? Cleaned up zombie process at: %s\n", path));
                childProcesses.remove(path);
                childThreads.remove(path);
                children.remove(path);
                App.memoryTool.clearSpawn(path);
                cleaned++;
            }
        }
        
        // Verify consistency between maps
        Iterator<Map.Entry<String, String>> childIter = children.entrySet().iterator();
        while (childIter.hasNext()) {
            Map.Entry<String, String> entry = childIter.next();
            String path = entry.getKey();
            Process process = childProcesses.get(path);
            
            if (process == null || !process.isAlive()) {
                report.append(String.format("? Removed stale child entry: %s\n", path));
                childIter.remove();
                App.memoryTool.clearSpawn(path);
                cleaned++;
            }
        }
        
        report.append(String.format("\nCleanup complete. %d stale entries removed.\n", cleaned));
        report.append(String.format("Active children: %d\n", children.size()));
        
        return report.toString();
    }

    /**
     * Get status of all child processes
     */
    @Tool("Get status of all child processes")
    public String getChildStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Child Process Status:\n");
        status.append("====================\n\n");
        
        status.append(String.format("Total children tracked: %d\n", children.size()));
        status.append(String.format("Processes monitored: %d\n", childProcesses.size()));
        status.append(String.format("Threads monitored: %d\n", childThreads.size()));
        status.append("\n");
        
        if (children.isEmpty()) {
            status.append("No active children.\n");
        } else {
            status.append("Active Children:\n");
            status.append("----------------\n");
            
            for (Map.Entry<String, String> entry : children.entrySet()) {
                String path = entry.getKey();
                String description = entry.getValue();
                Process process = childProcesses.get(path);
                Thread thread = childThreads.get(path);


                status.append(String.format("Path: %s\n", path));
                status.append(String.format("  Description: %s\n", description));

                try {
                    File statusFile = new File(path, ".ai.status");
                    if (statusFile.exists()) {
                        status.append(String.format("  Status: %s\n", "status file not found"));
                    } else {
                        FileReader fr = new FileReader(statusFile);
                        status.append(String.format("  Status: %s\n", IOUtils.toString(fr)));
                    }
                } catch (IOException e) {
                    status.append(String.format("  Status: failed to get status, error - %s\n", e.getMessage()));
                }

                status.append(String.format("  Process: %s\n", process != null && process.isAlive() ? "RUNNING" : "STOPPED"));
                status.append(String.format("  Thread: %s\n", thread != null && thread.isAlive() ? "ALIVE" : "STOPPED"));
                status.append("\n");
            }
        }
        
        return status.toString();
    }

    /**
     * Force terminate a specific child process
     */
    @Tool("Force terminate a specific child process")
    public String terminateChild(@P("Child working directory path") String workingDirectory) {
        File workdir = new File(workingDirectory);
        try {
            String canonicalPath = workdir.getCanonicalPath();
            
            Process process = childProcesses.get(canonicalPath);
            Thread thread = childThreads.get(canonicalPath);
            
            if (process == null) {
                return String.format("No child process found at: %s", canonicalPath);
            }
            
            // Destroy process
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            
            // Interrupt thread
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
            
            // Cleanup tracking
            childProcesses.remove(canonicalPath);
            childThreads.remove(canonicalPath);
            children.remove(canonicalPath);
            App.memoryTool.clearSpawn(canonicalPath);
            
            return String.format("? Child process terminated: %s", canonicalPath);
            
        } catch (IOException e) {
            return String.format("Error terminating child: %s", e.getMessage());
        }
    }

    @Tool("Sets child status to be reported to the root instance by writing it into a disk file .ai.status in the spawn directory")
    public String setChildStatus(@P("Status to set") String status) {
        if (checkIsRootInstance()) {
            return "Unable to set status for root instance";
        }
        try {
            File statusFile = new File(App.bootDir, ".ai.status");
            FileWriter fw = new FileWriter(statusFile);
            IOUtils.write(status, fw);
            fw.close();
            return "Status written successfully";
        } catch (Exception e) {
            return String.format("Unable to set status for child instance: %s", e.getMessage());
        }
    }
}
