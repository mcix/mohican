package nl.bytesoflife.inspector;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

@org.springframework.context.annotation.Configuration
public class CanonDriverConfiguration {

    /**
     * Owns all three drivers and decides which one holds the camera. The initial
     * choice comes from {@code camera.driver} in configuration.properties
     * ({@code sdk} by default; {@code camera.simulate=true} still forces
     * {@code simulate}); after boot the mohican UI toggle and the
     * {@code CAMERA_SELECT_DRIVER} command can switch it at runtime.
     */
    @Bean(destroyMethod = "shutdown")
    public CameraDriverRegistry cameraDriverRegistry() {
        return new CameraDriverRegistry();
    }

    /**
     * The wrapper everything injects. It is a thin delegate onto the registry's
     * active driver, so switching drivers needs no changes in any caller.
     */
    @Bean
    @Primary
    public CanonDriverWrapperInterface canonDriverWrapper(final CameraDriverRegistry registry) {
        return new DelegatingCanonDriverWrapper(registry);
    }

    /**
     * Bring the configured driver up once the context is ready.
     * <p>
     * Drivers no longer self-start from their own ApplicationReadyEvent: with
     * three of them in the context that would have every driver racing for the
     * same camera, and EDSDK allows only one session per body.
     */
    @Bean
    public CameraDriverStarter cameraDriverStarter(final CameraDriverRegistry registry) {
        return new CameraDriverStarter(registry);
    }

    public static class CameraDriverStarter {

        private final CameraDriverRegistry registry;

        CameraDriverStarter(final CameraDriverRegistry registry) {
            this.registry = registry;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            registry.activateConfigured();
        }
    }
}
