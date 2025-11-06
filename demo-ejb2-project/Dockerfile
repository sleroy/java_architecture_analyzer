# Multi-stage Dockerfile for EJB Application
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies (this layer will be cached)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create runtime image with WildFly
FROM quay.io/wildfly/wildfly:latest

# Copy the WAR file from builder stage to WildFly deployments directory
COPY --from=builder /build/target/demo-ejb2-project.war /opt/jboss/wildfly/standalone/deployments/

# Expose HTTP and management ports
EXPOSE 8080 9990

# Set memory options
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# WildFly will automatically deploy any WAR files in the deployments directory
