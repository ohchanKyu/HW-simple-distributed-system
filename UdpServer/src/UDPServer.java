import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import util.LoggingUtil;
import util.RequestDto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class UDPServer {

    private final String applicationName;
    private final int serverPort;
    private final int storagePort;
    private Process localStorageProcess;
    private Selector selector;
    private DatagramChannel udpChannel;
    private boolean isRunning = true;

    public UDPServer(String applicationName, int serverPort, int storagePort) {
        this.applicationName = "App " + applicationName;
        this.serverPort = serverPort;
        this.storagePort = storagePort;
        try {
            LoggingUtil.logAsync(Level.INFO, "Application Name - "+applicationName);
            LoggingUtil.logAsync(Level.INFO, "UDP Server started and listening on port "+serverPort);
            selector = Selector.open();
            initialize();
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE, "Error initializing TCP Server: " + e.getMessage());
        }
    }
    public void initialize() throws IOException{

        udpChannel = DatagramChannel.open();
        udpChannel.bind(new InetSocketAddress(serverPort));
        udpChannel.configureBlocking(false);
        udpChannel.register(selector, SelectionKey.OP_READ);

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
                    if (key.isReadable()) {
                        handleRequest((DatagramChannel) key.channel());
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

    private synchronized void handleRequest(DatagramChannel datagramChannel) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketAddress address = datagramChannel.receive(buffer);

        buffer.flip();
        String request = new String(buffer.array(), 0, buffer.limit());
        if (request.isEmpty()) {
            datagramChannel.close();
            LoggingUtil.logAsync(Level.INFO,"Client connection closed.");
            return;
        }
        RequestDto requestDto = parsingUdpRequest(request);
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
            udpChannel.send(buffer,address);
        }
    }

    private RequestDto parsingUdpRequest(String request){
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

        DatagramChannel udpChannel = DatagramChannel.open();
        InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1",storagePort);
        byte[] sendData = clientRequest.getBytes();
        ByteBuffer sendBuffer = ByteBuffer.wrap(sendData);
        udpChannel.send(sendBuffer,serverAddress);

        ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
        udpChannel.configureBlocking(true);
        udpChannel.receive(receiveBuffer);
        receiveBuffer.flip();
        String response = new String(receiveBuffer.array(), 0, receiveBuffer.limit());
        receiveBuffer.clear();
        udpChannel.close();
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
            if (udpChannel != null && udpChannel.isOpen()) {
                udpChannel.close();
            }
            String output = """
                    ====================
                    UDP Server closed successfully.
                    ====================
                    """;
            System.out.println(output);
            LoggingUtil.logAsync(Level.INFO,"UDP Server closed successfully.");
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing UDP Server: " + e.getMessage());
        }
    }
}
