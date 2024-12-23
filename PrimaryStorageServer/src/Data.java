
public class Data {

    private final Long id;
    private String title;
    private String body;

    public Data(Long id, String title, String body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }
    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }
}
