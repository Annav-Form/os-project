import java.util.Scanner;
import java.io.File;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = scanner.nextLine();

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
                continue;
            }

            if (command.startsWith("cd ")) {
                String path = command.substring(3);

                File target = new File(path);

                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target.getCanonicalFile();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }

                continue;
            }

            if (command.startsWith("echo ")) {
                System.out.println(command.substring(5));
                continue;
            }

            if (command.startsWith("type ")) {
                String cmd = command.substring(5);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type") ||
                    cmd.equals("pwd") ||
                    cmd.equals("cd")) {

                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String path : paths) {
                    File file = new File(path, cmd);

                    if (file.exists() && file.isFile() && file.canExecute()) {
                        System.out.println(cmd + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(cmd + ": not found");
                }

                continue;
            }

            String[] parts = command.split(" ");
            String cmd = parts[0];

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
                ProcessBuilder pb = new ProcessBuilder(Arrays.asList(parts));
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