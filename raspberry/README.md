# Samiz Raspberry Pi

A comprehensive Python service that runs in a Docker container and provides BLE (Bluetooth Low Energy) communication capabilities for data synchronization. This is a migration of the Kotlin BLE functionality to Python, designed specifically for Raspberry Pi deployment.

## Prerequisites

Make sure Docker and Docker Compose are installed on your Raspberry Pi:

```bash
# Install Docker on Raspberry Pi
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Install Docker Compose (if not already included)
sudo apt-get update
sudo apt-get install docker-compose-plugin

# Add your user to the docker group (optional, to run without sudo)
sudo usermod -aG docker $USER
# Log out and back in for this to take effect
```

## Usage

### Option 1: Using Docker Compose (Recommended)

```bash
# Build and run the container with Docker Compose
docker compose up --build

# Or run in detached mode (background)
docker compose up --build -d

# Clean up after running
docker compose down
```

### Option 2: Manual Docker commands

```bash
# Build the Docker image
docker build -t samiz .

# Run the Docker container
docker run --rm samiz
```

### Bluetooth Requirements

For Bluetooth functionality to work properly:

1. **Raspberry Pi Bluetooth**: Ensure Bluetooth is enabled on your Raspberry Pi
   ```bash
   # Check if Bluetooth is running
   sudo systemctl status bluetooth
   
   # Enable Bluetooth if not running
   sudo systemctl enable bluetooth
   sudo systemctl start bluetooth
   ```

2. **Container Permissions**: The Docker container runs with privileged access to use Bluetooth hardware
3. **Host Network**: Uses host networking mode to access Bluetooth services
4. **D-Bus Access**: Mounts D-Bus socket for system communication

## Raspberry Pi Specific Considerations

- The Dockerfile uses `python:3.9-slim` which supports ARM architecture (required for Raspberry Pi)
- The image is optimized for size and performance on resource-constrained devices
- The `--rm` flag automatically removes the container after execution to save space

## Troubleshooting

### General Issues
- If you get permission errors, make sure Docker is properly installed and your user is in the docker group
- If the build fails, ensure you have internet connectivity to download the base image
- For older Raspberry Pi models, the initial image download might take some time

### Bluetooth-Specific Issues

**"No Bluetooth adapter found or accessible"**
- Check if Bluetooth is enabled: `sudo systemctl status bluetooth`
- Verify Bluetooth adapter is detected: `hciconfig` or `bluetoothctl list`
- Make sure the container has privileged access (already configured in docker-compose.yml)

**"Bluetooth error occurred" or Permission Denied**
- Ensure the Docker container is running with privileged mode
- Check if other Bluetooth applications are blocking access
- Try running with sudo: `sudo docker compose up --build`

**Build fails when installing Bleak**
- This usually indicates missing system dependencies
- The Dockerfile should handle this, but ensure bluez is installed: `sudo apt-get install bluez`
- Check that your system supports Bluetooth Low Energy (BLE)
