/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.warmer.get;

import com.google.common.collect.ObjectArrays;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.info.ClusterInfoRequestBuilder;
import org.elasticsearch.client.IndicesAdminClient;

/**
 */
public class GetWarmersRequestBuilder extends ClusterInfoRequestBuilder<GetWarmersRequest, GetWarmersResponse, GetWarmersRequestBuilder> {

    public GetWarmersRequestBuilder(IndicesAdminClient client, String... indices) {
        super(client, new GetWarmersRequest().indices(indices));
    }

    public GetWarmersRequestBuilder setWarmers(String... warmers) {
        request.warmers(warmers);
        return this;
    }

    public GetWarmersRequestBuilder addWarmers(String... warmers) {
        request.warmers(ObjectArrays.concat(request.warmers(), warmers, String.class));
        return this;
    }

    @Override
    protected void doExecute(ActionListener<GetWarmersResponse> listener) {
        client.getWarmers(request, listener);
    }
}
