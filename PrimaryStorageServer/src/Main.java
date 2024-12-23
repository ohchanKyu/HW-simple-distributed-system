import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        try {
            PrimaryStorage primaryStorage = new PrimaryStorage();
            Thread storageThread = new Thread(primaryStorage::start);
            storageThread.start();
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter 'exit' if you want to stop the server.");
            while (true) {
                String command = scanner.nextLine();
                if ("exit".equalsIgnoreCase(command)) {
                    primaryStorage.closePrimaryStorage();
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            LoggingUtil.getLogExecutor().shutdown();
        }
    }
}