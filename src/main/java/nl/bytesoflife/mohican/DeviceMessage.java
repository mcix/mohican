package nl.bytesoflife.mohican;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class DeviceMessage {
    private String device;
    private String message;

    public static DeviceMessage fromLinkedHashMap(LinkedHashMap<String, String> hashMap) {
        DeviceMessage deviceMessage = new DeviceMessage();
        deviceMessage.setDevice(hashMap.get("device"));
        deviceMessage.setMessage(hashMap.get("value"));
        return deviceMessage;
    }
}
