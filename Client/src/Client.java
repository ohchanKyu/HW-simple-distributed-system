import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {

    private static final String filePath = "../config.txt";
    private static SocketChannel clientTcpChannel = null;
    private static DatagramChannel clientUdpChannel = null;
    private static boolean running = true;
    private static boolean isServerConnect = false;
    private static String protocolType;
    public static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static int httpPort;

    record PortConfigType(String protocol, int port, String serverType) { }

    private static void printMenu() {
        System.out.println("=== Command Menu ===");
        System.out.println("1: Display the Protocol and Port for the Open Server.");
        System.out.println("2: Select a Protocol and Port and connect.");
        System.out.println("3: Send a request to the connected server.");
        System.out.println("4: Print the Menu again");
        System.out.println("q: Quit the program");
        System.out.println("====== Notice ======");
        System.out.println("> If you select http protocol, you can test with linux curl command!");
        System.out.println("====================");
    }

    private static void displayAllOpenPort(){
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String protocol = parts[0].trim();
                int port = Integer.parseInt(parts[1].trim());
                String serverType = parts[2].trim();
                System.out.println("[ "+serverType+" ]" + " [ Protocol : "+protocol+" ] "+"[ Port : "+port+" ]");
            }
        } catch (IOException e) {
            System.out.println("Error during config file - "+e.getMessage());
        }
    }
    private static boolean isValidOpenServer(String inputProtocol, int inputPort){
        List<PortConfigType> currentPortConfigType = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String protocol = parts[0].trim();
                int port = Integer.parseInt(parts[1].trim());
                String serverType = parts[2].trim();
                currentPortConfigType.add(new PortConfigType(protocol, port, serverType));
            }
        } catch (IOException e) {
            System.out.println("Error during config file - "+e.getMessage());
        }
        String configProtocol;
        String configServerType;
        if (inputProtocol.equals("tcp")){
            configProtocol = "TCP";
            configServerType = "TCP Server";
        }else if (inputProtocol.equals("http")){
            configProtocol = "TCP";
            configServerType = "API Server";
        }else{
            configProtocol = "UDP";
            configServerType = "UDP Server";
        }
        return currentPortConfigType.stream()
                .anyMatch(config -> config.port() == inputPort &&
                        configProtocol.equals(config.protocol()) &&
                        configServerType.equals(config.serverType()));
    }
    private static void connectOpenServer(String protocol,int port) throws IOException {
        protocolType = protocol;
        if (protocol.equals("udp")){
            clientUdpChannel = DatagramChannel.open();
            clientUdpChannel.configureBlocking(true);
            clientUdpChannel.connect(new InetSocketAddress("127.0.0.1", port));
        }else if (protocol.equals("tcp")){
            clientTcpChannel = SocketChannel.open();
            clientTcpChannel.configureBlocking(true);
            clientTcpChannel.connect(new InetSocketAddress("127.0.0.1", port));
        }else{
            httpPort = port;
        }
        isServerConnect = true;
        System.out.println("Connect Server on port "+port);
    }

    private static void printRestApi(){
        System.out.println("=== Rest Api ===");
        System.out.println("[GET] : [ /notes ] ");
        System.out.println("[GET] : [ /notes/{id} ] ");
        System.out.println("[POST]: [ /notes ] [ RequestBody -> title and body ]");
        System.out.println("[PUT]: [ /notes/{id} [ RequestBody -> title or body ] ");
        System.out.println("[PATCH]: [ /notes/{id} [ RequestBody -> title or body ] ");
        System.out.println("[DELETE]: [ /notes/{id} ");
        System.out.println("====================");
    }

    public static boolean validateRequest(String method, String path, String body) {
        if (!isValidMethod(method)) {
            System.out.println("Invalid HTTP method.");
            return false;
        }
        if (!isValidPath(method,path)) {
            System.out.println("Invalid path for method: " + method);
            return false;
        }
        if (!isValidBody(method,body)) {
            System.out.println("Invalid body for method: " + method + " on path: " + path);
            return false;
        }
        return true;
    }

    private static boolean isValidMethod(String method) {
        return method.equals("GET") || method.equals("POST") || method.equals("PUT") ||
                method.equals("PATCH") || method.equals("DELETE");
    }

    private static boolean isValidPath(String method, String path) {
        return switch (method) {
            case "GET" -> path.matches("^/notes(/\\d+)?$");
            case "POST" -> path.equals("/notes");
            case "PUT", "PATCH", "DELETE" -> path.matches("^/notes/\\d+$");
            default -> false;
        };
    }

    private static boolean isValidBody(String method, String body) {

        if (method.equals("GET") || method.equals("DELETE")) {
            return body == null || body.trim().isEmpty();
        }
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
            if (body == null || body.trim().isEmpty()) {
                System.out.println("Body is required for " + method + " requests.");
                return false;
            }
            JsonObject jsonBody;
            try {
                jsonBody = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                System.out.println("Invalid JSON body.");
                return false;
            }
            boolean hasTitle = jsonBody.has("title");
            boolean hasBody = jsonBody.has("body");
            if (method.equals("POST")) {
                return hasTitle && hasBody;
            }
            return hasTitle || hasBody;
        }
        return true;
    }
    private static void sendUdpMessage(String body) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(body.getBytes());
        clientUdpChannel.write(responseBuffer);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientUdpChannel.read(buffer);
        buffer.flip();
        String response = new String(buffer.array(), 0, bytesRead);
        buffer.clear();
        System.out.println("Server Response - " + response);
    }
    private static void sendTcpMessage(String body) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(body.getBytes());
        clientTcpChannel.write(responseBuffer);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientTcpChannel.read(buffer);
        buffer.flip();
        String response = new String(buffer.array(), 0, bytesRead);
        buffer.clear();
        System.out.println("Server Response - " + response);
    }
    private static void sendHttpMessage(String method,String path, String body) throws IOException, URISyntaxException, InterruptedException {
        URI uri = new URI("http://localhost:" + httpPort + path);
        HttpRequest httpRequest;

        if (method.equals("GET")) {
            httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } else if (method.equals("DELETE")) {
            httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .DELETE()
                    .build();
        } else {
            httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8))
                    .build();
        }

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Server Response Status - " + response.statusCode());
        System.out.println("Server Response Body - " + response.body());
    }
    private static void sendMessageToServer(String method,String path,String body){
        try {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("method",method);
            jsonObject.addProperty("path",path);
            if (body != null && !body.trim().isEmpty()) {
                JsonObject jsonBody = JsonParser.parseString(body).getAsJsonObject();
                JsonObject bodyObject = new JsonObject();
                if (jsonBody.has("title")) {
                    bodyObject.addProperty("title", jsonBody.get("title").getAsString());
                }
                if (jsonBody.has("body")) {
                    bodyObject.addProperty("body", jsonBody.get("body").getAsString());
                }
                if (!bodyObject.isEmpty()) {
                    jsonObject.add("body", bodyObject);
                }
            }
            if (protocolType.equals("tcp")){
                sendTcpMessage(jsonObject.toString());
            }else if (protocolType.equals("http")){
                sendHttpMessage(method,path,body);
            }else{
                sendUdpMessage(jsonObject.toString());
            }
        } catch (IOException | URISyntaxException | InterruptedException e) {
            System.err.println("Failed to communicate with the server: " + e.getMessage());
        }
    }
    public static void main(String[] args) throws IOException {

        Scanner scanner = new Scanner(System.in);
        printMenu();
        while (running) {
            System.out.println();
            System.out.print("Input your command: ");
            String command = scanner.nextLine();
            switch (command) {
                case "1":
                    displayAllOpenPort();
                    break;
                case "2":
                    if (isServerConnect){
                        System.out.println("Already Connection to Server");
                        break;
                    }
                    System.out.print("Enter a protocol (http, tcp, udp): ");
                    String protocol = scanner.nextLine();
                    if (!protocol.equals("http") && !protocol.equals("tcp") && !protocol.equals("udp")) {
                        System.out.println("Invalid command. Please enter a valid protocol (http, tcp, udp).");
                        break;
                    }
                    System.out.print("Enter a port : ");
                    int port = Integer.parseInt(scanner.nextLine());
                    if (isValidOpenServer(protocol,port)){
                        connectOpenServer(protocol,port);
                    }else{
                        System.out.println("This is not valid input");
                        System.out.println("Please input available protocol and port!");
                    }
                    break;
                case "3":
                    if (!isServerConnect){
                        System.out.println("Server is not connected yet. Please connect to the server first.");
                        break;
                    }
                    printRestApi();
                    System.out.print("Input the Http method ( Upper Case [GET] / [POST]... ) : ");
                    String method = scanner.nextLine().toUpperCase();
                    System.out.print("Input the path : ");
                    String path = scanner.nextLine();
                    String body = null;
                    if (!method.equals("GET") && !method.equals("DELETE")){
                        System.out.println("> Body format - { title : ''. body : '' } ");
                        System.out.print("Input the Request body : ");
                        body = scanner.nextLine();
                        if (body.length() > 500) {
                            System.out.println("The message is too long. Please enter a message within 500 characters.");
                            break;
                        }
                    }
                    boolean isValid = validateRequest(method, path, body);
                    if (isValid){
                        sendMessageToServer(method,path,body);
                    }
                    break;
                case "4":
                    printMenu();
                    break;
                case "q":
                    System.out.println("Program terminated.");
                    try{
                        if (clientTcpChannel != null && clientTcpChannel.isOpen()) {
                            clientTcpChannel.close();
                        }
                        if (clientUdpChannel != null && clientUdpChannel.isOpen()) {
                            clientUdpChannel.close();
                        }
                    }catch (IOException e){
                        System.err.println("Error close channel - "+e.getMessage());
                    }
                    running = false;
                    break;
                default:
                    System.out.println("Invalid command. Please try again.");
            }
        }
        scanner.close();
    }
}
