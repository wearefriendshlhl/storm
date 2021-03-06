/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.apache.storm.metrics2;

import com.codahale.metrics.Counter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.storm.task.WorkerTopologyContext;
import org.apache.storm.utils.ConfigUtils;

public class TaskMetrics {
    private static final String METRIC_NAME_ACKED = "__ack-count";
    private static final String METRIC_NAME_FAILED = "__fail-count";
    private static final String METRIC_NAME_EMITTED = "__emit-count";
    private static final String METRIC_NAME_TRANSFERRED = "__transfer-count";
    private static final String METRIC_NAME_EXECUTED = "__execute-count";
    private static final String METRIC_NAME_PROCESS_LATENCY = "__process-latency";
    private static final String METRIC_NAME_COMPLETE_LATENCY = "__complete-latency";
    private static final String METRIC_NAME_EXECUTE_LATENCY = "__execute-latency";

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RollingAverageGauge> gauges = new ConcurrentHashMap<>();

    private final String topologyId;
    private final String componentId;
    private final Integer taskId;
    private final Integer workerPort;
    private final StormMetricRegistry metricRegistry;
    private final int samplingRate;


    public TaskMetrics(WorkerTopologyContext context, String componentId, Integer taskid,
                       StormMetricRegistry metricRegistry, Map<String, Object> topoConf) {
        this.metricRegistry = metricRegistry;
        this.topologyId = context.getStormId();
        this.componentId = componentId;
        this.taskId = taskid;
        this.workerPort = context.getThisWorkerPort();
        this.samplingRate = ConfigUtils.samplingRate(topoConf);
    }

    public void spoutAckedTuple(String streamId, long latencyMs) {
        String metricName = METRIC_NAME_ACKED + "-" + streamId;
        Counter c = this.getCounter(metricName, streamId);
        c.inc(this.samplingRate);

        metricName = METRIC_NAME_COMPLETE_LATENCY + "-" + streamId;
        RollingAverageGauge gauge = this.getRollingAverageGauge(metricName, streamId);
        gauge.addValue(latencyMs);
    }

    public void boltAckedTuple(String sourceComponentId, String sourceStreamId, long latencyMs) {
        String key = sourceComponentId + ":" + sourceStreamId;
        String metricName = METRIC_NAME_ACKED + "-" + key;
        Counter c = this.getCounter(metricName, sourceStreamId);
        c.inc(this.samplingRate);

        metricName = METRIC_NAME_PROCESS_LATENCY + "-" + key;
        RollingAverageGauge gauge = this.getRollingAverageGauge(metricName, sourceStreamId);
        gauge.addValue(latencyMs);
    }

    public void spoutFailedTuple(String streamId) {
        String key = streamId;
        String metricName = METRIC_NAME_FAILED + "-" + key;
        Counter c = this.getCounter(metricName, streamId);
        c.inc(this.samplingRate);
    }

    public void boltFailedTuple(String sourceComponentId, String sourceStreamId) {
        String key = sourceComponentId + ":" + sourceStreamId;
        String metricName = METRIC_NAME_FAILED + "-" + key;
        Counter c = this.getCounter(metricName, sourceStreamId);
        c.inc(this.samplingRate);
    }

    public void emittedTuple(String streamId) {
        String key = streamId;
        String metricName = METRIC_NAME_EMITTED + "-" + key;
        Counter c = this.getCounter(metricName, streamId);
        c.inc(this.samplingRate);
    }

    public void transferredTuples(String streamId, int amount) {
        String key = streamId;
        String metricName = METRIC_NAME_TRANSFERRED + "-" + key;
        Counter c = this.getCounter(metricName, streamId);
        c.inc(amount * this.samplingRate);
    }

    public void boltExecuteTuple(String sourceComponentId, String sourceStreamId, long latencyMs) {
        String key = sourceComponentId + ":" + sourceStreamId;
        String metricName = METRIC_NAME_EXECUTED + "-" + key;
        Counter c = this.getCounter(metricName, sourceStreamId);
        c.inc(this.samplingRate);

        metricName = METRIC_NAME_EXECUTE_LATENCY + "-" + key;
        RollingAverageGauge gauge = this.getRollingAverageGauge(metricName, sourceStreamId);
        gauge.addValue(latencyMs);
    }

    private Counter getCounter(String metricName, String streamId) {
        Counter c = this.counters.get(metricName);
        if (c == null) {
            synchronized (this) {
                c = this.counters.get(metricName);
                if (c == null) {
                    c = metricRegistry.counter(metricName, this.topologyId, this.componentId,
                            this.taskId, this.workerPort, streamId);
                    this.counters.put(metricName, c);
                }
            }
        }
        return c;
    }

    private RollingAverageGauge getRollingAverageGauge(String metricName, String streamId) {
        RollingAverageGauge gauge = this.gauges.get(metricName);
        if (gauge == null) {
            synchronized (this) {
                gauge = this.gauges.get(metricName);
                if (gauge == null) {
                    gauge = new RollingAverageGauge();
                    metricRegistry.gauge(metricName, gauge, this.topologyId, this.componentId,
                            streamId, this.taskId, this.workerPort);
                    this.gauges.put(metricName, gauge);
                }
            }
        }
        return gauge;
    }
}
