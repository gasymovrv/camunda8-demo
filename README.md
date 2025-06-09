# Camunda 8 Demo Project

This is a demonstration project showcasing the integration of Camunda 8 (Zeebe) with Spring Boot using Kotlin. The project demonstrates how to implement workflow automation using Camunda 8's workflow engine.

## 🚀 Technology Stack

- **Java Version**: 21
- **Kotlin Version**: 2.1.0
- **Spring Boot**: 3.2.12
- **Camunda 8 (Zeebe)**: 8.5.15
- **SpringDoc OpenAPI**: 2.5.0

## 📋 Prerequisites

- Java 21 or higher
- Maven
- Docker and Docker Compose (for local development)
- Camunda 8 Platform (Zeebe)

## 🏗️ Project Structure

```
src/main/kotlin/com/example/camunda8demo/
├── config/         # Configuration classes
├── controller/     # REST API controllers
├── service/        # Business logic services
├── util/          # Utility classes
├── worker/        # Zeebe workers
└── Constants.kt   # Project constants
```

## 🎮 Controllers

The project includes a comprehensive REST API controller ([Controller.kt](src/main/kotlin/com/example/camunda8demo/controller/Controller.kt)) that provides endpoints for:

- Process Management:
  - Create process instances (`POST /processes`)
  - Cancel process instances (`POST /processes/{key}/cancel`)
  - Deploy process definitions (`POST /process-definitions`)
  - Delete process definitions (`DELETE /process-definitions/{key}`)

- Message Handling:
  - Send messages to processes (`POST /messages`)

- Incident Management:
  - Resolve incidents (`POST /incidents/{key}/resolve`)
  - Update job retries (`POST /jobs/{key}/retries`)

- System Information:
  - Get cluster topology (`GET /topology`)
  - Monitor execution times (`GET /execution-time/*`)

## 🔄 BPMN Process Definitions

The project includes several BPMN process definitions in [src/main/resources/bpmn/](src/main/resources/bpmn/):

- [simple-process.bpmn](src/main/resources/bpmn/simple-process.bpmn) - Basic process flow
- [long-process.bpmn](src/main/resources/bpmn/long-process.bpmn) - Extended process with multiple steps
- [timer.bpmn](src/main/resources/bpmn/timer.bpmn) - Process with timer events
- [process-with-embedded-subprocess.bpmn](src/main/resources/bpmn/process-with-embedded-subprocess.bpmn) - Process containing embedded subprocesses
- [process-with-call-activity.bpmn](src/main/resources/bpmn/process-with-call-activity.bpmn) - Process with call activities
- [process-with-multi-instance-call-activity.bpmn](src/main/resources/bpmn/process-with-multi-instance-call-activity.bpmn) - Process with parallel call activities
- [process-with-event-subprocess.bpmn](src/main/resources/bpmn/process-with-event-subprocess.bpmn) - Process with event subprocesses
- [process-with-reusable-call-activity.bpmn](src/main/resources/bpmn/process-with-reusable-call-activity.bpmn) - Process with reusable call activities

## 👷 Zeebe Workers

The project implements three types of workers:

1. **System Workers** ([SystemWorkers.kt](src/main/kotlin/com/example/camunda8demo/worker/SystemWorkers.kt)):
   - Handles system-level tasks
   - Manages process execution time tracking
   - Provides execution statistics

2. **Routing Workers** ([RoutingWorkers.kt](src/main/kotlin/com/example/camunda8demo/worker/RoutingWorkers.kt)):
   - Implements complex routing logic
   - Handles process flow decisions
   - Manages process branching and merging

3. **General Workers** ([Workers.kt](src/main/kotlin/com/example/camunda8demo/worker/Workers.kt)):
   - Handles common business tasks
   - Implements service tasks
   - Manages user tasks

## 🛠️ Setup and Installation

1. Clone the repository:
   ```bash
   git clone [repository-url]
   cd camunda8-demo
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## 🐳 Docker Setup

The project includes Docker configurations for local development in the [docker/local](docker/local) directory. The setup includes:

### Zeebe Clusters
- [Operate](docker/local/zeebe/operate/) - Standalone Zeebe setup with Camunda Operate as a default process monitor and Elasticsearch exporter for event streaming
- [Simple Monitor with Hazelcast](docker/local/zeebe/simple-monitor-hazelcast/) - Standalone Zeebe setup with open source Simple-Monitor instead of Operate and Hazelcast exporter
- [Simple Monitor with Hazelcast Cluster](docker/local/zeebe/simple-monitor-hazelcast-cluster/) - Clustered Zeebe setup with Simple-Monitor and Hazelcast exporter
- [Simple Monitor with Kafka Cluster](docker/local/zeebe/simple-monitor-kafka-cluster/) - Clustered Zeebe setup with Simple-Monitor and Kafka exporter

To start the Docker environment:
```bash
# Start Zeebe with Kafka exporter
cd docker/local/zeebe/simple-monitor-kafka-cluster
docker-compose up -d
```

## 🔧 Configuration

The application uses Spring Boot's configuration system. Key configurations can be found in the [config](src/main/kotlin/com/example/camunda8demo/config) directory and the [application.yml](src/main/resources/application.yml) file.

## 📚 API Documentation

The project uses SpringDoc OpenAPI for API documentation. Once the application is running, you can access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

## 🌐 HTTP Request Examples

The project includes a collection of HTTP request examples in [src/main/resources/http/](src/main/resources/http/) that demonstrate how to interact with different BPMN processes. These examples can be used with tools like IntelliJ IDEA's HTTP Client or VS Code's REST Client.

### Common Operations ([common-requests.http](src/main/resources/http/common-requests.http))
- Cancel process instances
- Update job retries
- Resolve incidents
- Deploy/Delete process definitions
- Get cluster topology
- Monitor execution times

### Process-Specific Examples

1. **Simple Process** ([simple-process-requests.http](src/main/resources/http/simple-process-requests.http))
   - Create instance with registration message
   - Send message to trigger process events

2. **Long Process** ([long-process-requests.http](src/main/resources/http/long-process-requests.http))
   - Create instance of extended process flow
   - Monitor long-running operations

3. **Process with Call Activity** ([process-with-call-activity-requests.http](src/main/resources/http/process-with-call-activity-requests.http))
   - Create instance with call activity
   - Handle subprocess interactions

4. **Process with Embedded Subprocess** ([process-with-embedded-subprocess-requests.http](src/main/resources/http/process-with-embedded-subprocess-requests.http))
   - Create instance with embedded subprocess
   - Manage nested process flow

5. **Process with Event Subprocess** ([process-with-event-subprocess-requests.http](src/main/resources/http/process-with-event-subprocess-requests.http))
   - Create instance with event handling
   - Trigger event subprocesses

6. **Process with Multi-Instance Call Activity** ([process-with-multi-instance-call-activity-requests.http](src/main/resources/http/process-with-multi-instance-call-activity-requests.http))
   - Create instance with parallel call activities
   - Handle multiple concurrent subprocesses

7. **Process with Reusable Call Activity** ([process-with-reusable-call-activity-requests.http](src/main/resources/http/process-with-reusable-call-activity-requests.http))
   - Create instance with reusable components
   - Manage shared process elements

8. **Routing Examples** ([routing/](src/main/resources/http/routing/))
   - Complex routing scenarios
   - Conditional process flows
   - Decision management

To use these examples:
1. Ensure the application is running
2. Open any `.http` file in your IDE
3. Click the "Run" button next to any request
4. View the response in your IDE's HTTP client panel

Note: Some requests use variables (e.g., `{{key}}`) that are automatically managed by the HTTP client.

## 🔗 Additional Resources

- [Camunda 8 Documentation](https://docs.camunda.io/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html) 
