# Add a dummy argument that changes every time to break the cache layer
ARG CACHE_BUSTER=1

# Change the line before the build to break the cache
RUN echo "Forcing rebuild ${CACHE_BUSTER}" > /dev/null

# Stage 1: Build the Application
FROM eclipse-temurin:17-jdk-jammy AS build

# Set the working directory inside the container
WORKDIR /app

# Copy ALL local files into the container's /app directory.
# This ensures all gradlew, gradle folders, and source code are available.
COPY . .

# Ensure the wrapper is executable
RUN chmod +x ./gradlew

# Build the Ktor application and install it (creates the build/install directory)
# We will use 'clean installDist' to ensure a fresh, full build.
RUN ./gradlew clean installDist

# Stage 2: Final Runtime Image (much smaller)
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# Copy only the installed application from the build stage
COPY --from=build /app/build/install/CritterTrack-Server CritterTrack-Server

# Set the entry point to run your application's startup script
ENTRYPOINT ["/app/CritterTrack-Server/bin/CritterTrack-Server"]

# Expose the default Ktor port
EXPOSE 8080
