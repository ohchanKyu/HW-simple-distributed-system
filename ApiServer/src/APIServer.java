import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import util.LoggingUtil;
import util.RequestDto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class APIServer {

    private final String applicationName;
    private final int serverPort;
    private final int storagePort;
    private Process localStorageProcess;
    private Selector selector;
    private ServerSocketChannel tcpChannel;
    private boolean isRunning = true;

    public APIServer(String applicationName, int serverPort, int storagePort) {
        this.applicationName = "App " + applicationName;
        this.serverPort = serverPort;
        this.storagePort = storagePort;
        try {
            LoggingUtil.logAsync(Level.INFO, "Application Name - "+applicationName);
            LoggingUtil.logAsync(Level.INFO, "API Server started and listening on port "+serverPort);
            selector = Selector.open();
            initialize();
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE, "Error initializing API Server: " + e.getMessage());
        }
    }
    public void initialize() throws IOException{

        tcpChannel = ServerSocketChannel.open();
        tcpChannel.bind(new InetSocketAddress(serverPort));
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_ACCEPT);

    }
    public void start() {
        startLocalStorage();
        try {
            while (isRunning) {
                int readyChannels = selector.select(1000);
                if (readyChannels == 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isAcceptable()) {
                        handleConnection();
                    }
                    if (key.isReadable()) {
                        handleRequest((SocketChannel) key.channel());
                    }
                }
            }
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during Primary Storage operation: " + e.getMessage());
            isRunning = false;
        }
    }
    private void startLocalStorage() {
        try {
        
            ProcessBuilder runPb = new ProcessBuilder(
                    "java",
                    "-cp", "../LocalStorage/src:../gson-2.10.1.jar",
                    "Main",
                    applicationName,
                    "127.0.0.1",
                    String.valueOf(storagePort)
            );
            runPb.inheritIO();
            localStorageProcess = runPb.start();
            System.out.println("Local Storage started as a separate process.");
        } catch (IOException e) {
            System.err.println("Failed to start Local Storage: " + e.getMessage());
        }
    }
    public void stopLocalStorage() {
        if (localStorageProcess != null && localStorageProcess.isAlive()) {
            try {
                localStorageProcess.destroy();
                boolean terminated = localStorageProcess.waitFor(10, TimeUnit.SECONDS);
                if (terminated) {
                    System.out.println("====================\nLocal Storage process terminated successfully.");
                } else {
                    System.out.println("====================\nLocal Storage process did not terminate in time, forcing shutdown.");
                    localStorageProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("====================\nError while waiting for Local Storage process to terminate: " + e.getMessage());
            }
        }
        stopApiServer();
    }

    private void handleConnection() throws IOException {
        SocketChannel clientChannel = tcpChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private synchronized void handleRequest(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        StringBuilder requestBuilder = new StringBuilder();
        String request;
        while (true) {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                LoggingUtil.logAsync(Level.INFO, "Client connection closed.");
                return;
            }
            buffer.flip();
            requestBuilder.append(new String(buffer.array(), 0, bytesRead));
            buffer.clear();
            request = requestBuilder.toString();
            int headerEndIndex = request.indexOf("\r\n\r\n");
            if (headerEndIndex != -1) {
                String headers = request.substring(0, headerEndIndex);
                int contentLength = 0;

                for (String line : headers.split("\r\n")) {
                    if (line.startsWith("Content-Length:")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
                int totalLength = headerEndIndex + 4 + contentLength;
                if (request.length() >= totalLength) {
                    LoggingUtil.logAsync(Level.INFO, "Received Http request\n" + request);
                    break;
                }
            }
        }
        RequestDto requestDto = parsingHttpRequest(request);
        Timestamp requestTimeStamp = new Timestamp(System.currentTimeMillis());
        System.out.println("["+requestTimeStamp+"] " +
                "["+applicationName+"] " + "[ " +requestDto.method() + " ] " +
                "[ " +requestDto.url() + " ] " +
                "[ Request Body : " +requestDto.body() + " ] ");
        String response = getLocalStorageResponse(request);
        Timestamp responseTimeStamp = new Timestamp(System.currentTimeMillis());
        System.out.println("["+responseTimeStamp+"] " +
                "["+applicationName+"] " + "[ " +requestDto.method() + " ] " +
                "[ " +requestDto.url() + " ] " +
                "[ Response Body : " + response + " ] ");
        buffer.clear();
        buffer.put(generateHttpResponse(response));
        buffer.flip();
        while (buffer.hasRemaining()) {
            clientChannel.write(buffer);
        }
    }
    public RequestDto parsingHttpRequest(String request){
        String[] requestLines = request.split("\r\n");
        String[] requestLineParts = requestLines[0].split(" ");

        String method = requestLineParts[0];
        String url = requestLineParts[1];
        String body = getRequestBody(requestLines);
        return new RequestDto(method,url,body);
    }
    private String getRequestBody(String[] requestLines) {
        boolean bodyStarted = false;
        StringBuilder body = new StringBuilder();
        for (String line : requestLines) {
            if (line.trim().isEmpty()) {
                bodyStarted = true;
                continue;
            }
            if (bodyStarted) {
                body.append(line).append("\r\n");
            }
        }
        if (!body.isEmpty()) {
            try {
                JsonArray jsonArray = JsonParser.parseString(body.toString().trim()).getAsJsonArray();
                return jsonArray.toString();
            } catch (Exception e) {
                try {
                    JsonObject jsonObject = JsonParser.parseString(body.toString().trim()).getAsJsonObject();
                    return jsonObject.toString();
                } catch (Exception ex) {
                    return body.toString();
                }
            }
        }
        return null;
    }
    private byte[] generateHttpResponse(String response){
        int contentLength = response.getBytes(StandardCharsets.UTF_8).length;

        String httpResponse =  "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n" + response;
        return httpResponse.getBytes(StandardCharsets.UTF_8);
    }
    private String getLocalStorageResponse(String clientRequest) throws IOException{

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("127.0.0.1",storagePort));
        socketChannel.configureBlocking(true);
        ByteBuffer responseBuffer = ByteBuffer.wrap(clientRequest.getBytes());
        socketChannel.write(responseBuffer);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socketChannel.read(buffer);
        buffer.flip();
        String response = new String(buffer.array(), 0, bytesRead);
        buffer.clear();
        socketChannel.close();
        return response;
    }

    public void stopApiServer() {
        try {
            isRunning = false;
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close();
            }
            String output = """
                    ====================
                    API Server closed successfully.
                    ====================
                    """;
            System.out.println(output);
            LoggingUtil.logAsync(Level.INFO,"API Server closed successfully.");
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing API Server: " + e.getMessage());
        }
    }
}
