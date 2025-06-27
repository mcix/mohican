package nl.bytesoflife.inspector;

import nl.bytesoflife.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@org.springframework.context.annotation.Configuration
public class CanonDriverConfiguration {

    @Bean
    @Primary
    public CanonDriverWrapperInterface canonDriverWrapper() {
        if (Configuration.getInstance().getCameraSimulate()) {
            // Return the simulated wrapper
            return new SimulatedCanonDriverWrapper();
        } else {
            // Return the real wrapper
            return new CanonDriverWrapper();
        }
    }
} 