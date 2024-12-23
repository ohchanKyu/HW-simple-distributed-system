package util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

public class HttpManager {

    public static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public static final String NOTES_URI = "/notes";
    public static final String PRIMARY_SERVER_URL = "http://localhost:5001/primary";

    public static RequestDto parsingJsonRequest(String request){
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

    public static String forwardToPrimaryServer(RequestDto requestDto,String storageName) throws URISyntaxException, IOException, InterruptedException {

        String method = requestDto.getMethod();
        String url = requestDto.getUrl();
        String body = requestDto.getBody();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        System.out.println("["+timestamp+"] " +
                "["+storageName+"] "+"[ Method : "+method+ ", URL : "+url+", Body : "+body+" ]"+" [Forward Request to primary]");
        URI uri;
        if (method.equals("POST")){
            uri = new URI(PRIMARY_SERVER_URL);
        }else{
            String id = url.split("/")[2];
            uri = new URI(PRIMARY_SERVER_URL+"/"+id);
        }
        HttpRequest forwardRequest;
        if (method.equals("DELETE")){
            forwardRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .DELETE()
                    .build();
        }else{
           forwardRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        }
        HttpResponse<String> response = httpClient.send(forwardRequest, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public static boolean isHttpRequest(String request) {
        return request.startsWith("GET") || request.startsWith("POST") || request.startsWith("PUT")
                || request.startsWith("PATCH") || request.startsWith("DELETE")
                || request.contains("HTTP/");
    }

    public static RequestDto parsingHttpRequest(String request){
        String[] requestLines = request.split("\r\n");
        String[] requestLineParts = requestLines[0].split(" ");

        String method = requestLineParts[0];
        String url = requestLineParts[1];
        String body = getRequestBody(requestLines);
        return new RequestDto(method,url,body);
    }
    private static String getRequestBody(String[] requestLines) {
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
    public static byte[] generateHttpResponse(String jsonResponse){
        int contentLength = jsonResponse.getBytes(StandardCharsets.UTF_8).length;

        String httpResponse =  "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n" + jsonResponse;
        return httpResponse.getBytes(StandardCharsets.UTF_8);
    }
}
