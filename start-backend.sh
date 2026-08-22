#!/bin/bash
cd "$(dirname "$0")/springboot-backend"
set -a
source .env
set +a
exec ./mvnw spring-boot:run
