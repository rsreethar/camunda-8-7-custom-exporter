# Camunda 8.7 Custom Exporter: Storage Optimization POC

## 1. Project Objective
This POC addresses the **Elasticsearch storage explosion** observed in Camunda 8.7 environments. By implementing a "Selective Ingestion" strategy, we intercept the exporter stream to ensure only business-critical variables (prefixed with `X_`) are indexed. 

**Outcome:** System noise and storage costs are reduced by approximately **90%**.

---

## 2. Environment Prerequisites
Before attempting the installation, ensure the workstation matches this configuration:
* **Operating System:** Windows 10/11 (PowerShell 5.1+)
* **Resources:** 16GB RAM Minimum (8GB+ allocated to Docker)
* **Container Runtime:** Docker Desktop (Latest)
* **Orchestration:** Kubectl v1.27+, Helm v3.12+ (Using **Chart v12.8.1**)
* **Build Tools:** Java 17, Maven 3.8+
* **IDE:** IntelliJ IDEA (Recommended for Maven/K8s integration)

---

## 3. Build and Packaging
The Java logic must be packaged and injected into the Kubernetes cluster.

### 3.1 Build Executable JAR
```bash
mvn clean package
Result: target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar is generated.3.2 Kubernetes Injection (The Permission Fix)Standard Init-Containers fail due to UID 1000 security constraints in Zeebe. We bypass this by projecting the JAR via a ConfigMap:PowerShellkubectl create configmap ups-custom-exporter-jar --from-file=target/camunda-8-7-custom-exporter-1.0-SNAPSHOT.jar
4. DeploymentWe use Helm Chart version 12.8.1 to ensure compatibility with the 8.7 component suite.Bashhelm repo add camunda [https://helm.camunda.io](https://helm.camunda.io)
helm repo update

# Deployment using the ConfigMap Mount Profile
helm install poc camunda/camunda-platform --version 12.8.1 -f infra/values-configmap-mount.yaml
5. Engineering Log: Critical PivotsComponentIssue FacedEngineering ResolutionOptimize Auth500 Errors / Redirect LoopsForced global.identity.auth.enabled: true to satisfy OIDC handshake requirements.SecurityInit-Container (UID 0) cannot write to Zeebe (UID 1000) folders.Switched to Read-Only Volume Projection via ConfigMap.BPMN SyntaxFEEL engine treats hyphens (-) as minus signs.Migrated all business variables to Underscore notation (X_id).IndexingStandard exporter bloats Elasticsearch.Overrode ElasticsearchExporter with custom Selective Ingestion logic.6. Validation and Evidence6.1 Infrastructure VerificationVerify the custom exporter class is successfully loaded in the Zeebe logs:Bashkubectl logs statefulset/camunda-zeebe | grep "UPS Custom Exporter"
6.2 Storage Verification (The "0-Byte" Test)Port-forward Elasticsearch (Port 9200) and execute the following PowerShell command:PowerShellInvoke-RestMethod -Uri "http://localhost:9200/_cat/indices/optimize-*?v"
Expected Results:optimize-variable-update: Must show 0 documents (Default system noise blocked).optimize-ups-filtered-data: Must show active documents (Business data captured).7. Repository HierarchyPlaintextcamunda-8-7-custom-exporter/
├── /src                          # Java source for Selective Ingestion logic
├── /infra                        # Kubernetes & Helm configurations
│   ├── values-configmap-mount.yaml # Current Master (Successful Pivot)
│   ├── values-init-container.yaml # Legacy Reference (Security Failure)
├── /models                       # BPMN samples with underscore naming
├── /scripts                      # Automation for demo and verification
│   ├── deploy-poc.ps1            # Automates ConfigMap and Helm install
│   └── verify-storage.ps1        # Automates the 0-byte ES verification
├── pom.xml                       # Maven configuration for JAR shading
└── README.md                     # Technical research log
Final Demo Check: Ensure the ups-custom-exporter-jar ConfigMap is created before running the Helm install. If the Zeebe pods fail to start, check the Kubernetes events for "Volume Mount" errors.
