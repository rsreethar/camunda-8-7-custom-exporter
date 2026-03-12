package com.ups.camunda.poc;

import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UPS Custom Exporter for Camunda 8.7
 * Implements a "Selective Ingestion" strategy to optimize Elasticsearch storage.
 */
public class UpsCustomExporter extends ElasticsearchExporter {

    private static final Logger LOG = LoggerFactory.getLogger(UpsCustomExporter.class);
    private final String VARIABLE_PREFIX = "X_";
    private ElasticsearchExporterConfiguration configuration;

    @Override
    public void configure(Context context) {
        // Instantiate the configuration object from the Zeebe context
        this.configuration = context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);

        // Set the custom index prefix BEFORE parent configuration
        // This redirects filtered data to a dedicated UPS index to prevent system-wide bloat
        this.configuration.index.prefix = "optimize-ups-filtered-data";

        // Initialize the parent ElasticsearchExporter with modified configuration
        super.configure(context);

        LOG.info("UPS Custom Exporter: Configured for prefix: {}", this.configuration.index.prefix);
    }

    @Override
    public void open(Controller controller) {
        LOG.info("UPS_DEBUG: Attempting to open exporter connection...");
        super.open(controller);
        LOG.info("UPS_DEBUG: Exporter opened successfully.");
    }

    @Override
    public void export(Record<?> record) {
        // Core Logic: Only export data matching the UPS partner filter
        if (shouldExport(record)) {
            if (record.getValueType() == ValueType.VARIABLE) {
                VariableRecordValue varValue = (VariableRecordValue) record.getValue();
                LOG.info("UPS_DATA_FOUND: Exporting variable [{}]", varValue.getName());
            }

            // Pass authorized records to the parent for Elasticsearch indexing
            super.export(record);
        }
        // Records not matching the filter are skipped here, preventing storage accumulation in ES.
    }

    private boolean shouldExport(Record<?> record) {
        // Always include process metadata for lifecycle visualization in Optimize
        if (record.getValueType() == ValueType.PROCESS_INSTANCE) {
            return true;
        }

        // Apply partner-prefix filter to process variables
        if (record.getValueType() == ValueType.VARIABLE) {
            VariableRecordValue varValue = (VariableRecordValue) record.getValue();
            return varValue.getName().startsWith(VARIABLE_PREFIX);
        }

        return false;
    }
}