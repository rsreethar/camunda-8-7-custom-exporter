# 🚀 Camunda 8.7 Custom Exporter – Production‑Ready README

Purpose:
This README documents the exact working setup, commands, and failure recovery steps
for running a custom Zeebe exporter on Camunda Platform 8.7 (Self‑Managed, Helm/Kubernetes).
It is written so a new engineer can reproduce the setup without tribal knowledge and
understand why certain standard approaches do NOT work.

---

## 1. Problem Statement

Camunda 8’s default Elasticsearch exporter indexes all variables, including system and
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
    │   ├── values-configmap-mount.yaml     (WORKING – used by this POC)
    │   └── values-init-container.yaml      (ENTERPRISE STANDARD – does NOT work for Zeebe)
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

## 6. Why the Init‑Container Approach DOES NOT Work (CRITICAL)

In many enterprise Kubernetes standards, custom binaries are injected using
an init‑container that copies files into a shared volume.

This approach FAILS for Zeebe.

### Root Cause

- Zeebe containers run as **non‑root user (UID 1000)**
- Init‑containers typically run as **root (UID 0)**
- Files copied by init‑containers are owned by root
- Zeebe is **not allowed** to read or execute those files

Result:

- Zeebe startup fails
- Exporter validation fails
- Pod enters crash loop

This is a **platform‑level security constraint**, not a configuration bug.

The file `values-init-container.yaml` is kept only as a reference to this
enterprise‑wide pattern, but it MUST NOT be used for Zeebe exporters.

---

## 7. Working Strategy: ConfigMap‑Based JAR Projection (MANDATORY)

The ONLY reliable approach for Zeebe is:

- Package exporter as a JAR
- Project it into the container using a **read‑only ConfigMap**
- Avoid filesystem ownership changes entirely

Zeebe does not scan `/lib/custom` automatically.
The JAR must be mounted AND explicitly referenced.

---

## 8. Create / Update ConfigMap

    kubectl create configmap ups-custom-exporter-jar \
      --from-file=target/ups-custom-exporter-poc-1.0-SNAPSHOT.jar \
      -o yaml --dry-run=client | kubectl apply -f -

Verify mount inside the pod:

    kubectl exec camunda-zeebe-0 -- ls /usr/local/zeebe/lib/custom

Expected:

    ups-custom-exporter-poc-1.0-SNAPSHOT.jar

---

## 9. Enable the Custom Exporter (CRITICAL STEP)

Both CLASSNAME and JARPATH are required.
Missing either will crash Zeebe at startup.

    kubectl set env statefulset/camunda-zeebe \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_CLASSNAME=com.ups.camunda.poc.UpsCustomExporter \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_JARPATH=/usr/local/zeebe/lib/custom/ups-custom-exporter-poc-1.0-SNAPSHOT.jar

Restart Zeebe to apply:

    kubectl delete pod camunda-zeebe-0

---

## 10. Elasticsearch Connectivity (Startup Stability)

Ensure Zeebe exporters can reach Elasticsearch:

    kubectl set env statefulset/camunda-zeebe \
      ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_ARGS_ELASTICSEARCH_URL=http://camunda-elasticsearch:9200

Important notes:

- Elasticsearch may be Running but not Ready during startup
- Initial Connection refused errors are expected
- Zeebe exporter retries automatically

---

## 11. Verification Checklist

### Pod Health

    kubectl get pods
    kubectl describe pod camunda-zeebe-0

Expected:
- READY: 1/1
- No restart loops

### Exporter JAR Loaded

    kubectl exec camunda-zeebe-0 -- ls /usr/local/zeebe/lib/custom

### Exporter Execution

    kubectl logs camunda-zeebe-0 | findstr UpsCustomExporter

Presence of UpsCustomExporter.export() confirms execution.

---

## 12. Known Errors and Fixes

### ClassNotFoundException

    ExporterCfg{ jarPath='null', className='com.ups.camunda.poc.UpsCustomExporter' }

Cause:
- JARPATH missing or incorrect

Fix:
- Re‑apply exporter env vars

---

### Connection refused (Elasticsearch)

Cause:
- Elasticsearch not ready yet

Fix:
- Wait for readiness
- Verify service:

    kubectl get svc camunda-elasticsearch

---

### volumeMount not found

Cause:
- Volume name mismatch in Helm values

Fix:
- Ensure volume and volumeMount names match exactly

---

### Duplicate env vars (Helm + kubectl)

Cause:
- Repeated kubectl set env combined with Helm upgrades

Fix:

    kubectl set env statefulset/camunda-zeebe ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_CLASSNAME-
    kubectl set env statefulset/camunda-zeebe ZEEBE_BROKER_EXPORTERS_UPSCUSTOMEXPORTERPOC_JARPATH-

    helm upgrade camunda camunda/camunda-platform -f values-configmap-mount.yaml --version 12.8.1

---

## 13. PowerShell vs Linux Shell Warning

Do NOT paste Zeebe container startup logs into PowerShell.

Examples that will fail:

    export VAR=value
    ls /exporters
    echo "No exporters available"

These are Linux shell traces, not PowerShell commands.

---

## 14. Final Working State

- Zeebe image: camunda/zeebe:8.7.24
- Helm chart: camunda-platform 12.8.1
- Exporter JAR mounted at: /usr/local/zeebe/lib/custom
- Exporter class: com.ups.camunda.poc.UpsCustomExporter
- Zeebe pod status: READY = True

---

## 15. Next Enhancements

- Add exporter metrics
- Add variable-level allow/deny rules
- Split indices per business domain
- Add ILM policies for exporter indices

---

This README documents the ONLY approach that works reliably for Zeebe exporters
under enterprise Kubernetes security constraints.
