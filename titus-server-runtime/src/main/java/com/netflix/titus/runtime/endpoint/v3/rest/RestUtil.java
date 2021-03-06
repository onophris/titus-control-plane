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

package com.netflix.titus.runtime.endpoint.v3.rest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;

import com.netflix.titus.common.util.CollectionsExt;
import com.netflix.titus.common.util.StringExt;
import com.netflix.titus.grpc.protogen.Page;

import static com.netflix.titus.runtime.endpoint.v3.rest.RestConstants.*;

public final class RestUtil {

    public static Page createPage(MultivaluedMap<String, String> map) {
        Page.Builder pageBuilder = Page.newBuilder();
        pageBuilder.setPageNumber(Integer.parseInt(getFirstOrDefault(map, PAGE_QUERY_KEY, "0")));
        pageBuilder.setPageSize(Integer.parseInt(getFirstOrDefault(map, PAGE_SIZE_QUERY_KEY, "10")));
        pageBuilder.setCursor(getFirstOrDefault(map, CURSOR_QUERY_KEY, ""));
        return pageBuilder.build();
    }

    private static String getFirstOrDefault(MultivaluedMap<String, String> map, String key, String defaultValue) {
        String first = map.getFirst(key);
        if (first == null) {
            return defaultValue;
        }
        return first;
    }

    public static Map<String, String> getFilteringCriteria(MultivaluedMap<String, String> map) {
        Map<String, String> filterCriteria = new HashMap<>();
        map.keySet()
                .stream()
                .filter(e -> !IGNORED_QUERY_PARAMS.contains(e.toLowerCase()))
                .forEach(e -> {
                    String first = map.getFirst(e);
                    if (first != null) {
                        filterCriteria.put(e, first);
                    }
                });
        return filterCriteria;
    }

    public static List<String> getFieldsParameter(MultivaluedMap<String, String> queryParameters) {
        List<String> fields = queryParameters.get(FIELDS_QUERY_KEY);
        if (CollectionsExt.isNullOrEmpty(fields)) {
            return Collections.emptyList();
        }
        return fields.stream().flatMap(f -> StringExt.splitByComma(f).stream()).collect(Collectors.toList());
    }
}
