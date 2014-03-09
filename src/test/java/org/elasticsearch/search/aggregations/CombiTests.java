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

package org.elasticsearch.search.aggregations;

import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.IntIntOpenHashMap;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.missing.Missing;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Collection;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 *
 */
public class CombiTests extends ElasticsearchIntegrationTest {


    @Override
    public Settings indexSettings() {
        return ImmutableSettings.builder()
                .put("index.number_of_shards", between(1, 5))
                .put("index.number_of_replicas", between(0, 1))
                .build();
    }

    /**
     * Making sure that if there are multiple aggregations, working on the same field, yet require different
     * value source type, they can all still work. It used to fail as we used to cache the ValueSource by the
     * field name. If the cached value source was of type "bytes" and another aggregation on the field required to see
     * it as "numeric", it didn't work. Now we cache the Value Sources by a custom key (field name + ValueSource type)
     * so there's no conflict there.
     */
    @Test
    public void multipleAggs_OnSameField_WithDifferentRequiredValueSourceType() throws Exception {

        createIndex("idx");
        IndexRequestBuilder[] builders = new IndexRequestBuilder[randomInt(30)];
        IntIntMap values = new IntIntOpenHashMap();
        long missingValues = 0;
        for (int i = 0; i < builders.length; i++) {
            String name = "name_" + randomIntBetween(1, 10);
            if (rarely()) {
                missingValues++;
                builders[i] = client().prepareIndex("idx", "type").setSource(jsonBuilder()
                        .startObject()
                        .field("name", name)
                        .endObject());
            } else {
                int value = randomIntBetween(1, 10);
                values.put(value, values.getOrDefault(value, 0) + 1);
                builders[i] = client().prepareIndex("idx", "type").setSource(jsonBuilder()
                        .startObject()
                        .field("name", name)
                        .field("value", value)
                        .endObject());
            }
        }
        indexRandom(true, builders);
        ensureSearchable();


        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(missing("missing_values").field("value"))
                .addAggregation(terms("values").field("value"))
                .execute().actionGet();

        assertSearchResponse(response);

        Aggregations aggs = response.getAggregations();

        Missing missing = aggs.get("missing_values");
        assertNotNull(missing);
        assertThat(missing.getDocCount(), equalTo(missingValues));

        Terms terms = aggs.get("values");
        assertNotNull(terms);
        Collection<Terms.Bucket> buckets = terms.getBuckets();
        assertThat(buckets.size(), equalTo(values.size()));
        for (Terms.Bucket bucket : buckets) {
            values.remove(bucket.getKeyAsNumber().intValue());
        }
        assertTrue(values.isEmpty());
    }


    /**
     * Some top aggs (eg. date_/histogram) that are executed on unmapped fields, will generate an estimate count of buckets - zero.
     * when the sub aggregator is then created, it will take this estimation into account. This used to cause
     * and an ArrayIndexOutOfBoundsException...
     */
    @Test
    public void subAggregationForTopAggregationOnUnmappedField() throws Exception {

        prepareCreate("idx").addMapping("type", jsonBuilder()
                .startObject()
                .startObject("type").startObject("properties")
                    .startObject("name").field("type", "string").endObject()
                    .startObject("value").field("type", "integer").endObject()
                .endObject().endObject()
                .endObject()).execute().actionGet();

        ensureSearchable("idx");

        SearchResponse searchResponse = client().prepareSearch("idx")
                .addAggregation(histogram("values").field("value1").interval(1)
                        .subAggregation(terms("names").field("name")))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), Matchers.equalTo(0l));
        Histogram values = searchResponse.getAggregations().get("values");
        assertThat(values, notNullValue());
        assertThat(values.getBuckets().isEmpty(), is(true));
    }
}