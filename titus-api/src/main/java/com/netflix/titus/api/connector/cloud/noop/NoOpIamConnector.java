/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.titus.api.connector.cloud.noop;

import com.netflix.titus.api.connector.cloud.IamConnector;
import com.netflix.titus.api.iam.model.IamRole;
import reactor.core.publisher.Mono;

public class NoOpIamConnector implements IamConnector {

    @Override
    public Mono<IamRole> getIamRole(String iamRoleName) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> canIamAssume(String iamRoleName, String assumeResourceName) {
        return Mono.empty();
    }
}
