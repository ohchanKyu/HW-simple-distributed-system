import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class DataStorage {

    private static DataStorage instance;
    private static final ReentrantLock instanceLock = new ReentrantLock();
    private final ReentrantLock methodLock = new ReentrantLock();
    private final List<Data> dataList;
    private Long currentId;

    private DataStorage() {
        dataList = new ArrayList<>();
        currentId = 1L;
    }

    public static DataStorage getInstance() {
        if (instance == null) {
            instanceLock.lock();
            try {
                if (instance == null) {
                    instance = new DataStorage();
                }
            } finally {
                instanceLock.unlock();
            }
        }
        return instance;
    }

    public Data save(String title, String body) {
        methodLock.lock();
        try{
            Data newData = new Data(currentId++, title, body);
            dataList.add(newData);
            return newData;
        }finally {
            methodLock.unlock();
        }

    }

    public Optional<Data> findById(Long id) {
        return dataList.stream()
                .filter(data -> data.getId().equals(id))
                .findFirst();
    }

    public boolean updateDataWithPutMethod(Long id, String newTitle, String newBody) {
        methodLock.lock();
        try{
            Optional<Data> dataOptional = findById(id);
            if (dataOptional.isPresent()) {
                Data data = dataOptional.get();
                data.setTitle(newTitle);
                data.setBody(newBody);
                return true;
            }
            return false;
        }finally {
            methodLock.unlock();
        }

    }

    public boolean updateDataWithPatchMethod(Long id, String newTitle, String newBody) {
        methodLock.lock();
        try{
            Optional<Data> dataOptional = findById(id);
            if (dataOptional.isPresent()) {
                Data data = dataOptional.get();
                if (newTitle != null) {
                    data.setTitle(newTitle);
                }
                if (newBody != null) {
                    data.setBody(newBody);
                }
                return true;
            }
            return false;
        }finally {
            methodLock.unlock();
        }

    }

    public boolean deleteById(Long id) {
        methodLock.lock();
        try{
            return dataList.removeIf(data -> data.getId().equals(id));
        }finally {
            methodLock.unlock();
        }

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
