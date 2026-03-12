# 🚀 Camunda 8.7 Custom Exporter – Storage Optimization POC

## 1. Project Objective

This project demonstrates a production‑validated Proof of Concept (POC) for addressing
Elasticsearch storage explosion in **Camunda Platform 8.7 (Self‑Managed)** environments.

The default Camunda Elasticsearch exporter indexes a large volume of system and runtime
variables that are not business‑relevant. Over time, this leads to excessive index growth,
higher storage costs, and operational overhead.

This POC introduces a **Selective Ingestion Custom Exporter** that ensures **only
business‑critical variables** are indexed.

Filtering rule:
- Only variables prefixed with `X_` are retained
- All other variable records are discarded before indexing

Outcome:
- ~90% reduction in indexed variable volume
- Predictable Elasticsearch growth
- Clear separation of business vs system data

---

## 2. Scope and Assumptions

This project is designed for:

- Camunda Platform **8.7.x**
- **Self‑Managed** deployments only
- Kubernetes + Helm based installations

Out of scope:
- Camunda 8 SaaS (custom exporters are not supported)
- Camunda 8 Run (local lightweight runtime)
- Camunda 7

---

## 3. Architecture Overview

This solution replaces the default Elasticsearch exporter behavior by introducing
a custom exporter at the **Zeebe broker level**.

High‑level flow:

Zeebe Broker  
→ Exporter API  
→ Custom Variable Filter (`X_` prefix)  
→ Elasticsearch (custom indices)

Key characteristics:
- Exporter processes **all Zeebe records**
- Filtering logic is applied inside the exporter
- Non‑matching records are dropped before indexing
- Exporter is loaded **during Zeebe startup**
- Misconfiguration will cause broker startup failure (expected behavior)

---

## 4. Environment Prerequisites

Ensure the following environment is available before setup:

- Operating System: Windows 10 / 11
- PowerShell: 5.1 or later
- Memory: 16 GB RAM minimum
  - At least 8 GB allocated to Docker
- Container Runtime: Docker Desktop (latest)
- Kubernetes Tooling:
  - kubectl v1.27+
  - Helm v3.12+
- Camunda Helm Chart: v12.8.1
- Build Tools:
  - Java 17
  - Maven 3.8+
- IDE (optional): IntelliJ IDEA

---

## 5. Repository Structure

    camunda-8-7-custom-exporter/
    ├── src/
    │   └── main/java/com/ups/camunda/poc
    │       └── Custom Exporter implementation
    ├── infra/
    │   ├── values-configmap-mount.yaml   # ✅ Primary working Helm values
    │   └── values-init-container.yaml    # ❌ Legacy approach (permission failure)
    ├── models/
    │   └── BPMN samples using underscore variable naming
    ├── scripts/
    │   ├── deploy-poc.ps1                # End-to-end deployment automation
    │   └── verify-storage.ps1            # Elasticsearch verification
    ├── pom.xml                           # Maven build and shading configuration
    └── README.md                         # Project documentation

---

## 6. Build and Packaging

The exporter is implemented as a Java JAR and injected into the Zeebe broker at runtime.

### 6.1 Build Executable JAR

Run the following command from the project root:

    mvn clean package

Expected output artifact:

    target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar

---

## 7. Kubernetes Injection Strategy (Security Constraint)

### Problem

- Zeebe containers run as **UID 1000**
- Standard init‑containers run as **UID 0**
- Writing to Zeebe directories from init‑containers fails
- Results in startup and permission errors

### Solution

Instead of copying the JAR at runtime, the exporter is injected using a
**read‑only ConfigMap volume projection**.

Create the ConfigMap:

    kubectl create configmap ups-custom-exporter-jar ^
      --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar

This avoids filesystem ownership conflicts entirely.

---

## 8. Zeebe Exporter Configuration

The exporter is configured via Helm values in:

    infra/values-configmap-mount.yaml

Key concepts:
- Exporter ID must be unique
- Exporter class name must match the Java implementation
- JAR path must reference the mounted ConfigMap
- Any validation failure will prevent Zeebe startup

This is expected and ensures early failure detection.

---

## 9. Deployment

### 9.1 Add Camunda Helm Repository

    helm repo add camunda https://helm.camunda.io
    helm repo update

### 9.2 Install Camunda Platform

    helm install poc camunda/camunda-platform ^
      --version 12.8.1 ^
      -f infra/values-configmap-mount.yaml

---

## 10. Engineering Pivots and Resolutions

| Area | Issue | Resolution |
|----|------|-----------|
| Optimize Auth | 500 errors / redirect loops | Enabled `global.identity.auth.enabled: true` |
| Security | UID 0 → UID 1000 write failure | Switched to ConfigMap projection |
| BPMN / FEEL | Hyphen treated as minus operator | Migrated variables to underscore (`X_id`) |
| Indexing | Excessive Elasticsearch growth | Custom Selective Ingestion exporter |

---

## 11. Validation and Evidence

### 11.1 Exporter Load Verification

Verify exporter initialization in Zeebe logs:

    kubectl logs statefulset/camunda-zeebe | findstr "UPS Custom Exporter"

Presence of log entry confirms exporter loading.

---

### 11.2 Storage Verification (0‑Byte Test)

Execute:

    Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices/optimize-*?v"

Expected behavior:

- optimize-variable-update
  - 0 documents (system noise blocked)
- optimize-ups-filtered-data
  - Business variables present

---

## 12. Customization Guidance

You can adapt this POC by:

- Changing the variable prefix logic in exporter code
- Adding additional record‑type filters
- Modifying index naming strategies
- Extending exporter logic to forward data to other sinks

---

## 13. Common Failure Scenarios

- Zeebe pod fails to start
  → Exporter validation or configuration issue
- No data in Elasticsearch
  → Exporter not loaded or filter too restrictive
- Optimize UI returns 500 errors
  → Identity / auth misconfiguration

---

## 14. Final Checklist

- Build JAR successfully
- Create ConfigMap before Helm install
- Use `values-configmap-mount.yaml`
- Verify exporter logs
- Verify Elasticsearch indices
