# 🚀 Camunda 8.7 Custom Exporter – Storage Optimization POC

## 1. Project Objective

This POC addresses the Elasticsearch storage explosion observed in Camunda 8.7 environments.

A Selective Ingestion strategy is implemented so that only business‑critical variables
(prefixed with X_) are indexed.

Outcome: ~90% reduction in system noise and Elasticsearch storage footprint.

---

## 2. Environment Prerequisites

- Operating System: Windows 10 / 11 (PowerShell 5.1+)
- Memory: 16 GB RAM minimum
  - At least 8 GB allocated to Docker
- Container Runtime: Docker Desktop (latest)
- Orchestration Tools:
  - kubectl v1.27+
  - Helm v3.12+
  - Camunda Helm Chart v12.8.1
- Build Tools:
  - Java 17
  - Maven 3.8+
- IDE: IntelliJ IDEA (recommended)

---

## 3. Build and Packaging

### 3.1 Build Executable JAR

Run the following command:

    mvn clean package

Expected output:

    target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar

---

### 3.2 Kubernetes Injection (Permission Fix)

Problem observed:

- Zeebe runs as UID 1000
- Init containers run as UID 0
- Result: write permission failures

Solution:

Project the exporter JAR using a ConfigMap instead of an init container.

Command:

    kubectl create configmap ups-custom-exporter-jar ^
      --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar

---

## 4. Deployment

### 4.1 Add Camunda Helm Repository

    helm repo add camunda https://helm.camunda.io
    helm repo update

---

### 4.2 Install Camunda Platform

    helm install poc camunda/camunda-platform ^
      --version 12.8.1 ^
      -f infra/values-configmap-mount.yaml

---

## 5. Engineering Log – Critical Pivots

| Component      | Issue Faced                     | Engineering Resolution                                  |
|---------------|---------------------------------|---------------------------------------------------------|
| Optimize Auth | 500 errors / redirect loops     | Forced global.identity.auth.enabled: true               |
| Security      | UID write failure               | Switched to ConfigMap read-only projection               |
| BPMN Syntax  | Hyphen treated as minus sign    | Migrated variables to underscore format (X_id)          |
| Indexing     | Elasticsearch storage bloat     | Implemented custom Selective Ingestion exporter          |

---

## 6. Validation and Evidence

### 6.1 Exporter Load Verification

Check Zeebe logs:

    kubectl logs statefulset/camunda-zeebe | findstr "UPS Custom Exporter"

Exporter initialization message confirms successful loading.

---

### 6.2 Storage Verification (0‑Byte Test)

Run the following PowerShell command:

    Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices/optimize-*?v"

Expected results:

- optimize-variable-update
  - 0 documents (system noise blocked)
- optimize-ups-filtered-data
  - Business data present

---

## 7. Repository Structure

    camunda-8-7-custom-exporter/
    ├── src/                          # Java selective ingestion logic
    ├── infra/
    │   ├── values-configmap-mount.yaml   # Current working configuration
    │   └── values-init-container.yaml    # Legacy reference (permission failure)
    ├── models/                       # BPMN samples (underscore variables)
    ├── scripts/
    │   ├── deploy-poc.ps1            # Deployment automation
    │   └── verify-storage.ps1        # Elasticsearch validation
    ├── pom.xml                       # Maven build configuration
    └── README.md

---

## Final Demo Checklist

- Ensure ups-custom-exporter-jar ConfigMap exists before Helm install
- If Zeebe pods fail:
  - Check Kubernetes events
  - Look for volume mount or permission errors
