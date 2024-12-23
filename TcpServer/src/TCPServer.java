import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import util.LoggingUtil;
import util.RequestDto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TCPServer {

    private final String applicationName;
    private final int serverPort;
    private final int storagePort;
    private Process localStorageProcess;
    private Selector selector;
    private ServerSocketChannel tcpChannel;
    private boolean isRunning = true;

    public TCPServer(String applicationName, int serverPort, int storagePort) {
        this.applicationName = "App " + applicationName;
        this.serverPort = serverPort;
        this.storagePort = storagePort;
        try {
            LoggingUtil.logAsync(Level.INFO, "Application Name - "+applicationName);
            LoggingUtil.logAsync(Level.INFO, "TCP Server started and listening on port "+serverPort);
            selector = Selector.open();
            initialize();
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE, "Error initializing TCP Server: " + e.getMessage());
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
    private void handleConnection() throws IOException {
        SocketChannel clientChannel = tcpChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private synchronized void handleRequest(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            if (e.getMessage().contains("Connection reset")) {
                LoggingUtil.logAsync(Level.INFO,"Client connection closed successfully.");
                clientChannel.close();
                return;
            } else {
                throw e;
            }
        }
        if (bytesRead == -1) {
            clientChannel.close();
            LoggingUtil.logAsync(Level.INFO,"Client connection closed.");
            return;
        }
        buffer.flip();
        String request = new String(buffer.array(), 0, bytesRead);
        RequestDto requestDto = parsingTcpRequest(request);
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
        buffer.put(response.getBytes());
        buffer.flip();
        while (buffer.hasRemaining()) {
            clientChannel.write(buffer);
        }
    }
    private RequestDto parsingTcpRequest(String request){
        JsonObject jsonObject = JsonParser.parseString(request).getAsJsonObject();
        String method = jsonObject.get("method").getAsString();
        String url = jsonObject.get("path").getAsString();
        if (jsonObject.has("body") && !jsonObject.get("body").isJsonNull()) {
            JsonObject jsonBody = jsonObject.get("body").getAsJsonObject();
            return new RequestDto(method,url,jsonBody.toString());
        }else{
            return new RequestDto(method,url,null);
        }
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
        stopTcpServer();
    }
    public void stopTcpServer() {
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
                    TCP Server closed successfully.
                    ====================
                    """;
            System.out.println(output);
            LoggingUtil.logAsync(Level.INFO,"TCP Server closed successfully.");
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing TCP Server: " + e.getMessage());
        }
    }
}

