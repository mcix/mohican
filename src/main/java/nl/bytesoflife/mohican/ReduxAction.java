package nl.bytesoflife.mohican;

import lombok.Data;

@Data
public class ReduxAction {
    private String type;
    private Object value;
}
