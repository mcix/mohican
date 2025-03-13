package nl.bytesoflife.mohican;

import lombok.Data;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ReduxAction {
    private String type;
    private Object value;

    public void setValue(Object value) {
        if( value instanceof LinkedHashMap ) {
            LinkedHashMap<String, String> valueMap = (LinkedHashMap<String, String>) value;
            DeviceMessage deviceMessage = new DeviceMessage();
            Object device = valueMap.get("device");
            if( device == null ) {
                this.value = value;
            } else {
                deviceMessage.setDevice(valueMap.get("device"));
                deviceMessage.setMessage(valueMap.get("value"));
                this.value = deviceMessage;
            }
        } else {
            this.value = value;
        }
    }

    public static ReduxAction fromJson(String json) {
        JsonParser parser = JsonParserFactory.getJsonParser();
        Map<String, Object> jsonMap = parser.parseMap(json);

        ReduxAction action = new ReduxAction();
        action.setType((String) jsonMap.get("type"));
        //get the string value of the value
        if( action.getType().equals("MESSAGE")) {
            LinkedHashMap<String, String> value = (LinkedHashMap<String, String>) jsonMap.get("value");
            DeviceMessage deviceMessage = new DeviceMessage();
            deviceMessage.setDevice(value.get("device"));
            deviceMessage.setMessage(value.get("message"));
            action.setValue(deviceMessage);
        } else {
            action.setValue((String) jsonMap.get("value"));
        }
        return action;
    }
}
