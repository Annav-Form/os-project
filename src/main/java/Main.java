import java.util.Scanner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    private static String[] parseCommand(String input) {
        List<String> args = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append('\\');
                        current.append(next);
                        i++;
                    }
                } else {
                    current.append('\\');
                }
            } else if (!inSingleQuotes && !inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c)
                    && !inSingleQuotes
                    && !inDoubleQuotes) {

                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = scanner.nextLine();

            String[] parts = parseCommand(command);

            if (parts.length == 0) {
                continue;
            }

            String outputFile = null;
            List<String> cleaned = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {
                if ((parts[i].equals(">") || parts[i].equals("1>"))
                        && i + 1 < parts.length) {
                    outputFile = parts[i + 1];
                    i++;
                } else {
                    cleaned.add(parts[i]);
                }
            }

            parts = cleaned.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            String cmd = parts[0];

            if (cmd.equals("exit")) {
                break;
            }

            if (cmd.equals("pwd")) {
                String output = currentDirectory.getCanonicalPath();

                if (outputFile != null) {
                    Files.write(
                            Paths.get(outputFile),
                            (output + System.lineSeparator()).getBytes()
                    );
                } else {
                    System.out.println(output);
                }
                continue;
            }

            if (cmd.equals("cd")) {
                if (parts.length < 2) {
                    continue;
                }

                String path = parts[1];
                File target;

                if (path.equals("~")) {
                    target = new File(System.getenv("HOME"));
                } else if (new File(path).isAbsolute()) {
                    target = new File(path);
                } else {
                    target = new File(currentDirectory, path);
                }

                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target.getCanonicalFile();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }

                continue;
            }

            if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();

                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        sb.append(" ");
                    }
                    sb.append(parts[i]);
                }

                if (outputFile != null) {
                    Files.write(
                            Paths.get(outputFile),
                            (sb.toString() + System.lineSeparator()).getBytes()
                    );
                } else {
                    System.out.println(sb);
                }

                continue;
            }

            if (cmd.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String targetCmd = parts[1];
                String result;

                if (targetCmd.equals("echo") ||
                    targetCmd.equals("exit") ||
                    targetCmd.equals("type") ||
                    targetCmd.equals("pwd") ||
                    targetCmd.equals("cd")) {

                    result = targetCmd + " is a shell builtin";
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] paths = pathEnv.split(File.pathSeparator);

                    result = targetCmd + ": not found";

                    for (String path : paths) {
                        File file = new File(path, targetCmd);

                        if (file.exists() && file.isFile() && file.canExecute()) {
                            result = targetCmd + " is " + file.getAbsolutePath();
                            break;
                        }
                    }
                }

                if (outputFile != null) {
                    Files.write(
                            Paths.get(outputFile),
                            (result + System.lineSeparator()).getBytes()
                    );
                } else {
                    System.out.println(result);
                }

                continue;
            }

            String pathEnv = System.getenv("PATH");
            String[] paths = pathEnv.split(File.pathSeparator);

            File executable = null;

            for (String path : paths) {
                File file = new File(path, cmd);

                if (file.exists() && file.isFile() && file.canExecute()) {
                    executable = file;
                    break;
                }
            }

            if (executable != null) {
                List<String> commandParts = new ArrayList<>();

                boolean quotedExecutable =
                        cmd.contains(" ") ||
                        cmd.contains("\"") ||
                        cmd.contains("'") ||
                        cmd.contains("\\");

                if (quotedExecutable) {
                    commandParts.add(executable.getAbsolutePath());
                } else {
                    commandParts.add(cmd);
                }

                for (int i = 1; i < parts.length; i++) {
                    commandParts.add(parts[i]);
                }

                ProcessBuilder pb = new ProcessBuilder(commandParts);
                pb.directory(currentDirectory);

                if (outputFile != null) {
                    pb.redirectOutput(new File(outputFile));
                } else {
                    pb.inheritIO();
                }

                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                Process process = pb.start();
                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }
}