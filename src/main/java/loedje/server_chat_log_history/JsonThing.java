package loedje.server_chat_log_history;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonThing {
    private static JsonArray array;
    public static void createJson() {
        JsonObject json = new JsonObject();
        array = new JsonArray();
        json.add("array", array);
    }

    public static void addJsonElement(JsonElement element) {
        array.add(element);
    }

    public static JsonArray getJsonArray() {
        return array;
    }
}
