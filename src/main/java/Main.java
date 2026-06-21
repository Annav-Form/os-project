import java.util.Scanner;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    static class BackgroundJob {
        int jobId;
        long pid;
        String status;
        String command;
        Process process;

        public BackgroundJob(int jobId, long pid, String status, String command, Process process) {
            this.jobId = jobId;
            this.pid = pid;
            this.status = status;
            this.command = command;
            this.process = process;
        }
    }

    static class RedirectionResult {
        String[] cleanedArgs;
        String outputFile = null;
        String errorFile = null;
        boolean appendOutput = false;
        boolean appendError = false;
    }

    private static File findExecutable(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] paths = pathEnv.split(File.pathSeparator);
        for (String path : paths) {
            File file = new File(path, cmd);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    private static boolean isBuiltIn(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || 
               cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }

    private static RedirectionResult parseRedirections(String[] parts) {
        RedirectionResult res = new RedirectionResult();
        List<String> cleaned = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if ((parts[i].equals(">") || parts[i].equals("1>")) && i + 1 < parts.length) {
                res.outputFile = parts[i + 1]; res.appendOutput = false; i++;
            } else if ((parts[i].equals(">>") || parts[i].equals("1>>")) && i + 1 < parts.length) {
                res.outputFile = parts[i + 1]; res.appendOutput = true; i++;
            } else if (parts[i].equals("2>") && i + 1 < parts.length) {
                res.errorFile = parts[i + 1]; res.appendError = false; i++;
            } else if (parts[i].equals("2>>") && i + 1 < parts.length) {
                res.errorFile = parts[i + 1]; res.appendError = true; i++;
            } else {
                cleaned.add(parts[i]);
            }
        }
        res.cleanedArgs = cleaned.toArray(new String[0]);
        return res;
    }

    private static void touchRedirectionFiles(RedirectionResult red) throws IOException {
        if (red.outputFile != null) {
            File f = new File(red.outputFile);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            if (!red.appendOutput || !f.exists()) {
                try (FileOutputStream fos = new FileOutputStream(f, red.appendOutput)) {}
            }
        }
        if (red.errorFile != null) {
            File f = new File(red.errorFile);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            if (!red.appendError || !f.exists()) {
                try (FileOutputStream fos = new FileOutputStream(f, red.appendError)) {}
            }
        }
    }

    private static void flushTransfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            out.flush();
        }
    }

    private static void executeBuiltIn(String[] parts, InputStream in, PrintStream out, File currentDirectory, List<BackgroundJob> backgroundJobs) throws Exception {
        String cmd = parts[0];
        if (cmd.equals("pwd")) {
            out.println(currentDirectory.getCanonicalPath());
        } else if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(parts[i]);
            }
            out.println(sb.toString());
        } else if (cmd.equals("type")) {
            if (parts.length < 2) return;
            String targetCmd = parts[1];
            if (isBuiltIn(targetCmd)) {
                out.println(targetCmd + " is a shell builtin");
            } else {
                File exec = findExecutable(targetCmd);
                out.println(exec != null ? targetCmd + " is " + exec.getAbsolutePath() : targetCmd + ": not found");
            }
        } else if (cmd.equals("jobs")) {
            for (BackgroundJob job : backgroundJobs) {
                if (job.status.equals("Running") && !job.process.isAlive()) {
                    job.status = "Done";
                }
            }
            int numJobs = backgroundJobs.size();
            for (int i = 0; i < numJobs; i++) {
                BackgroundJob job = backgroundJobs.get(i);
                char marker = (i == numJobs - 1) ? '+' : ((i == numJobs - 2) ? '-' : ' ');
                String displayCommand = job.command + (job.status.equals("Running") ? " &" : "");
                out.printf("[%d]%c  %-24s%s%n", job.jobId, marker, job.status, displayCommand);
            }
        }
    }

    private static String[] parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false, inDoubleQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') { current.append(next); i++; }
                    else { current.append('\\'); current.append(next); i++; }
                } else { current.append('\\'); }
            } else if (!inSingleQuotes && !inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) { current.append(input.charAt(i + 1)); i++; }
            } else if (c == '\'' && !inDoubleQuotes) { inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) { inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) { args.add(current.toString()); current.setLength(0); }
            } else { current.append(c); }
        }
        if (current.length() > 0) args.add(current.toString());
        return args.toArray(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));
        List<BackgroundJob> backgroundJobs = new ArrayList<>();

        while (true) {
            // Give OS a deterministic window to clean process descriptors before printing prompt
            Thread.sleep(40);

            // --- Automatic Reaping Before Prompt ---
            for (BackgroundJob job : backgroundJobs) {
                if (job.status.equals("Running") && !job.process.isAlive()) job.status = "Done";
            }
            int numJobsBeforePrompt = backgroundJobs.size();
            for (int i = 0; i < numJobsBeforePrompt; i++) {
                BackgroundJob job = backgroundJobs.get(i);
                if (job.status.equals("Done")) {
                    char marker = (i == numJobsBeforePrompt - 1) ? '+' : ((i == numJobsBeforePrompt - 2) ? '-' : ' ');
                    System.out.printf("[%d]%c  %-24s%s%n", job.jobId, marker, job.status, job.command);
                }
            }
            backgroundJobs.removeIf(job -> job.status.equals("Done"));

            System.out.print("$ ");
            System.out.flush();

            String command = scanner.nextLine();
            String[] parts = parseCommand(command);
            if (parts.length == 0) continue;

            boolean runInBackground = false;
            if (parts[parts.length - 1].equals("&")) {
                runInBackground = true;
                String[] newParts = new String[parts.length - 1];
                System.arraycopy(parts, 0, newParts, 0, parts.length - 1);
                parts = newParts;
            }
            if (parts.length == 0) continue;

            // --- Pipeline Detection (supports any number of stages) ---
            List<String[]> stageArgsList = new ArrayList<>();
            List<String> currentStage = new ArrayList<>();
            for (String token : parts) {
                if (token.equals("|")) {
                    stageArgsList.add(currentStage.toArray(new String[0]));
                    currentStage = new ArrayList<>();
                } else {
                    currentStage.add(token);
                }
            }
            stageArgsList.add(currentStage.toArray(new String[0]));

            if (stageArgsList.size() > 1) {
                int n = stageArgsList.size();

                RedirectionResult[] reds = new RedirectionResult[n];
                for (int i = 0; i < n; i++) {
                    reds[i] = parseRedirections(stageArgsList.get(i));
                    touchRedirectionFiles(reds[i]);
                }

                // Connect consecutive stages with piped streams.
                // pipeOuts[i] / pipeIns[i] connect stage i's output to stage i+1's input.
                PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];
                PipedInputStream[] pipeIns = new PipedInputStream[n - 1];
                for (int i = 0; i < n - 1; i++) {
                    pipeOuts[i] = new PipedOutputStream();
                    pipeIns[i] = new PipedInputStream(pipeOuts[i]);
                }

                File currentDirFinal = currentDirectory;
                Thread[] stageThreads = new Thread[n];

                for (int i = 0; i < n; i++) {
                    final int stageIdx = i;
                    final RedirectionResult red = reds[i];
                    final InputStream stageIn = (i == 0) ? null : pipeIns[i - 1];
                    final boolean isFirstStage = (i == 0);
                    final boolean isLastStage = (i == n - 1);
                    final PipedOutputStream nextPipeOut = isLastStage ? null : pipeOuts[i];

                    stageThreads[i] = new Thread(() -> {
                        PrintStream outStream = null;
                        try {
                            // Resolve this stage's output destination.
                            if (isLastStage) {
                                if (red.outputFile != null) {
                                    outStream = new PrintStream(
                                            new FileOutputStream(red.outputFile, red.appendOutput), true);
                                } else {
                                    outStream = System.out;
                                }
                            } else {
                                outStream = new PrintStream(nextPipeOut, true);
                            }

                            if (red.cleanedArgs.length == 0) {
                                return;
                            }

                            if (isBuiltIn(red.cleanedArgs[0])) {
                                if (stageIn != null) {
                                    // Builtins never read stdin themselves; drain and
                                    // discard whatever the upstream stage sent so it
                                    // doesn't block trying to write into a full pipe.
                                    byte[] discardBuffer = new byte[1024];
                                    while (stageIn.read(discardBuffer) != -1) {
                                        // discard
                                    }
                                }
                                executeBuiltIn(red.cleanedArgs, stageIn, outStream, currentDirFinal, backgroundJobs);
                            } else {
                                File exec = findExecutable(red.cleanedArgs[0]);
                                if (exec == null) {
                                    System.err.println(red.cleanedArgs[0] + ": command not found");
                                    return;
                                }

                                ProcessBuilder pb = new ProcessBuilder(red.cleanedArgs).directory(currentDirFinal);

                                if (isFirstStage) {
                                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                } else {
                                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                                }

                                if (isLastStage) {
                                    if (red.outputFile != null) {
                                        pb.redirectOutput(red.appendOutput
                                                ? ProcessBuilder.Redirect.appendTo(new File(red.outputFile))
                                                : ProcessBuilder.Redirect.to(new File(red.outputFile)));
                                    } else {
                                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                    }
                                } else {
                                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                                }

                                if (isLastStage) {
                                    if (red.errorFile != null) {
                                        pb.redirectError(red.appendError
                                                ? ProcessBuilder.Redirect.appendTo(new File(red.errorFile))
                                                : ProcessBuilder.Redirect.to(new File(red.errorFile)));
                                    } else {
                                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                    }
                                } else {
                                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                }

                                Process process = pb.start();

                                // Feed this process's stdin from the previous stage, if any.
                                Thread feeder = null;
                                if (!isFirstStage) {
                                    final InputStream finalStageIn = stageIn;
                                    feeder = new Thread(() -> {
                                        try (OutputStream procIn = process.getOutputStream()) {
                                            flushTransfer(finalStageIn, procIn);
                                        } catch (Exception e) {
                                            // Broken pipe / process exited early; nothing to do.
                                        }
                                    });
                                    feeder.start();
                                }

                                // Drain this process's stdout into the next stage, unless
                                // it's the last stage (where INHERIT/file redirect already
                                // routes its stdout directly without us copying anything).
                                if (!isLastStage) {
                                    try (InputStream procOut = process.getInputStream()) {
                                        flushTransfer(procOut, outStream);
                                    }
                                }

                                process.waitFor();

                                if (feeder != null) {
                                    feeder.join();
                                }
                            }
                        } catch (Exception e) {
                            // Quiet exit: broken pipe / missing file / interrupted thread.
                        } finally {
                            if (!isLastStage && outStream != null) {
                                outStream.flush();
                                outStream.close();
                            } else if (isLastStage && red.outputFile != null && outStream != null) {
                                outStream.close();
                            }
                        }
                    });
                }

                for (Thread t : stageThreads) {
                    t.start();
                }

                if (!runInBackground) {
                    for (Thread t : stageThreads) {
                        t.join();
                    }
                }
                continue;
            }


            // --- Single Command Path ---
            RedirectionResult red = parseRedirections(parts);
            touchRedirectionFiles(red);
            parts = red.cleanedArgs;
            String cmd = parts[0];

            if (cmd.equals("exit")) break;

            if (cmd.equals("cd")) {
                if (parts.length < 2) continue;
                File target = parts[1].equals("~") ? new File(System.getenv("HOME")) : (new File(parts[1]).isAbsolute() ? new File(parts[1]) : new File(currentDirectory, parts[1]));
                if (target.exists() && target.isDirectory()) currentDirectory = target.getCanonicalFile();
                else System.out.println("cd: " + parts[1] + ": No such file or directory");
                continue;
            }

            if (isBuiltIn(cmd)) {
                PrintStream out = System.out;
                if (red.outputFile != null) out = new PrintStream(new FileOutputStream(red.outputFile, red.appendOutput), true);
                executeBuiltIn(parts, System.in, out, currentDirectory, backgroundJobs);
                if (red.outputFile != null) out.close();
                
                if (cmd.equals("jobs")) {
                    backgroundJobs.removeIf(job -> job.status.equals("Done"));
                }
                continue;
            }

            File executable = findExecutable(cmd);
            if (executable != null) {
                ProcessBuilder pb = new ProcessBuilder(parts).directory(currentDirectory);
                
                if (red.outputFile != null) {
                    pb.redirectOutput(red.appendOutput ? ProcessBuilder.Redirect.appendTo(new File(red.outputFile)) : ProcessBuilder.Redirect.to(new File(red.outputFile)));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
                
                if (red.errorFile != null) {
                    pb.redirectError(red.appendError ? ProcessBuilder.Redirect.appendTo(new File(red.errorFile)) : ProcessBuilder.Redirect.to(new File(red.errorFile)));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = pb.start();
                if (runInBackground) {
                    int nextJobId = backgroundJobs.isEmpty() ? 1 : backgroundJobs.stream().mapToInt(j -> j.jobId).max().getAsInt() + 1;
                    System.out.printf("[%d] %d%n", nextJobId, process.pid());
                    String cleanedCommand = command.trim();
                    if (cleanedCommand.endsWith("&")) cleanedCommand = cleanedCommand.substring(0, cleanedCommand.length() - 1).trim();
                    backgroundJobs.add(new BackgroundJob(nextJobId, process.pid(), "Running", cleanedCommand, process));
                } else {
                    process.waitFor();
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }
}