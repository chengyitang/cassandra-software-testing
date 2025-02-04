/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.cql3.statements;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.SchemaLoader;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.db.Slices;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.service.ClientState;

public class SelectStatementTest
{

    private static final String KEYSPACE = "ks";

    @BeforeClass
    public static void setupClass()
    {
        DatabaseDescriptor.daemonInitialization();
        SchemaLoader.prepareServer();
        SchemaLoader.createKeyspace(KEYSPACE, KeyspaceParams.simple(1));
    }

    private static SelectStatement parseSelect(String query)
    {
        CQLStatement stmt = QueryProcessor.parseStatement(query).prepare(ClientState.forInternalCalls());
        assert stmt instanceof SelectStatement;
        return (SelectStatement) stmt;
    }

    @Test
    public void testNonsensicalBounds()
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.tbl (k int, c int, v int, primary key (k, c))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.tbl (k, c, v) VALUES (100, 10, 0)");
        Assert.assertEquals(Slices.NONE, parseSelect("SELECT * FROM ks.tbl WHERE k=100 AND c > 10 AND c <= 10").makeSlices(QueryOptions.DEFAULT));
        Assert.assertEquals(Slices.NONE, parseSelect("SELECT * FROM ks.tbl WHERE k=100 AND c < 10 AND c >= 10").makeSlices(QueryOptions.DEFAULT));
        Assert.assertEquals(Slices.NONE, parseSelect("SELECT * FROM ks.tbl WHERE k=100 AND c < 10 AND c > 10").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Integer Parition Key Boundaries
    public void testIntegerPartitionBoundaries() 
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.int_boundaries (k int, v text, PRIMARY KEY (k))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.int_boundaries (k, v) VALUES (?, 'min')", Integer.MIN_VALUE);  // -2147483648
        QueryProcessor.executeOnceInternal("INSERT INTO ks.int_boundaries (k, v) VALUES (0, 'zero')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.int_boundaries (k, v) VALUES (?, 'max')", Integer.MAX_VALUE);  // 2147483647
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.int_boundaries WHERE k = " + Integer.MIN_VALUE).makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.int_boundaries WHERE k = " + Integer.MAX_VALUE).makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Null Values Handling
    public void testNullValuesHandling() 
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.null_test (id int PRIMARY KEY, value text)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.null_test (id, value) VALUES (1, null)");
        Assert.assertEquals(Slices.NONE, parseSelect("SELECT * FROM ks.null_test WHERE value = 'nonexistent'").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test String Partition Key Boundaries
    public void testStringPartitionBoundaries() 
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.string_boundaries (k text PRIMARY KEY, v text)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.string_boundaries (k, v) VALUES ('', 'empty')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.string_boundaries (k, v) VALUES ('a', 'a')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.string_boundaries (k, v) VALUES ('z', 'z')");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.string_boundaries WHERE k = ''").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.string_boundaries WHERE k = 'a'").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.string_boundaries WHERE k = 'z'").makeSlices(QueryOptions.DEFAULT));
    }
}
