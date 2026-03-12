🚀 Camunda 8.7 Custom Exporter – Storage Optimization POC

1. Project Objective
This POC addresses the Elasticsearch storage explosion observed in Camunda 8.7 environments.
By implementing a Selective Ingestion strategy, we intercept the exporter stream and ensure that only business‑critical variables (prefixed with X_) are indexed.
Outcome:
✅ System noise and Elasticsearch storage costs reduced by ~90%

2. Environment Prerequisites
Before attempting installation, ensure the workstation meets the following requirements:

Operating System: Windows 10 / 11 (PowerShell 5.1+)
Memory: 16 GB RAM minimum

At least 8 GB allocated to Docker


Container Runtime: Docker Desktop (latest)
Orchestration Tools:

kubectl v1.27+
Helm v3.12+
Camunda Helm Chart v12.8.1


Build Tools:

Java 17
Maven 3.8+


IDE: IntelliJ IDEA (recommended for Maven + K8s work)


3. Build and Packaging
The custom exporter logic is implemented in Java and packaged as an executable JAR.
3.1 Build Executable JAR
Shellmvn clean packageShow more lines
This generates:
Plain Texttarget/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jarShow more lines

3.2 Kubernetes Injection (Permission Workaround)
Standard init containers fail due to UID conflicts:

Zeebe runs as UID 1000
Init containers often run as UID 0
Result: write permission failures

✅ Solution:
Project the JAR using a ConfigMap instead of an init container.
PowerShellkubectl create configmap ups-custom-exporter-jar `  --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jarShow more lines

4. Deployment
We use Helm Chart v12.8.1 to stay compatible with Camunda 8.7 components.
4.1 Add Camunda Helm Repository
Shellhelm repo add camunda https://helm.camunda.iohelm repo update``Show more lines

4.2 Deploy Using ConfigMap Mount Profile
Shellhelm install poc camunda/camunda-platform \  --version 12.8.1 \  -f infra/values-configmap-mount.yamlShow more lines

5. Engineering Log – Critical Pivots






























ComponentIssue FacedEngineering ResolutionOptimize Auth500 errors / redirect loopsForced global.identity.auth.enabled: true to satisfy OIDCSecurityInit container (UID 0) cannot write to Zeebe (UID 1000)Switched to read‑only ConfigMap volume projectionBPMN SyntaxFEEL treats - as minus operatorMigrated variables to underscore naming (X_id)IndexingDefault exporter bloats ElasticsearchReplaced with Selective Ingestion custom exporter

6. Validation and Evidence
6.1 Infrastructure Verification
Verify that the custom exporter is loaded by Zeebe:
Shellkubectl logs statefulset/camunda-zeebe | grep "UPS Custom Exporter"``Show more lines
✅ Log entry confirms exporter initialization.

6.2 Storage Verification – “0‑Byte Test”

Port‑forward Elasticsearch (9200)
Run the following PowerShell command:

PowerShellInvoke-RestMethod `  -Uri "http://localhost:9200/_cat/indices/optimize-*?v"Show more lines
Expected Results:

optimize-variable-update

✅ 0 documents (system noise blocked)


optimize-ups-filtered-data

✅ Business data present




7. Repository Structure
PowerShellcamunda-8-7-custom-exporter/├── src/                          # Java Selective Ingestion logic├── infra/                        # Kubernetes & Helm configs│   ├── values-configmap-mount.yaml   # ✅ Current master (working)│   └── values-init-container.yaml    # ❌ Legacy (security failure)├── models/                       # BPMN samples (underscore variables)├── scripts/│   ├── deploy-poc.ps1            # ConfigMap + Helm automation│   └── verify-storage.ps1        # 0-byte ES verification├── pom.xml                       # Maven shading config└── README.md                     # Technical research logShow more lines

✅ Final Demo Checklist

Ensure ups-custom-exporter-jar ConfigMap exists before Helm install
If Zeebe pods fail:

Inspect Kubernetes events
Look for Volume Mount or permission errors
