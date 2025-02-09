/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder;

import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.peerforwarder.RequiresPeerForwarding;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.commons.collections.CollectionUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.peerforwarder.exception.EmptyPeerForwarderPluginIdentificationKeysException;
import org.opensearch.dataprepper.peerforwarder.exception.UnsupportedPeerForwarderPluginException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeerForwardingProcessingDecoratorTest {
    private static final String TEST_IDENTIFICATION_KEY = "identification_key";

    private Record<Event> record;

    @Mock
    private Processor processor;

    @Mock(extraInterfaces = Processor.class)
    private RequiresPeerForwarding requiresPeerForwarding;

    @Mock(extraInterfaces = Processor.class)
    private RequiresPeerForwarding requiresPeerForwardingCopy;

    @Mock
    private PeerForwarderProvider peerForwarderProvider;

    private String pipelineName;
    private String pluginId;

    @BeforeEach
    void setUp() {
        record = mock(Record.class);
        pipelineName = UUID.randomUUID().toString();
        pluginId = UUID.randomUUID().toString();
    }

    private List<Processor> createObjectUnderTesDecoratedProcessors(final List<Processor> processors) {
        return PeerForwardingProcessorDecorator.decorateProcessors(processors, peerForwarderProvider, pipelineName, pluginId);
    }

    @Test
    void PeerForwardingProcessingDecorator_should_not_have_any_interactions_if_its_not_an_instance_of_RequiresPeerForwarding() {
        assertThrows(UnsupportedPeerForwarderPluginException.class, () -> createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor)));

        verifyNoInteractions(peerForwarderProvider);
    }

    @Test
    void PeerForwardingProcessingDecorator_execute_with_empty_identification_keys_should_throw() {
        when(requiresPeerForwarding.getIdentificationKeys()).thenReturn(Collections.emptySet());

        assertThrows(EmptyPeerForwarderPluginIdentificationKeysException.class, () -> createObjectUnderTesDecoratedProcessors(Collections.singletonList((Processor) requiresPeerForwarding)));
    }

    @Test
    void decorateProcessors_with_different_identification_key_should_throw() {
        List<Processor> processorList = new ArrayList<>();
        processorList.add((Processor) requiresPeerForwarding);
        processorList.add((Processor) requiresPeerForwardingCopy);

        when(requiresPeerForwarding.getIdentificationKeys()).thenReturn(Set.of(UUID.randomUUID().toString()));
        when(requiresPeerForwardingCopy.getIdentificationKeys()).thenReturn(Set.of(UUID.randomUUID().toString()));

        assertThrows(RuntimeException.class, () -> createObjectUnderTesDecoratedProcessors(List.of(((Processor) requiresPeerForwarding), (Processor) requiresPeerForwardingCopy)));
    }


    @Test
    void decorateProcessors_with_empty_processors_should_return_empty_list_of_processors() {
        final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.emptyList());
        assertThat(processors.size(), equalTo(0));
    }

    @Nested
    class WithRegisteredPeerForwarder {
        @Mock
        private RemotePeerForwarder peerForwarder;
        private Set<String> identificationKeys;
        private Processor processor;

        @BeforeEach
        void setUp() {
            identificationKeys = Set.of(TEST_IDENTIFICATION_KEY);

            when(peerForwarderProvider.register(pipelineName, pluginId, identificationKeys)).thenReturn(peerForwarder);
            when(requiresPeerForwarding.getIdentificationKeys()).thenReturn(identificationKeys);
            processor = (Processor) requiresPeerForwarding;
        }

        @Test
        void PeerForwardingProcessingDecorator_should_have_interaction_with_getIdentificationKeys() {
            createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));
            verify(requiresPeerForwarding, times(2)).getIdentificationKeys();
            verify(peerForwarderProvider).register(pipelineName, pluginId, identificationKeys);
        }

        @Test
        void PeerForwardingProcessingDecorator_execute_should_forwardRecords_with_correct_values() {
            List<Record<Event>> testData = Collections.singletonList(record);

            when(peerForwarder.forwardRecords(testData)).thenReturn(testData);

            when(processor.execute(testData)).thenReturn(testData);

            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));
            assertThat(processors.size(), equalTo(1));
            final Collection<Record<Event>> records = processors.get(0).execute(testData);

            verify(requiresPeerForwarding, times(2)).getIdentificationKeys();
            verify(peerForwarder).forwardRecords(testData);
            Assertions.assertNotNull(records);
            assertThat(records.size(), equalTo(testData.size()));
            assertThat(records, equalTo(testData));
        }

        @Test
        void PeerForwardingProcessingDecorator_execute_should_receiveRecords() {
            Collection<Record<Event>> forwardTestData = Collections.singletonList(record);
            Collection<Record<Event>> receiveTestData = Collections.singletonList(mock(Record.class));

            when(peerForwarder.forwardRecords(forwardTestData)).thenReturn(forwardTestData);
            when(peerForwarder.receiveRecords()).thenReturn(receiveTestData);

            final Collection<Record<Event>> expectedRecordsToProcessLocally = CollectionUtils.union(forwardTestData, receiveTestData);

            when(((Processor) requiresPeerForwarding).execute(anyCollection())).thenReturn(expectedRecordsToProcessLocally);

            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList((Processor) requiresPeerForwarding));
            assertThat(processors.size(), equalTo(1));
            final Collection<Record<Event>> records = processors.get(0).execute(forwardTestData);

            verify(requiresPeerForwarding, times(2)).getIdentificationKeys();
            verify(peerForwarder).forwardRecords(forwardTestData);
            verify(peerForwarder).receiveRecords();
            Assertions.assertNotNull(records);
            assertThat(records.size(), equalTo(expectedRecordsToProcessLocally.size()));
            assertThat(records, equalTo(expectedRecordsToProcessLocally));
        }

        @Test
        void PeerForwardingProcessingDecorator_execute_will_call_inner_processors_execute() {
            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));
            Collection<Record<Event>> testData = Collections.singletonList(record);

            assertThat(processors.size(), equalTo(1));
            processors.get(0).execute(testData);
            verify(processor).execute(anyCollection());
        }

        @Test
        void PeerForwardingProcessingDecorator_prepareForShutdown_will_call_inner_processors_prepareForShutdown() {
            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));

            assertThat(processors.size(), equalTo(1));
            processors.get(0).prepareForShutdown();
            verify(processor).prepareForShutdown();
        }

        @Test
        void PeerForwardingProcessingDecorator_isReadyForShutdown_will_call_inner_processors_isReadyForShutdown() {
            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));

            assertThat(processors.size(), equalTo(1));
            processors.get(0).isReadyForShutdown();
            verify(processor).isReadyForShutdown();
        }

        @Test
        void PeerForwardingProcessingDecorator_shutdown_will_call_inner_processors_shutdown() {
            final List<Processor> processors = createObjectUnderTesDecoratedProcessors(Collections.singletonList(processor));

            assertThat(processors.size(), equalTo(1));
            processors.get(0).shutdown();
            verify(processor).shutdown();
        }
    }

}