/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.testkit.perf.load.rest.representation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StartScenarioRequest {

    private final String jobPlan;
    private final String agentPlan;
    private final int jobSize;
    private final double scaleFactor;

    @JsonCreator
    public StartScenarioRequest(@JsonProperty("jobPlan") String jobPlan,
                                @JsonProperty("jobSize") int jobSize,
                                @JsonProperty("agentPlan") String agentPlan,
                                @JsonProperty("scaleFactor") double scaleFactor) {
        this.jobPlan = jobPlan;
        this.agentPlan = agentPlan;
        this.jobSize = jobSize;
        this.scaleFactor = scaleFactor;
    }

    public String getJobPlan() {
        return jobPlan;
    }

    public String getAgentPlan() {
        return agentPlan;
    }

    public int getJobSize() {
        return jobSize;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }
}
