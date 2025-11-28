package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.mohican.Mohican;
import nl.bytesoflife.mohican.ReduxAction;
import nl.bytesoflife.mohican.ReduxEventListener;
import nl.bytesoflife.mohican.WebsocketProviderListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
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
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RestController
@RequestMapping(value = "")
public class WebEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebEventListener.class);

    private List<ReduxEventListener> eventListenerList= new ArrayList();
    private WebsocketProviderListener websocketProviderListener= null;
    
    // Track active session count
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);

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
        int currentCount = activeSessionCount.incrementAndGet();
        logger.info("Session connected. Active sessions: {}", currentCount);
        
        // Only call handleOnConnect for the first connection
        if (currentCount == 1) {
            handleOnConnect();
        }
    }

    @EventListener
    private void handleSessionDisconnect(SessionDisconnectEvent event) {
        int currentCount = activeSessionCount.decrementAndGet();
        logger.info("Session disconnected. Active sessions: {}", currentCount);
        
        // Only call handleOnDisconnect when all sessions are closed
        if (currentCount == 0) {
            handleOnDisconnect();
        }
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity test() {
        return ResponseEntity.ok("<html><head>"
                + "<title>DeltaProto - Mohican</title>"
                + "<link rel=\"icon\" type=\"image/x-icon\" href=\"/favicon.ico\">"
                + "</head><body>OK</body></html>");
    }

    @RequestMapping(value = "/favicon.ico", method = RequestMethod.GET)
    public ResponseEntity<byte[]> favicon() {
        try {
            ClassPathResource resource = new ClassPathResource("favicon.ico");
            byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/x-icon"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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
