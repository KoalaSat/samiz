# Use Python slim image that supports ARM architecture for Raspberry Pi
FROM python:3.9-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Install system dependencies for Bluetooth
RUN apt-get update && apt-get install -y \
    bluez \
    bluetooth \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the entire source code to container
COPY . .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Make the main script executable
RUN chmod +x main.py

# Set the default command to run the main Python script
CMD ["python3", "main.py"]
