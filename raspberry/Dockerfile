# Use Python slim image that supports ARM architecture for Raspberry Pi
FROM python:3.9-slim

# Install system dependencies for Bluetooth
RUN apt-get update && apt-get install -y \
    bluez \
    bluetooth \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy Python script to container
COPY app.py .

# Make the script executable
RUN chmod +x app.py

# Set the default command to run the Python script
CMD ["python3", "app.py"]
