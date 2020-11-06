/**
 * Copyright 2016-2020 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.specification.kafka.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;
import static org.reaktivity.specification.kafka.internal.types.KafkaConditionType.HEADER;
import static org.reaktivity.specification.kafka.internal.types.KafkaConditionType.HEADERS;
import static org.reaktivity.specification.kafka.internal.types.KafkaConditionType.KEY;
import static org.reaktivity.specification.kafka.internal.types.KafkaConditionType.NOT;

import java.nio.ByteBuffer;
import java.util.Objects;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;
import org.reaktivity.specification.kafka.internal.types.Array32FW;
import org.reaktivity.specification.kafka.internal.types.KafkaDeltaType;
import org.reaktivity.specification.kafka.internal.types.KafkaOffsetFW;
import org.reaktivity.specification.kafka.internal.types.KafkaSkip;
import org.reaktivity.specification.kafka.internal.types.KafkaValueMatchFW;
import org.reaktivity.specification.kafka.internal.types.OctetsFW;
import org.reaktivity.specification.kafka.internal.types.control.KafkaRouteExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaApi;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaBootstrapBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaDataExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaDescribeBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaDescribeDataExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaFetchBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaFetchDataExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaFetchFlushExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaFlushExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaMergedBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaMergedDataExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaMergedFlushExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaMetaBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaMetaDataExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaProduceBeginExFW;
import org.reaktivity.specification.kafka.internal.types.stream.KafkaProduceDataExFW;

public class KafkaFunctionsTest
{
    private ExpressionFactory factory;
    private ELContext ctx;

    @Before
    public void setUp() throws Exception
    {
        factory = newExpressionFactory();
        ctx = new ExpressionContext();
    }

    @Test
    public void shouldGenerateRouteExtension()
    {
        byte[] build = KafkaFunctions.routeEx()
                                     .topic("topic")
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaRouteExFW routeEx = new KafkaRouteExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals("topic", routeEx.topic().asString());
    }

    @Test
    public void shouldGenerateBootstrapBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .bootstrap()
                                         .topic("topic")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.BOOTSTRAP.value(), beginEx.kind());

        final KafkaBootstrapBeginExFW bootstrapBeginEx = beginEx.bootstrap();
        assertEquals("topic", bootstrapBeginEx.topic().asString());
    }

    @Test
    public void shouldGenerateMetaBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .meta()
                                         .topic("topic")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.META.value(), beginEx.kind());

        final KafkaMetaBeginExFW metaBeginEx = beginEx.meta();
        assertEquals("topic", metaBeginEx.topic().asString());
    }

    @Test
    public void shouldGenerateMetaDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .meta()
                                         .partition(0, 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.META.value(), dataEx.kind());

        final KafkaMetaDataExFW metaDataEx = dataEx.meta();
        final MutableInteger partitionsCount = new MutableInteger();
        metaDataEx.partitions().forEach(f -> partitionsCount.value++);
        assertEquals(1, partitionsCount.value);

        assertNotNull(metaDataEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.leaderId() == 1));
    }

    @Test
    public void shouldGenerateDescribeBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .describe()
                                         .topic("topic")
                                         .config("cleanup.policy")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.DESCRIBE.value(), beginEx.kind());

        final KafkaDescribeBeginExFW describeBeginEx = beginEx.describe();
        assertEquals("topic", describeBeginEx.topic().asString());

        final MutableInteger configsCount = new MutableInteger();
        describeBeginEx.configs().forEach(f -> configsCount.value++);
        assertEquals(1, configsCount.value);

        assertNotNull(describeBeginEx.configs()
                .matchFirst(c -> "cleanup.policy".equals(c.asString())));
    }

    @Test
    public void shouldGenerateDescribeDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .describe()
                                         .config("cleanup.policy", "compact")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.DESCRIBE.value(), dataEx.kind());

        final KafkaDescribeDataExFW describeDataEx = dataEx.describe();
        final MutableInteger configsCount = new MutableInteger();
        describeDataEx.configs().forEach(f -> configsCount.value++);
        assertEquals(1, configsCount.value);

        assertNotNull(describeDataEx.configs()
                .matchFirst(c -> "cleanup.policy".equals(c.name().asString()) &&
                                 "compact".equals(c.value().asString())));
    }

    @Test
    public void shouldGenerateMergedBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));

    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithHeaderNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .headerNot("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == HEADER.value() &&
                    "name".equals(c.not().condition().header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.not().condition().header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithKeyNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .keyNot("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == KEY.value() &&
                    "match".equals(c.not().condition().key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithNullKeyOrHeaderNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .keyNot(null)
                                             .build()
                                         .filter()
                                             .headerNot("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == KEY.value() &&
                    Objects.isNull(c.not().condition().key().value())) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == HEADER.value() &&
                    "name".equals(c.not().condition().header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.not().condition().header().value())) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithNullKeyOrNullHeaderValue()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .key(null)
                                             .build()
                                         .filter()
                                             .header("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        final MutableInteger partitionCount = new MutableInteger();
        mergedBeginEx.partitions().forEach(f -> partitionCount.value++);
        assertEquals(1, partitionCount.value);

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    Objects.isNull(c.key().value())) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.header().value())) != null));
    }

    @Test
    public void shouldGenerateMergedDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null);
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithByteArrayValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .deferred(100)
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerBytes("name", "value".getBytes(UTF_8))
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(100, mergedDataEx.deferred());
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                                  .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                          .value()
                                          .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                                  .matchFirst(h ->
                                                  "name".equals(h.name()
                                                                 .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                                      "value".equals(h.value()
                                                             .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null);
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertNull(mergedDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithNullKeyAndNullByteArrayHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                     .timestamp(12345678L)
                                     .partition(0, 0L)
                                     .progress(0, 1L)
                                     .key(null)
                                     .headerBytes("name", null)
                                     .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                                  .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertNull(mergedDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                                  .matchFirst(h ->
                                                  "name".equals(h.name()
                                                                 .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                                      Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateMergedFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .merged()
                                         .progress(0, 1L)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaMergedFlushExFW mergedFlushEx = flushEx.merged();
        final MutableInteger partitionsCount = new MutableInteger();
        mergedFlushEx.progress().forEach(f -> partitionsCount.value++);
        assertEquals(1, partitionsCount.value);

        assertNotNull(mergedFlushEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));
    }

    @Test
    public void shouldMatchMergedDataExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .deferred(100)
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.deferred(100)
                             .timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L, 1L)
                                                 .progress(0, 1L, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();


        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L).latestOffset(1L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L).latestOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithByteArrayValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerBytes("name", "value".getBytes(UTF_8))
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .partition(0, 0L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .progress(0, 1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .key(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .delta("NONE", -1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeaderWithNullByteArrayValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .headerBytes("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x02)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .timestamp(123456789L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 1L)
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .progress(0, 2L)
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 10L)
                                                 .timestamp(12345678L)
                                                 .delta("JSON_PATCH", 9L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(10L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateFetchBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateFetchBeginExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L, 0L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());
        assertEquals(0L, partition.latestOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }
    @Test
    public void shouldGenerateFetchBeginExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L)
                                         .filter()
                                             .key(null)
                                             .build()
                                         .filter()
                                             .header("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    Objects.isNull(c.key().value())) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.header().value())) != null));
    }

    @Test
    public void shouldGenerateFetchDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(10, fetchDataEx.deferred());
        assertEquals(12345678L, fetchDataEx.timestamp());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        assertEquals("match", fetchDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null);
    }

    @Test
    public void shouldGenerateFetchDataExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .partition(0, 0L, 0L)
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(10, fetchDataEx.deferred());
        assertEquals(12345678L, fetchDataEx.timestamp());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());
        assertEquals(0L, partition.latestOffset());

        assertEquals("match", fetchDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                                 .matchFirst(h ->
                                     "name".equals(h.name()
                                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                         "value".equals(h.value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null);
    }

    @Test
    public void shouldGenerateFetchDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(12345678L, fetchDataEx.timestamp());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        assertNull(fetchDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateFetchFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .partition(0, 1L)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();
        final KafkaOffsetFW partition = fetchFlushEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(1L, partition.partitionOffset());
    }

    @Test
    public void shouldGenerateFetchFlushExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .partition(0, 1L, 1L)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();
        final KafkaOffsetFW partition = fetchFlushEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(1L, partition.partitionOffset());
        assertEquals(1L, partition.latestOffset());
    }

    @Test
    public void shouldMatchFetchDataExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 0L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 0L, 0L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L).latestOffset(0L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .partition(0, 0L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .key(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .delta("NONE", -1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x02)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldNotBuildMatcher() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1);

        Object matched = matcher.match(byteBuf);

        assertNull(matched);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(123456789L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .partition(0, 0L)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateProduceBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .produce()
                                         .transaction("transaction")
                                         .producerId(1)
                                         .topic("topic")
                                         .partitionId(0)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), beginEx.kind());

        final KafkaProduceBeginExFW produceBeginEx = beginEx.produce();
        assertEquals("transaction", produceBeginEx.transaction().asString());
        assertEquals(1, produceBeginEx.producerId());
        assertEquals("topic", produceBeginEx.topic().asString());
        assertEquals(0, produceBeginEx.partitionId());
    }

    @Test
    public void shouldGenerateProduceDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .produce()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .sequence(0)
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), dataEx.kind());

        final KafkaProduceDataExFW produceDataEx = dataEx.produce();
        assertEquals(10, produceDataEx.deferred());
        assertEquals(12345678L, produceDataEx.timestamp());
        assertEquals(0, produceDataEx.sequence());

        assertEquals("match", produceDataEx.key()
                                           .value()
                                           .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        produceDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(produceDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null);
    }


    @Test
    public void shouldMatchProduceDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                               .sequence(0)
                               .key(k -> k.length(5)
                                          .value(v -> v.set("match".getBytes(UTF_8))))
                               .headersItem(h -> h.nameLen(4)
                                                  .name(n -> n.set("name".getBytes(UTF_8)))
                                                  .valueLen(5)
                                                  .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionSequence() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .sequence(0)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .key(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchProduceDataExtensionSequence() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .produce()
                                                 .timestamp(12345678L)
                                                 .sequence(1)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchProduceDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .produce()
                                                 .sequence(0)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateProduceDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .produce()
                                         .timestamp(12345678L)
                                         .sequence(0)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), dataEx.kind());

        final KafkaProduceDataExFW produceDataEx = dataEx.produce();
        assertEquals(12345678L, produceDataEx.timestamp());
        assertEquals(0, produceDataEx.sequence());

        assertNull(produceDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        produceDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(produceDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateFetchBeginExtensionWithHeadersFilter()
    {
        KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                        .topic("topic")
                                        .partition(0, 1L)
                                        .filter()
                                            .headers("name")
                                                .sequence("one", "two")
                                                .skip(1)
                                                .sequence("four")
                                                .skipMany()
                                                .build()
                                            .build()
                                        .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(1, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADERS.value() &&
                    "name".equals(c.headers().name()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c ->
                {
                    boolean matches;
                    final Array32FW<KafkaValueMatchFW> values = c.headers().values();
                    final DirectBuffer items = values.items();

                    int progress = 0;

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches = "one".equals(valueMatchRO.value()
                                                       .value()
                                                       .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "two".equals(valueMatchRO.value()
                                                        .value()
                                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP == valueMatchRO.skip().get();

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "four".equals(valueMatchRO.value()
                                                         .value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP_MANY == valueMatchRO.skip().get();

                    return c.kind() == HEADERS.value() && matches;
                }) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithHeadersFilter()
    {
        KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                        .topic("topic")
                                        .partition(0, 1L)
                                        .filter()
                                            .headers("name")
                                                .sequence("one", "two")
                                                .skip(1)
                                                .sequence("four")
                                                .skipMany()
                                                .build()
                                            .build()
                                        .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(1, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADERS.value() &&
                    "name".equals(c.headers().name()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c ->
                {
                    boolean matches;
                    final Array32FW<KafkaValueMatchFW> values = c.headers().values();
                    final DirectBuffer items = values.items();

                    int progress = 0;

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches = "one".equals(valueMatchRO.value()
                                                       .value()
                                                       .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "two".equals(valueMatchRO.value()
                                                        .value()
                                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP == valueMatchRO.skip().get();

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "four".equals(valueMatchRO.value()
                                                         .value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP_MANY == valueMatchRO.skip().get();

                    return c.kind() == HEADERS.value() && matches;
                }) != null));
    }

    @Test
    public void shouldInvokeLength() throws Exception
    {
        String expressionText = "${kafka:length(\"text\")}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, int.class);
        Object actual = expression.getValue(ctx);
        assertEquals("text".length(), ((Integer) actual).intValue());
    }

    @Test
    public void shouldInvokeLengthAsShort() throws Exception
    {
        String expressionText = "${kafka:lengthAsShort(\"text\")}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, short.class);
        Object actual = expression.getValue(ctx);
        assertEquals("text".length(), ((Short) actual).intValue());
    }

    @Test
    public void shouldInvokeNewRequestId() throws Exception
    {
        String expressionText = "${kafka:newRequestId()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, Integer.class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof Integer);
    }

    @Test
    public void shouldInvokeRandomBytes() throws Exception
    {
        String expressionText = "${kafka:randomBytes(10)}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof byte[]);
        assertEquals(10, ((byte[]) actual).length);
    }

    @Test
    public void shouldInvokeTimestamp() throws Exception
    {
        String expressionText = "${kafka:timestamp()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, Long.class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof Long);
    }

    @Test
    public void shouldComputeVarintTenBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", Long.MAX_VALUE);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xfe, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTenBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 62);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 62);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 55);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 55);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 48);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 48);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 41);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 41);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 34);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 34);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 27);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 27);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 20);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 20);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 13);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 13);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 6);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 6);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMin() throws Exception
    {
        String expressionText = "${kafka:varint(0)}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { 0x00 }, actuals);
    }

    @Test
    public void shouldResolveOffsetTypeHistorical()
    {
        assertEquals(-2, KafkaFunctions.offset("HISTORICAL"));
    }

    @Test
    public void shouldResolveOffsetTypeLive()
    {
        assertEquals(-1, KafkaFunctions.offset("LIVE"));
    }
}
