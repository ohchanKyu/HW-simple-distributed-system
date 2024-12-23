import util.LoggingUtil;
import util.PortUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Essential Args : ApplicationName, LocalPort");
            System.out.println("Please Input : java -cp src Main [ApplicationName] [LocalPort]");
            System.exit(0);
        }

        PortUtil portUtil = new PortUtil("API Server");
        String applicationName = args[0];
        int localPort = Integer.parseInt(args[1]);

        if (portUtil.isAvailablePort(localPort)) {
            try{
                int storagePort = portUtil.getAvailableLocalStoragePort();
                APIServer apiServer = new APIServer(applicationName, localPort, storagePort);
                Thread serverThread = new Thread(apiServer::start);
                serverThread.start();

                Scanner scanner = new Scanner(System.in);
                System.out.println("Enter 'exit' if you want to stop the server.");
                while (true) {
                    String command = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(command)) {
                        portUtil.deletePortStatus("TCP", localPort);
                        portUtil.deletePortStatus("TCP", storagePort);
                        try{
                            URI uri = new URI("http://localhost:5001/primary/unregister/"+storagePort);
                            HttpRequest removeRequest = HttpRequest.newBuilder()
                                    .uri(uri)
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build();
                            HttpClient httpClient = HttpClient.newBuilder()
                                    .version(HttpClient.Version.HTTP_1_1)
                                    .build();
                            HttpResponse<String> response = httpClient.send(removeRequest, HttpResponse.BodyHandlers.ofString());
                            System.out.println("Remove from primary Server : "+response.body());
                        }catch (URISyntaxException | IOException | InterruptedException e){
                            System.err.println("Error while closing Local Storage from primary Server :"+e.getMessage());
                        }
                        apiServer.stopLocalStorage();
                        System.exit(0);
                    }
                }
            }catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                LoggingUtil.getLogExecutor().shutdown();
            }
        } else {
            System.out.println("This port is already binding. Please use a different server port.");
            System.exit(0);
        }
    }
}
