import java.util.Scanner;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

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

            String cmd = parts[0];

            if (cmd.equals("exit")) {
                break;
            }

            if (cmd.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
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
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        System.out.print(" ");
                    }
                    System.out.print(parts[i]);
                }
                System.out.println();
                continue;
            }

            if (cmd.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String targetCmd = parts[1];

                if (targetCmd.equals("echo") ||
                    targetCmd.equals("exit") ||
                    targetCmd.equals("type") ||
                    targetCmd.equals("pwd") ||
                    targetCmd.equals("cd")) {

                    System.out.println(targetCmd + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String path : paths) {
                    File file = new File(path, targetCmd);

                    if (file.exists() && file.isFile() && file.canExecute()) {
                        System.out.println(targetCmd + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(targetCmd + ": not found");
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
                pb.inheritIO();

                Process process = pb.start();
                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }
}