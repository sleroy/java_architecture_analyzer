#!/bin/bash

# Deploy EJB Application to WildFly using Docker
# This script builds a custom Docker image and runs the container

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONTAINER_NAME="demo-ejb2-wildfly"
IMAGE_NAME="demo-ejb2-app"
IMAGE_TAG="latest"
HTTP_PORT=8080
MGMT_PORT=9990

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  EJB Application Docker Deployment${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker daemon is not running${NC}"
    echo "Please start Docker and try again"
    exit 1
fi

echo -e "${GREEN}✓ Docker is available${NC}"

# Stop and remove existing container if it exists
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo ""
    echo -e "${YELLOW}Stopping and removing existing container...${NC}"
    docker stop "$CONTAINER_NAME" 2>/dev/null || true
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    echo -e "${GREEN}✓ Old container removed${NC}"
fi

# Build Docker image
echo ""
echo -e "${YELLOW}Building Docker image (this may take a few minutes on first build)...${NC}"
echo -e "${BLUE}Image will include: Maven build + WildFly + Application WAR${NC}"
echo ""

docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ Docker image built successfully${NC}"

# Start container from custom image
echo ""
echo -e "${YELLOW}Starting WildFly container...${NC}"
docker run -d \
    --name "$CONTAINER_NAME" \
    -p ${HTTP_PORT}:8080 \
    -p ${MGMT_PORT}:9990 \
    "${IMAGE_NAME}:${IMAGE_TAG}"

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to start container${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Container started successfully${NC}"

# Wait for WildFly to start and deploy application
echo ""
echo -e "${YELLOW}Waiting for WildFly to start and deploy application...${NC}"
sleep 5

# Check if deployment was successful
DEPLOYED=false
for i in {1..30}; do
    if docker logs "$CONTAINER_NAME" 2>&1 | grep -q "demo-ejb2-project.war.*deployed"; then
        DEPLOYED=true
        break
    fi
    if docker logs "$CONTAINER_NAME" 2>&1 | grep -q "WFLYSRV0025.*started"; then
        # WildFly started, check if WAR is deployed
        if docker logs "$CONTAINER_NAME" 2>&1 | grep -q "demo-ejb2-project.war"; then
            DEPLOYED=true
            break
        fi
    fi
    echo -n "."
    sleep 2
done
echo ""

if [ "$DEPLOYED" = true ]; then
    echo -e "${GREEN}✓ Application deployed successfully!${NC}"
else
    echo -e "${YELLOW}⚠ Deployment status unclear. Check logs with: docker logs $CONTAINER_NAME${NC}"
fi

# Display access information
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}  Application is Ready!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Access URLs:${NC}"
echo -e "  • Web Interface:  ${GREEN}http://localhost:${HTTP_PORT}/demo-ejb2-project/${NC}"
echo -e "  • REST API:       ${GREEN}http://localhost:${HTTP_PORT}/demo-ejb2-project/rest/members${NC}"
echo -e "  • SOAP WSDL:      ${GREEN}http://localhost:${HTTP_PORT}/demo-ejb2-project/MemberWebService?wsdl${NC}"
echo -e "  • Admin Console:  ${GREEN}http://localhost:${MGMT_PORT}/console${NC}"
echo ""
echo -e "${YELLOW}Container Management:${NC}"
echo -e "  • View logs:      ${BLUE}docker logs -f $CONTAINER_NAME${NC}"
echo -e "  • Stop server:    ${BLUE}docker stop $CONTAINER_NAME${NC}"
echo -e "  • Start server:   ${BLUE}docker start $CONTAINER_NAME${NC}"
echo -e "  • Remove server:  ${BLUE}docker rm -f $CONTAINER_NAME${NC}"
echo -e "  • Shell access:   ${BLUE}docker exec -it $CONTAINER_NAME bash${NC}"
echo ""
echo -e "${YELLOW}Image Management:${NC}"
echo -e "  • List images:    ${BLUE}docker images | grep $IMAGE_NAME${NC}"
echo -e "  • Remove image:   ${BLUE}docker rmi ${IMAGE_NAME}:${IMAGE_TAG}${NC}"
echo -e "  • Rebuild image:  ${BLUE}./deploy-docker.sh${NC}"
echo ""
echo -e "${BLUE}Note: The Docker image includes everything - Maven build, WildFly, and your application!${NC}"
echo -e "${GREEN}Happy testing! 🚀${NC}"
