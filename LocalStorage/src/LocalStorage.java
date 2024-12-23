import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import util.Data;
import util.DataUtil;
import util.RequestDto;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static util.HttpManager.*;

public class LocalStorage {

    private Selector selector;
    private final String storageName;
    private final String ip;
    private final int port;
    private DataUtil dataUtil;
    private ServerSocketChannel tcpChannel;
    private DatagramChannel udpChannel;
    private boolean isRunning = true;
    private static final ExecutorService executor = Executors.newFixedThreadPool(10); // 스레드 풀 생성

    public LocalStorage(String storageName,String ip,int port) {
        this.storageName = storageName+" LS";
        this.ip = ip;
        this.port = port;
        try {
            System.out.println("Local Storage Server started and listening on port "+port);
            selector = Selector.open();
            initialize();
            start();
        } catch (IOException e) {
            System.err.println("Error initializing Primary Storage: " + e.getMessage());
        }
    }
    public void initialize() throws IOException{

        try{
            String response = initializeDataStorage();
            JsonArray responseArray = JsonParser.parseString(response).getAsJsonArray();
            List<Data> initialData = new ArrayList<>();
            for(JsonElement jsonObject : responseArray){
                Long id = jsonObject.getAsJsonObject().get("id").getAsLong();
                String title = jsonObject.getAsJsonObject().get("title").getAsString();
                String body = jsonObject.getAsJsonObject().get("body").getAsString();
                initialData.add(new Data(id,title,body));
            }
            dataUtil = new DataUtil(initialData);
        } catch (URISyntaxException | InterruptedException e) {
            System.out.println("Initialize Error - " + e.getMessage());
        }
        tcpChannel = ServerSocketChannel.open();
        tcpChannel.bind(new InetSocketAddress(ip,port));
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_ACCEPT);

        udpChannel = DatagramChannel.open();
        udpChannel.bind(new InetSocketAddress(ip,port));
        udpChannel.configureBlocking(false);
        udpChannel.register(selector, SelectionKey.OP_READ);

    }

    private void start() {
        try {
            isRunning = true;
            while (isRunning) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isAcceptable()) {
                        handleTcpConnection();
                    }
                    if (key.isReadable()) {
                        if (key.channel() instanceof DatagramChannel) {
                            handleUdpRequest((DatagramChannel) key.channel());
                        } else if (key.channel() instanceof SocketChannel) {
                            handleTcpRequest((SocketChannel) key.channel());
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException | InterruptedException e) {
            System.err.println("Error during TCP "+ port + "server run : " + e.getMessage());
        }
    }
    private void handleTcpConnection() throws IOException {
        SocketChannel clientChannel = tcpChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private synchronized void  handleTcpRequest(SocketChannel clientChannel) throws IOException, URISyntaxException, InterruptedException {

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        StringBuilder requestBuilder = new StringBuilder();
        String request;
        while (true) {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                return;
            }
            buffer.flip();
            requestBuilder.append(new String(buffer.array(), 0, bytesRead));
            buffer.clear();
            request = requestBuilder.toString();
            if (isHttpRequest(request)) {
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
                        break;
                    }
                }
            } else {
                break;
            }
        }
        RequestDto requestDto;
        if (isHttpRequest(request)){
            requestDto = parsingHttpRequest(request);
        }else{
            requestDto = parsingJsonRequest(request);
        }
        // W3 W4
        if (requestDto.getUrl().startsWith("/backup")){
            String awkMessage = backupProcess(requestDto);
            String printMessage = awkMessage + " " + (requestDto.getBody() != null ? requestDto.getBody() : "");
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println("["+timestamp+"] " +
                    "["+storageName+"] "+"[ Reply : "+ printMessage +"]"+" [Acknowledge update]");
            buffer.clear();
            buffer.put(generateHttpResponse(awkMessage));
            buffer.flip();
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
            clientChannel.close();
            return;
        }
        executor.submit(() -> {
            try {
                String response = generateServerResponse(requestDto);
                buffer.clear();
                assert response != null;
                buffer.put(response.getBytes());
                buffer.flip();
                // W5 출력 후 Client 에게 전달
                if (!requestDto.getMethod().equals("GET")){
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    String printMessage = requestDto.getMethod() + " " + response;
                    System.out.println("["+timestamp+"] " +
                            "["+storageName+"] "+"[ Reply : "+ printMessage +" ]"+" [Acknowledge write completed]");
                }
                while (buffer.hasRemaining()) {
                    clientChannel.write(buffer);
                }
            } catch (Exception e) {
                System.err.println("Error during forward thread - "+e.getMessage());
            }
        });
    }
    private String generateServerResponse(RequestDto requestDto) throws URISyntaxException, IOException, InterruptedException {
        String response;
        if (isValidRequest(requestDto)){
            String method = requestDto.getMethod();
            String url = requestDto.getUrl();
            if (method.equals("GET") && url.equals(NOTES_URI)){
                response = dataUtil.findAllByJsonString();
            }else if (method.equals("GET") && url.matches(NOTES_URI+"/\\d+")){
                Long id = Long.parseLong(url.split("/")[2]);
                Optional<Data> targetData = dataUtil.findById(id);
                if (targetData.isPresent()){
                    response = dataUtil.convertJsonStringOneObject(targetData.get());
                }else{
                    response = generateJsonErrorMessage("Not exist id - " + id);
                }
            }else{
                // W2
                response = forwardToPrimaryServer(requestDto,storageName);
            }
        }else{
            response = generateJsonErrorMessage("Not Valid Request");
        }
        return response;
    }
    // 자신의 요청이 아닌 다른 Local Storage Update 받는 곳 W4
    private String backupProcess(RequestDto requestDto){

        String method = requestDto.getMethod();
        String requestBody = requestDto.getBody();
        String url = requestDto.getUrl();
        JsonObject ackObject = new JsonObject();
        ackObject.addProperty("update","successful");
        if (method.equals("POST") && url.equals("/backup")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.get("title").getAsString();
            String body = jsonObject.get("body").getAsString();
            dataUtil.save(title,body);
            return ackObject.toString();
        }
        Long noteId = Long.parseLong(url.split("/")[2]);
        if (method.equals("PUT") && url.matches("/backup/\\d+")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.has("title") ? jsonObject.get("title").getAsString() : null;
            String body = jsonObject.has("body") ? jsonObject.get("body").getAsString() : null;
            dataUtil.updateDataWithPutMethod(noteId,title,body);
        }
        if (method.equals("PATCH") && url.matches("/backup/\\d+")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.has("title") ? jsonObject.get("title").getAsString() : null;
            String body = jsonObject.has("body") ? jsonObject.get("body").getAsString() : null;
            dataUtil.updateDataWithPatchMethod(noteId,title,body);
        }
        if (method.equals("DELETE") && url.matches("/backup/\\d+")){
            dataUtil.deleteById(noteId);
        }
        return ackObject.toString();
    }

    private String generateJsonErrorMessage(String errMessage){
        JsonObject errObject = new JsonObject();
        errObject.addProperty("msg",errMessage);
        return errObject.toString();
    }

    private boolean isValidRequest(RequestDto requestDto){
        String method = requestDto.getMethod();
        String url = requestDto.getUrl();
        String body = requestDto.getBody();

        // [GET]/notes
        if (method.equals("GET") && url.equals(NOTES_URI)) {
            return true;
        }
        // [GET]/notes/{id}
        if (method.equals("GET") && url.matches(NOTES_URI+"/\\d+")) {
            return true;
        }
        // [POST]/notes
        if (method.equals("POST") && url.equals(NOTES_URI)) {
            return isValidJsonBody(method,body);
        }
        // [PUT]/notes/{id}
        if (method.equals("PUT") && url.matches(NOTES_URI+"/\\d+")) {
            return body == null || isValidJsonBody(method,body);
        }

        // [PATCH]/notes/{id}
        if (method.equals("PATCH") && url.matches(NOTES_URI+"/\\d+")) {
            return isValidJsonBody(method,body);
        }
        // [DELETE]/notes/{id}
        return method.equals("DELETE") && url.matches(NOTES_URI + "/\\d+");
    }
    private boolean isValidJsonBody(String method,String body) {
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            if (method.equals("PUT") || method.equals("PATCH")){
                return jsonObject.has("title") || jsonObject.has("body");
            }
            return jsonObject.has("title") && jsonObject.has("body");
        } catch (Exception e) {
            return false;
        }
    }
    private String initializeDataStorage() throws IOException, URISyntaxException, InterruptedException {

        URI uri = new URI(PRIMARY_SERVER_URL+"/"+port);
        HttpRequest ackRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(ackRequest, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private synchronized void handleUdpRequest(DatagramChannel udpChannel) throws IOException, URISyntaxException, InterruptedException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketAddress address = udpChannel.receive(buffer);
        buffer.flip();
        String request = new String(buffer.array(), 0, buffer.limit());
        RequestDto requestDto = parsingJsonRequest(request);
        executor.submit(() -> {
            try {
                String response = generateServerResponse(requestDto);
                buffer.clear();
                assert response != null;
                buffer.put(response.getBytes());
                buffer.flip();
                // W5 출력 후 Client 에게 전달
                if (!requestDto.getMethod().equals("GET")){
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    String printMessage = requestDto.getMethod() + " " + response;
                    System.out.println("["+timestamp+"] " +
                            "["+storageName+"] "+"[ Reply : "+ printMessage +" ]"+" [Acknowledge write completed]");
                }
                while (buffer.hasRemaining()) {
                    udpChannel.send(buffer,address);
                }
            } catch (Exception e) {
                System.err.println("Error during forward thread - "+e.getMessage());
            }
        });
    }

    public void closeLocalStorage(){
        try {
            isRunning = false;
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close();
            }
            if (udpChannel != null && udpChannel.isOpen()) {
                udpChannel.close();
            }
            String output = """
                    ====================
                    Local Storage closed successfully.
                    ====================
                    """;
            System.out.println(output);
        } catch (IOException e) {
            System.err.println("Error while closing Local Storage: " + e.getMessage());
        }
    }
}
