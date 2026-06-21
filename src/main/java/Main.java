System.out.print("$ ");
        String command = scanner.nextLine();

        if (command.equals("exit 0")){
            break;
        }

        System.out.println(command + ": command not found");
       }
    }