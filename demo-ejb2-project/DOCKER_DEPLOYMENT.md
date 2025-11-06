# Docker Deployment Guide

Deploy and run the EJB application using a custom Docker image with everything built-in.

## 🎯 Overview

This deployment uses a **multi-stage Docker build** that:
1. ✅ Builds the Maven project inside Docker
2. ✅ Creates a custom WildFly image with your WAR pre-deployed
3. ✅ No need for local Java or Maven installation!

## 🚀 Quick Start

### One Command Deployment

```bash
cd demo-ejb2-project
./deploy-docker.sh
```

That's it! The script will:
- Build a custom Docker image (includes Maven build + WildFly + your app)
- Start a container from that image
- Display all access URLs

## 📦 What's Inside the Docker Image

**Stage 1 - Builder:**
- Maven 3.9 with Java 8
- Builds your application from source
- Creates the WAR file

**Stage 2 - Runtime:**
- WildFly application server
- Your compiled WAR file (pre-deployed)
- Optimized for production

**Benefits:**
- 🔒 **Reproducible**: Same build every time
- 📦 **Portable**: Share the image with anyone
- 🚀 **Fast startup**: WAR is already deployed
- 🧹 **Clean**: No build artifacts in runtime image

## 🌐 Access Your Application

Once deployed, access these services:

| Service | URL |
|---------|-----|
| **Web Interface** | http://localhost:8080/demo-ejb2-project/ |
| **REST API** | http://localhost:8080/demo-ejb2-project/rest/members |
| **SOAP Services** | http://localhost:8080/demo-ejb2-project/MemberWebService?wsdl |
| **Admin Console** | http://localhost:9990/console |

## 🛠️ Container Management

### View Real-time Logs

```bash
docker logs -f demo-ejb2-wildfly
```

### Stop the Application

```bash
docker stop demo-ejb2-wildfly
```

### Start Again

```bash
docker start demo-ejb2-wildfly
```

### Remove Container

```bash
docker rm -f demo-ejb2-wildfly
```

### Access Container Shell

```bash
docker exec -it demo-ejb2-wildfly bash
```

## 🔄 Rebuilding After Code Changes

When you modify your code, simply rerun the deployment script:

```bash
./deploy-docker.sh
```

This will:
1. Stop the old container
2. Rebuild the Docker image with your changes
3. Start a new container with the updated application

## 📋 Docker Image Management

### List Your Images

```bash
docker images | grep demo-ejb2-app
```

### Remove the Image

```bash
docker rmi demo-ejb2-app:latest
```

### Check Image Size

```bash
docker images demo-ejb2-app:latest
```

## 🔧 Customization

### Change Ports

Edit `deploy-docker.sh`:

```bash
HTTP_PORT=8080
MGMT_PORT=9990
```

### Modify Memory Settings

Edit `Dockerfile`:

```dockerfile
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
```

### Use Different WildFly Version

Edit `Dockerfile`:

```dockerfile
FROM quay.io/wildfly/wildfly:31.0.0.Final
```

## 🧪 Testing the Deployment

### Test REST API

```bash
# Get all members
curl http://localhost:8080/demo-ejb2-project/rest/members

# Create a member
curl -X POST http://localhost:8080/demo-ejb2-project/rest/members \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","phoneNumber":"1234567890"}'
```

### Test SOAP Service

```bash
curl -X POST http://localhost:8080/demo-ejb2-project/MemberWebService \
  -H "Content-Type: text/xml" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://soap.example.com/">
   <soapenv:Body>
      <soap:getAllMembers/>
   </soapenv:Body>
</soapenv:Envelope>'
```

## 🐛 Troubleshooting

### Build Fails

**Check Docker is running:**
```bash
docker info
```

**Check Dockerfile syntax:**
```bash
docker build -t test . --no-cache
```

### Container Won't Start

**Check port conflicts:**
```bash
# See what's using port 8080
lsof -i :8080
```

**Check logs:**
```bash
docker logs demo-ejb2-wildfly
```

### Application Not Responding

**Wait a bit longer** - WildFly startup can take 30-60 seconds

**Check deployment status:**
```bash
docker logs demo-ejb2-wildfly | grep "deployed"
```

### Clean Restart

Remove everything and start fresh:

```bash
# Remove container
docker rm -f demo-ejb2-wildfly

# Remove image
docker rmi demo-ejb2-app:latest

# Remove dangling images
docker image prune -f

# Rebuild and redeploy
./deploy-docker.sh
```

## 🎓 Understanding the Multi-Stage Build

### Stage 1: Builder
```dockerfile
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests
```

- Downloads dependencies (cached layer)
- Compiles your code
- Creates WAR file

### Stage 2: Runtime
```dockerfile
FROM quay.io/wildfly/wildfly:latest
COPY --from=builder /build/target/demo-ejb2-project.war /opt/jboss/wildfly/standalone/deployments/
```

- Starts with clean WildFly image
- Copies only the WAR file (not build tools)
- Results in smaller, production-ready image

## 📊 Comparing Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **Volume Mount** | Fast development | Permission issues, OS-dependent |
| **Copy into Container** | Simple | Manual steps, not reproducible |
| **Custom Image** ✅ | Reproducible, portable, clean | Rebuild on changes |

## 🌟 Best Practices

1. **Use .dockerignore** to exclude unnecessary files
2. **Tag images** with versions for production
3. **Use specific base image versions** (not `latest`) in production
4. **Monitor container resources** with `docker stats`
5. **Back up data** if using databases

## 📚 Additional Resources

- **WildFly Documentation**: https://docs.wildfly.org/
- **Docker Best Practices**: https://docs.docker.com/develop/dev-best-practices/
- **Multi-stage Builds**: https://docs.docker.com/build/building/multi-stage/

## 🎉 Ready to Deploy!

You now have a fully containerized EJB application with:
- ✅ Automated building and deployment
- ✅ Reproducible environments
- ✅ Easy sharing and distribution
- ✅ Production-ready setup

Happy deploying! 🚀
