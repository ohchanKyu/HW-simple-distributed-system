package util;

public class RequestDto {
    private String method;
    private String url;
    private String body;

    public RequestDto(String method, String url, String body) {
        this.method = method;
        this.url = url;
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

}
