**Camunda 8.7 Custom Exporter** - Storage Optimization POC

Project Objective
Address Elasticsearch storage exhaustion in Camunda 8.7 environments by implementing a "Selective Ingestion" strategy. This ensures only business-critical variables (prefixed with X_) are indexed, reducing system noise and storage costs by approximately 90%.

1. Environment Prerequisites
OS: Windows 10/11 (PowerShell 5.1+)

Resources: 16GB RAM Minimum (8GB+ allocated to Docker)

Container Runtime: Docker Desktop (Latest)

Orchestration: Kubectl v1.27+, Helm v3.12+ (Chart v12.8.1)

Build Tools: Java 17, Maven 3.8+

IDE: IntelliJ IDEA (Recommended for Maven/K8s integration)

2. Build and Packaging
The Java logic must be packaged and injected into the Kubernetes cluster.

Step 2.1: Build Executable JAR
Bash
mvn clean package
Result: target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar is generated.

Step 2.2: Kubernetes Injection (Security Workaround)
Standard Init-Containers fail due to UID 1000 security constraints in Zeebe. We bypass this by projecting the JAR via a ConfigMap:

PowerShell
kubectl create configmap ups-custom-exporter-jar --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar
3. Deployment
We use Helm Chart version 12.8.1 to ensure compatibility with the 8.7 component suite.

Bash
helm repo add camunda https://helm.camunda.io
helm repo update

# Deployment using the ConfigMap Mount Profile
helm install poc camunda/camunda-platform --version 12.8.1 -f infra/values-configmap-mount.yaml
4. Engineering Pivots and Resolutions
Optimize OIDC Handshake
Issue: Optimize 8.7 returns 500 errors or infinite redirect loops.

Root Cause: Hard dependency on Identity/Keycloak for authentication.

Resolution: Explicitly enabled global.identity.auth.enabled: true and synchronized PostgreSQL credentials in the Helm values to stabilize the handshake.

The Security/Permission Barrier
Issue: Exporter directory (/lib/custom) is owned by UID 1000; runtime copy via Init-Container (UID 0) fails.

Resolution: Switched to Read-Only Volume Projection via ConfigMap. This provides the JAR to the pod without requiring elevated filesystem permissions.

FEEL Expression Syntax
Issue: Variable names with hyphens (e.g., X-id) fail math operations in 8.7 FEEL engine.

Resolution: Migrated all business variables to Underscore notation (X_id).

5. Validation and Evidence
Step 5.1: Infrastructure Check
Verify the exporter is loaded in the Zeebe logs:

Bash
kubectl logs statefulset/camunda-zeebe | grep "UPS Custom Exporter"
Step 5.2: Storage Verification (The "0-Byte" Test)
Port-forward Elasticsearch (Port 9200) and execute:

PowerShell
Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices/optimize-*?v"
Expected Results:
| Index | Documents | Result |
| :--- | :--- | :--- |
| optimize-variable-update | 0 | SUCCESS (System noise blocked) |
| optimize-ups-filtered-data | > 0 | SUCCESS (Business data captured) |

6. Repository Structure
/src: Java Source for Selective Ingestion logic.

/infra:

values-configmap-mount.yaml: Primary deployment profile.

values-init-container.yaml: Reference for failed security pivot.

/models: BPMN samples using underscore naming conventions.

pom.xml: Maven configuration for shading the exporter JAR.
