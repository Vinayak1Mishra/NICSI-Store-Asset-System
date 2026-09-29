# NICSI ERP 2.0 Store, Inventory & Asset Lifecycle Management System

## Project Overview
This project constitutes the Phase 0 baseline for the NICSI ERP 2.0 Store, Inventory & Asset Lifecycle Management System. It establishes the foundational architecture and database schema necessary for a modular monolith deployment, utilizing Spring Boot and PostgreSQL. The application manages stores, inventory control, and tracking the lifecycle of assets.

## Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose
- Node.js 20+

## Quick Start
1. Start the backing services using Docker Compose:
   ```bash
   docker-compose up -d
   ```
2. Run the application using Maven (ensure you run this in the `store-service` directory):
   ```bash
   cd store-service
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

## Smoke Test Commands
Use these commands to verify the application is running correctly (ensure application is running on port 8080):
- **Get dev token:** 
  ```bash
  curl -s -X POST http://localhost:8080/api/store/dev/token?username=store.operator
  ```
- **WhoAmI:** 
  ```bash
  curl -H 'Authorization: Bearer <token>' http://localhost:8080/api/store/whoami
  ```
- **Ping (admin only):**
  ```bash
  curl -H 'Authorization: Bearer <token>' http://localhost:8080/api/store/ping
  ```

## Architecture Overview
The system follows a modular monolith approach with a robust database schema divided into three namespaces/schemas to ensure separation of concerns:
- `store`: Handles main operational tables (Inventory, Asset, Requirements, etc.)
- `workflow`: Handles approval mechanisms, actions, and states
- `audit`: Tracks actions against entities over time to preserve history

## Phase Roadmap
- **Phase 0:** Setup core infrastructure, configurations, dependencies, and baseline database schema via Flyway.
- **Phase 1:** Implement core APIs for Master Data (UOM, Categories, Store configuration).
- **Phase 2:** Inventory & Fulfilment workflows (Requisitions, GRN, Stock Management).
- **Phase 3:** Asset Lifecycle & Auditing.
