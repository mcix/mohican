package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.mohican.ReduxAction;
import nl.bytesoflife.mohican.ReduxEventListener;
import nl.bytesoflife.mohican.WebsocketProviderListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;

@Controller
public class WebSocketController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebSocketController.class);

    private List<ReduxEventListener> eventListenerList= new ArrayList();

    public void addReduxEventListener(ReduxEventListener listener) {
        eventListenerList.add( listener );
    }

    @MessageMapping(value = "/mohicanreq")
    public ResponseEntity<String> mohican(String value) throws Exception {

        ReduxAction action = ReduxAction.fromJson(value);
        logger.info("received a message " + action.getType());

        handleReduxEvent(action);

        return ResponseEntity.ok("");
    }

    private void handleReduxEvent( ReduxAction event ) {
        for (ReduxEventListener reduxEventListener : eventListenerList) {
            reduxEventListener.onMessage( event );
        }
    }
}
