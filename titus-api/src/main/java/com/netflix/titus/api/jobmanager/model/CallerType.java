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

package com.netflix.titus.api.jobmanager.model;

import com.netflix.titus.common.util.StringExt;

public enum CallerType {

    Application,
    Unknown,
    User;

    public static CallerType parseCallerType(String callerId, String callerType) {
        String trimmedType = StringExt.safeTrim(callerType);
        if (trimmedType.isEmpty()) {
            return callerId.contains("@") ? User : Application;
        }

        try {
            return StringExt.parseEnumIgnoreCase(trimmedType, CallerType.class);
        } catch (Exception e) {
            return CallerType.Unknown;
        }
    }
}
