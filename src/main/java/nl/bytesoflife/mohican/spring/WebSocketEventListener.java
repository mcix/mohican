package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.mohican.Mohican;
import nl.bytesoflife.mohican.ReduxAction;
import nl.bytesoflife.mohican.ReduxEventListener;
import nl.bytesoflife.mohican.WebsocketProviderListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.List;

@Controller
@RestController
@RequestMapping(value = "")
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private List<ReduxEventListener> eventListenerList= new ArrayList();
    private WebsocketProviderListener websocketProviderListener= null;

    public void addReduxEventListener(ReduxEventListener listener) {
        eventListenerList.add( listener );
    }

    public void addWebsocketProviderListener(Mohican listener) {
        websocketProviderListener = listener;
    }
    private void handleReduxEvent( ReduxAction event ) {
        for (ReduxEventListener reduxEventListener : eventListenerList) {
            reduxEventListener.onMessage( event );
        }
    }

    private void handleOnConnect( ) {
        for (ReduxEventListener reduxEventListener : eventListenerList) {
            reduxEventListener.onConnect( );
        }

    }

    private void handleOnDisconnect( ) {
        for (ReduxEventListener reduxEventListener : eventListenerList) {
            reduxEventListener.onDisconnect( );
        }

    }

    @EventListener
    private void handleSessionConnected(SessionConnectEvent event) {
        handleOnConnect();
    }

    @EventListener
    private void handleSessionDisconnect(SessionDisconnectEvent event) {
        handleOnDisconnect();
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity test() {
        return ResponseEntity.ok("Certificate OK");
    }

    @RequestMapping(value = "/mohicanreq", method = RequestMethod.POST)
    public ResponseEntity mohican(@RequestBody ReduxAction action) throws Exception {

        //logger.info("received a message " + action.getType());

        handleReduxEvent(action);

        return ResponseEntity.ok("");
    }

    @RequestMapping(value = "/mohicanpos", method = RequestMethod.GET)
    public Mohican.Position mohicanpos() throws Exception {

        //logger.info("received a pos request");

        if( websocketProviderListener != null ) {
            return websocketProviderListener.getPosition();
        }

        throw new IllegalStateException("Not connected");
    }
}
