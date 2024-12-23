import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class PrimaryStorage {

    private Selector selector;
    private ServerSocketChannel tcpChannel;
    private boolean isRunning = true;
    private final DataStorage dataStorage = DataStorage.getInstance();
    private final List<Integer> localStoragePorts = new ArrayList<>();
    public static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PrimaryStorage() {
        try {
            LoggingUtil.logAsync(Level.INFO, "Primary Storage Server started and listening on port 5001 for both TCP and UDP");
            selector = Selector.open();
            initialize();
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE, "Error initializing Primary Storage: " + e.getMessage());
        }
    }

    public void initialize() throws IOException{
        tcpChannel = ServerSocketChannel.open();
        tcpChannel.bind(new InetSocketAddress(5001));
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void start() {
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
                        handleTcpConnection();
                    }
                    if (key.isReadable()) {
                        if (key.channel() instanceof SocketChannel) {
                            handleTcpRequest((SocketChannel) key.channel());
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException | URISyntaxException | ExecutionException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during Primary Storage operation: " + e.getMessage());
        }finally {
            closePrimaryStorage();
        }
    }
    private void handleTcpConnection() throws IOException {
        SocketChannel clientChannel = tcpChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private synchronized void handleTcpRequest(SocketChannel clientChannel) throws IOException, InterruptedException, URISyntaxException, ExecutionException {
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
        if (requestDto.url().startsWith("/primary/unregister")){
            int portNum = Integer.parseInt(requestDto.url().split("/")[3]);
            localStoragePorts.remove(Integer.valueOf(portNum));
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("unregister","successful");
            ByteBuffer responseBuffer = ByteBuffer.wrap(generateHttpResponse(jsonObject.toString()));
            clientChannel.write(responseBuffer);
            clientChannel.close();
            return;
        }
        ResponseDto responseDto = fetchRequestAndCreateResponse(requestDto);
        ByteBuffer responseBuffer = ByteBuffer.wrap(generateHttpResponse(responseDto.responseMessage()));
        if (!requestDto.method().equals("GET") && responseDto.isValid()) {
            triggerAllLocalStorage(requestDto);
        }
        clientChannel.write(responseBuffer);
        clientChannel.close();
    }

    // W3
    private void triggerAllLocalStorage(RequestDto requestDto) throws IOException, InterruptedException, URISyntaxException {

        String method = requestDto.method();
        String url = requestDto.url();
        String body = requestDto.body();
        for(Integer port : localStoragePorts){
            URI uri;
            String printUri;
            if (method.equals("POST")){
                uri = new URI("http://localhost:" + port + "/backup");
                printUri = "/backup";
            }else{
                String id = url.split("/")[2];
                uri = new URI("http://localhost:" + port + "/backup"+"/"+id);
                printUri = "/backup/"+id;
            }
            HttpRequest request;
            if (method.equals("DELETE")){
                request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .DELETE()
                        .build();
            }else{
                request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
            }
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println("["+timestamp+"] " +
                    "[PRIMARY_SERVER] "+"[ Method : "+method+ ", URL : "+printUri+", Body : "+body+" ]"+" [Tell backups to update]");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            LoggingUtil.logAsync(Level.INFO,"Server Awk Message - "+response);
        }
    }

    private ResponseDto fetchRequestAndCreateResponse(RequestDto requestDto){
        String method = requestDto.method();
        String url = requestDto.url();
        String requestBody = requestDto.body();

        JsonObject errObject = new JsonObject();
        errObject.addProperty("msg","Not exist id. Try Again");
        JsonObject successObject = new JsonObject();
        successObject.addProperty("msg","OK");
        JsonObject urlErrObject = new JsonObject();
        urlErrObject.addProperty("msg","Not valid Request header or body. Try Again!");

        if (method.equals("GET") && url.matches("/primary/\\d+")){
            int localStoragePort = Integer.parseInt(url.split("/")[2]);
            if (!localStoragePorts.contains(localStoragePort)) {
                localStoragePorts.add(localStoragePort);
            }
            return new ResponseDto(dataStorage.findAllByJsonString(),true);
        }
        if (method.equals("POST") && url.equals("/primary")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.get("title").getAsString();
            String body = jsonObject.get("body").getAsString();
            Data newData = dataStorage.save(title,body);
            return new ResponseDto(dataStorage.convertJsonStringOneObject(newData),true);
        }
        Long noteId = Long.parseLong(url.split("/")[2]);
        if (method.equals("PUT") && url.matches("/primary/\\d+")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.has("title") ? jsonObject.get("title").getAsString() : null;
            String body = jsonObject.has("body") ? jsonObject.get("body").getAsString() : null;
            if (dataStorage.updateDataWithPutMethod(noteId,title,body)){
                Optional<Data> targetData = dataStorage.findById(noteId);
                return targetData.map(data -> new ResponseDto(dataStorage.convertJsonStringOneObject(data), true))
                        .orElseGet(() -> new ResponseDto(errObject.toString(), false));
            }
            return new ResponseDto(errObject.toString(),false);
        }
        if (method.equals("PATCH") && url.matches("/primary/\\d+")){
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String title = jsonObject.has("title") ? jsonObject.get("title").getAsString() : null;
            String body = jsonObject.has("body") ? jsonObject.get("body").getAsString() : null;
            if (dataStorage.updateDataWithPatchMethod(noteId,title,body)){
                Optional<Data> targetData = dataStorage.findById(noteId);
                return targetData.map(data -> new ResponseDto(dataStorage.convertJsonStringOneObject(data), true))
                        .orElseGet(() -> new ResponseDto(errObject.toString(), false));
            }
            return new ResponseDto(errObject.toString(),false);
        }
        if (method.equals("DELETE") && url.matches("/primary/\\d+")){
            if (dataStorage.deleteById(noteId)){
                return new ResponseDto( successObject.toString(),true);
            }
            return new ResponseDto(errObject.toString(),false);
        }
        return new ResponseDto(urlErrObject.toString(),false);
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
    private byte[] generateHttpResponse(String jsonResponse){
        int contentLength = jsonResponse.getBytes(StandardCharsets.UTF_8).length;

        String httpResponse =  "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n" + jsonResponse;
        return httpResponse.getBytes(StandardCharsets.UTF_8);
    }


    public void closePrimaryStorage() {
        try {
            if (!isRunning){
                return;
            }
            isRunning = false;
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close();
            }
            String output = """
                    ====================
                    Primary Storage closed successfully.
                    ====================
                    """;
            System.out.println(output);
            LoggingUtil.logAsync(Level.INFO,"Primary Storage closed successfully.");
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing Primary Storage: " + e.getMessage());
        }
    }
}
