# Camera Simulation

This project now supports camera simulation for testing and development purposes without requiring a physical Canon camera.

## Configuration

To enable camera simulation, set the following property in your `configuration.properties` file:

```properties
camera.simulate=true
```

To use the real camera, set it to `false` or remove the property (defaults to `false`):

```properties
camera.simulate=false
```

## How it Works

The application uses Spring's conditional bean configuration to automatically inject either:

- **SimulatedCanonDriverWrapper**: When `camera.simulate=true`
- **CanonDriverWrapper**: When `camera.simulate=false` (or not set)

Both implementations implement the same `CanonDriverWrapperInterface`, ensuring compatibility.

## Simulated Camera Features

The simulated camera provides realistic responses for all camera operations:

- **Photo taking**: Simulates taking photos and increments a counter
- **Settings**: Maintains simulated aperture, ISO, shutter speed, and image quality settings
- **Image retrieval**: Returns minimal valid JPEG data for testing
- **Camera detection**: Always reports camera as found
- **Event notifications**: Properly triggers camera connect/disconnect and photo taken events

## Benefits

1. **Development**: Test camera functionality without physical hardware
2. **CI/CD**: Run automated tests without camera dependencies
3. **Demo**: Demonstrate camera features in environments without cameras
4. **Debugging**: Isolate camera-related issues from hardware problems

## Usage

The simulated camera behaves exactly like the real camera from the application's perspective. All existing camera commands and WebSocket messages work identically.

### Example Simulated Responses

- `getAllImageInfo()`: Returns `["IMG_0001.JPG", "IMG_0002.JPG", ...]`
- `takePhoto()`: Returns `0` (success) and increments photo counter
- `getImage("IMG_0001.JPG")`: Returns minimal valid JPEG data
- `getApertureSetting()`: Returns current simulated aperture setting

## Logging

The simulated camera logs all operations with the prefix "Simulated camera" for easy identification in logs. 