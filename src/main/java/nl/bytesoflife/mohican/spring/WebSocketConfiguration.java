package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@org.springframework.context.annotation.Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration extends AbstractWebSocketMessageBrokerConfigurer {

    public static final String MESSAGE_PREFIX = "/topic";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = Configuration.getInstance().getAllowedOrigins();

        // SockJS is required, not optional: dp-3's browser client connects with
        // `Stomp.over(() => SockJS(url))` (BomMohican.js), which first probes
        // `/mohican/info`. Without withSockJS() that probe 404s and the browser
        // never connects at all — taking the v1 CANON_* protocol and every motor
        // push down with it, not just the newer camera protocol.
        //
        // setWebSocketEnabled(true) keeps the native WebSocket transport available
        // inside the SockJS negotiation, so a raw-WS client still works and the
        // frontend can migrate off sockjs-client when it wants to.
        registry.addEndpoint("/mohican", "/sock", "/").setAllowedOriginPatterns(
            allowedOrigins
        ).withSockJS().setWebSocketEnabled(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(MESSAGE_PREFIX);
        registry.setApplicationDestinationPrefixes("/app");
    }


}
