public class Main {
    public static void main(String[] args) {
        String localStorageName = args[0];
        String ip = args[1];
        int localStoragePort = Integer.parseInt(args[2]);
        LocalStorage localStorage = new LocalStorage(localStorageName,ip,localStoragePort);
        Runtime.getRuntime().addShutdownHook(new Thread(localStorage::closeLocalStorage));
    }
}