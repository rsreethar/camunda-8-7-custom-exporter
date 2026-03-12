# 🚀 Camunda 8.7 Custom Exporter – Production‑Ready README

Purpose: This README documents the exact working setup, commands, and failure recovery steps
for running a custom Zeebe exporter on Camunda Platform 8.7 (Self‑Managed, Helm/Kubernetes).
It is written so a new engineer can reproduce the setup without tribal knowledge.

---

## 1. Problem Statement

Camunda 8.7  default Elasticsearch exporter indexes all variables, including system and
technical noise. Over time this causes:

- Rapid Elasticsearch index growth
- Higher storage and operational costs
- Slower Optimize queries

This project introduces a custom Zeebe exporter that:

- Intercepts Zeebe records
- Filters variables by a defined rule (example: business variables only)
- Delegates persistence to Elasticsearch using Camunda’s exporter APIs

---

## 2. Supported Scope

- ✅ Camunda Platform 8.7.x (Self‑Managed)
- ✅ Kubernetes (Helm chart 12.8.1)
- ✅ Custom Java exporter packaged as a JAR

- ❌ Camunda SaaS
- ❌ Camunda Run
- ❌ Camunda 7

---

## 3. High‑Level Architecture

    Zeebe Broker
       |
       |-- Default Elasticsearch Exporter (system records)
       |
       `-- UpsCustomExporter (business‑filtered records)
              |
              `-- Elasticsearch

Key points:

- Exporters are loaded at Zeebe startup
- A misconfigured exporter will crash the broker (expected behavior)
- Zeebe retries exporter failures automatically

---

## 4. Repository Structure

    camunda-8-7-custom-exporter/
    ├── src/main/java/com/ups/camunda/poc/
    │   └── UpsCustomExporter.java
    ├── pom.xml
    ├── infra/
    │   └── values.yaml
    ├── scripts/
    │   └── verify-elasticsearch.ps1
    └── README.md

---

## 5. Build the Exporter JAR

From the project root:

    mvn clean package

Expected output:

    target/ups-custom-exporter-poc-1.0-SNAPSHOT.jar

---

## 6. Mount the Exporter JAR into Zeebe (MANDATORY)

Zeebe does not scan /lib/custom automatically. The JAR must be:

1. Mounted into the container
2. Explicitly referenced via JARPATH

### 6.1 Create / Update ConfigMap

    kubectl create configmap ups-custom-exporter-jar \
      --from-file=target/ups-custom-exporter-poc-1.0-SNAPSHOT.jar \
      -o yaml --dry-run=client | kubectl apply -f -

### 6.2 Verify Mount Inside Pod

    kubectl exec camunda-zeebe-0 -- ls /usr/local/zeebe/lib/custom

Expected:

    ups-custom-exporter-poc-1.0-SNAPSHOT.jar

---

## 7. Enable the Custom Exporter (CRITICAL STEP)

Both CLASSNAME and JARPATH are required. Missing either will crash Zeebe.

    kubectl set env statefulset/camunda-zeebe \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_CLASSNAME=com.ups.camunda.poc.UpsCustomExporter \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_JARPATH=/usr/local/zeebe/lib/custom/ups-custom-exporter-poc-1.0-SNAPSHOT.jar

Restart Zeebe to apply:

    kubectl delete pod camunda-zeebe-0

---

## 8. Elasticsearch Connectivity (Required for Startup Stability)

Ensure Zeebe exporters can reach Elasticsearch:

    kubectl set env statefulset/camunda-zeebe \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_ARGS_ELASTICSEARCH_URL=http://camunda-elasticsearch:9200

Important notes:

- During cluster startup, Elasticsearch may be running but not ready
- Initial Connection refused errors are expected and retried automatically

---

## 9. Verification Checklist

### 9.1 Pod Health

    kubectl get pods
    kubectl describe pod camunda-zeebe-0

Expected:

- READY: 1/1
- No restart loops

### 9.2 Exporter JAR Loaded

    kubectl exec camunda-zeebe-0 -- ls /usr/local/zeebe/lib/custom

### 9.3 Exporter Execution

    kubectl logs camunda-zeebe-0 | findstr UpsCustomExporter

Presence of stack traces referencing UpsCustomExporter.export() confirms execution.

---

## 10. Known Error Messages and How to Fix Them

### Error: ClassNotFoundException

    Failed to load exporter with configuration
    ExporterCfg{ jarPath='null', className='com.ups.camunda.poc.UpsCustomExporter' }

Cause:
- JARPATH not set or incorrect

Fix:

    kubectl set env statefulset/camunda-zeebe \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_JARPATH=/usr/local/zeebe/lib/custom/ups-custom-exporter-poc-1.0-SNAPSHOT.jar

---

### Error: Connection refused (Elasticsearch)

    ElasticsearchExporterException: Failed to put component template
    Caused by: java.net.ConnectException: Connection refused

Cause:
- Elasticsearch not yet ready
- Incorrect service URL

Fix:

    kubectl get svc camunda-elasticsearch

Then wait for readiness (Zeebe retries automatically).

---

### Error: Helm upgrade volumeMount not found

    volumeMounts[].name: Not found: "zeebe-lib-share"

Cause:
- Volume name mismatch between volumes and volumeMounts

Fix:
- Ensure volume and mount names match exactly in values.yaml

---

### Error: Duplicate environment variables (Helm + kubectl)

Cause:
- Repeated kubectl set env combined with Helm upgrades

Fix (cleanup first):

    kubectl set env statefulset/camunda-zeebe ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_CLASSNAME-
    kubectl set env statefulset/camunda-zeebe ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_JARPATH-

    helm upgrade camunda camunda/camunda-platform -f values.yaml --version 12.8.1

---

## 11. PowerShell vs Linux Shell Warning (IMPORTANT)

Do NOT paste Zeebe container startup logs into PowerShell.

Examples that will fail in PowerShell:

    export VAR=value
    ls /exporters
    echo "No exporters available"

These are Linux shell traces, not PowerShell commands.

---

## 12. Why This Design Works

- ConfigMap avoids UID and permission issues
- Explicit JARPATH avoids classpath ambiguity
- Zeebe exporter retry model handles transient Elasticsearch outages
- Fail-fast startup prevents silent data loss

---

## 13. Final Working State (Reference)

- Zeebe image: camunda/zeebe:8.7.24
- Helm chart: camunda-platform 12.8.1
- Exporter JAR mounted at: /usr/local/zeebe/lib/custom
- Exporter class: com.ups.camunda.poc.UpsCustomExporter
- Zeebe pod status: READY = True

---

## 14. Next Enhancements

- Add exporter metrics
- Add variable-level allow/deny rules
- Split indices per business domain
- Add ILM policies for exporter indices

---

This README reflects the real, battle-tested setup that is currently working.
