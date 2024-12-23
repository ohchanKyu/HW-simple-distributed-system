package util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;

public class DataUtil {

    private final List<Data> dataList;
    private Long currentId;

    public DataUtil(List<Data> dataList){
        this.dataList = dataList;
        this.currentId = (long) dataList.size();
    }
    public void save(String title, String body) {
        Data newData = new Data(++currentId, title, body);
        dataList.add(newData);
    }
    public Optional<Data> findById(Long id) {
        return dataList.stream()
                .filter(data -> data.getId().equals(id))
                .findFirst();
    }
    public void updateDataWithPutMethod(Long id, String newTitle, String newBody) {

        Optional<Data> dataOptional = findById(id);
        if (dataOptional.isPresent()) {
            Data data = dataOptional.get();
            data.setTitle(newTitle);
            data.setBody(newBody);
        }
    }
    public void updateDataWithPatchMethod(Long id, String newTitle, String newBody) {

        Optional<Data> dataOptional = findById(id);
        if (dataOptional.isPresent()) {
            Data data = dataOptional.get();
            if (newTitle != null) {
                data.setTitle(newTitle);
            }
            if (newBody != null) {
                data.setBody(newBody);
            }

        }
    }
    public void deleteById(Long id) {
       dataList.removeIf(data -> data.getId().equals(id));
    }
    public String findAllByJsonString(){
        JsonArray jsonArray = new JsonArray();
        for (Data data : this.dataList) {
            JsonObject jsonData = new JsonObject();
            jsonData.addProperty("id", data.getId());
            jsonData.addProperty("title", data.getTitle());
            jsonData.addProperty("body", data.getBody());
            jsonArray.add(jsonData);
        }
        return new Gson().toJson(jsonArray);
    }
    public String convertJsonStringOneObject(Data data){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id",data.getId());
        jsonObject.addProperty("title",data.getTitle());
        jsonObject.addProperty("body",data.getBody());
        return jsonObject.toString();
    }
}
