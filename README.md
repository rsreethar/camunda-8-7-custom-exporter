# 🚀 Camunda 8.7 Custom Exporter
Project Goal
To address the Elasticsearch storage explosion in Camunda 8.7 production-like environments by implementing a "Selective Ingestion" strategy. This ensures that only business-critical variables (prefixed with X_) are indexed, reducing system noise and storage costs by up to 90%.

## 📋 1. Prerequisites (The Environment)
Before attempting the install, ensure your workstation meets these specs:

OS: Windows 10/11 with PowerShell 5.1+.

RAM: 16GB Minimum (8GB+ dedicated to Docker).

Tools:

Docker Desktop: Latest version.

Kubectl: v1.27+

Helm: v3.12+ (Specifically using Chart v12.8.1).

Java 17 & Maven: For compiling the exporter.

IDE: IntelliJ IDEA (Recommended for Maven/K8s integration).

## 🏗️ 2. Before Installation: Build & Package
We must turn our Java logic into a deployable asset.

Build the JAR:

Bash
mvn clean package
Result: target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar is generated.

Inject into Kubernetes (The Permission Fix):
Standard Init-Containers fail due to UID 1000 security constraints in Zeebe. We bypass this by creating a ConfigMap:

PowerShell
kubectl create configmap ups-custom-exporter-jar --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar
## 🚀 3. Installation Steps
We use Helm Chart 12.8.1 as it is the "Golden Version" that stably supports the 8.7 component suite.

Add Repo:

Bash
helm repo add camunda https://helm.camunda.io
helm repo update
Deploy via Master Profile:

Bash
helm install poc camunda/camunda-platform --version 12.8.1 -f infra/values-configmap-mount.yaml
## ⚙️ 4. Post-Installation Configuration & Troubleshooting
The Optimize "Handshake" Issue
Problem: Optimize 8.7 would fail with a 500 Error or an infinite redirect loop.

Root Cause: 8.7 has a hard dependency on Identity/Keycloak for OIDC authentication.

The Fix: We explicitly enabled global.identity.auth.enabled: true and configured the internal Keycloak PostgreSQL passwords in the YAML to ensure the handshake succeeded.

The "Connection Refused" Fix
The exporter cannot use localhost:9200. We manually injected the internal K8s service URL:

Bash
kubectl set env statefulset/camunda-zeebe ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTER_ARGS_URL="http://camunda-elasticsearch:9200"
## 🔍 5. Validation & Testing
Step 1: Port-Forwarding
PowerShell
kubectl port-forward svc/camunda-elasticsearch 9200
Step 2: The Storage Proof
Run this PowerShell command to compare indices:

PowerShell
Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices/optimize-*?v"
Success Criteria: * optimize-variable-update should show 0 documents (Default variables blocked).

optimize-ups-filtered-data should show active documents (Filtered variables captured).

## 🔄 6. Summary of Engineering Pivots (The "Why")
Pivot Point	Issue Faced	Final Solution
BPMN Variables	FEEL engine treats - as minus (math error).	Migrated all variables to Underscore (X_order_id).
Security	Init-Container couldn't write to /lib/custom.	Switched to Read-Only Volume Projection (ConfigMap).
8.8 vs 8.7	8.8 was unstable in low-RAM local clusters.	Downgraded to 8.7.24 for proven stability in POCs.
Indexing	Standard exporter bloats Elasticsearch.	Overrode ElasticsearchExporter with custom Selective Ingestion logic.
## 📂 7. Repository Hierarchy
/src: Java Source code for the Custom Exporter.

/infra:

values-configmap-mount.yaml (Current Master).

values-init-container.yaml (Legacy Reference).

/models: Optimized BPMN sample file.

pom.xml: Maven build configuration.

This is the complete research log. You have captured every technical detail and workaround. Would you like me to help you push this final version of the README to your repo now?
