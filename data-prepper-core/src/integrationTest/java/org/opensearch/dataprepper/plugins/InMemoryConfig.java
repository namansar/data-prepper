/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for an in_memory plugin.
 */
class InMemoryConfig {
    @JsonProperty("testing_key")
    private String testingKey;

    public String getTestingKey() {
        return testingKey;
    }

    public void setTestingKey(final String testingKey) {
        this.testingKey = testingKey;
    }
}
