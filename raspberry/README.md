# Raspberry Pi Docker Bluetooth Scanner

A Python application that runs in a Docker container and scans for nearby Bluetooth devices. Specifically designed for Raspberry Pi with Bluetooth capabilities.

## Files

- `app.py` - The main Python script that scans for Bluetooth devices
- `Dockerfile` - Docker configuration optimized for Raspberry Pi with Bluetooth support
- `docker-compose.yml` - Docker Compose configuration with Bluetooth permissions
- `requirements.txt` - Python dependencies including Bleak for Bluetooth functionality
- `README.md` - This documentation file

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

### Option 3: Run the Python script directly

```bash
# If you have Python installed directly on the Raspberry Pi
python3 app.py
```

## Expected Output

When you run the Docker container, you should see:

```
Bluetooth adapter detected and accessible.

Scanning for Bluetooth devices...
----------------------------------------
Found 2 Bluetooth device(s):

1. Device Name: My Phone
   MAC Address: AA:BB:CC:DD:EE:FF
   Signal Strength (RSSI): -45 dBm
   Service UUIDs: 3 service(s)
     - 0000180f-0000-1000-8000-00805f9b34fb
     - 0000180a-0000-1000-8000-00805f9b34fb
     - 0000fff0-0000-1000-8000-00805f9b34fb
   Manufacturer Data: Available (1 entries)

2. Device Name: Wireless Headphones
   MAC Address: 11:22:33:44:55:66
   Signal Strength (RSSI): -62 dBm
   Service UUIDs: No services advertised
   Manufacturer Data: Available (1 entries)

----------------------------------------
Bluetooth scan completed successfully!
```

## Bluetooth Functionality

The application includes Bluetooth Low Energy (BLE) scanning capabilities using Bleak:

- **Device Discovery**: Scans for nearby Bluetooth devices for 10 seconds
- **Device Information**: Shows device names, MAC addresses, and signal strength (RSSI)
- **Service Detection**: Displays advertised service UUIDs for each device
- **Manufacturer Data**: Shows available manufacturer-specific data
- **Adapter Check**: Verifies that a Bluetooth adapter is available and accessible

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

**No devices found during scan**
- Make sure nearby Bluetooth devices are discoverable
- Check if devices are in pairing mode
- Increase scan duration in the Python script if needed
- Verify Bluetooth range (devices should be within ~10 meters)

**Build fails when installing Bleak**
- This usually indicates missing system dependencies
- The Dockerfile should handle this, but ensure bluez is installed: `sudo apt-get install bluez`
- Check that your system supports Bluetooth Low Energy (BLE)

## Extending the Application

To add more functionality:

1. Modify `app.py` with your Python code
2. Add any required dependencies to a `requirements.txt` file
3. Update the Dockerfile to install dependencies:
   ```dockerfile
   COPY requirements.txt .
   RUN pip install --no-cache-dir -r requirements.txt
