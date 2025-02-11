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
import org.apache.cassandra.exceptions.SyntaxException;
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

    @Test // New Test: Test Composite Partition Key Boundaries
    public void testCompositePartitionKeyBounds() {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.composite_key (k1 int, k2 text, v int, PRIMARY KEY ((k1, k2)))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.composite_key (k1, k2, v) VALUES (1, 'a', 0)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.composite_key (k1, k2, v) VALUES (1, 'b', 0)");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.composite_key WHERE k1 = 1 AND k2 = 'a'").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.composite_key WHERE k1 = 1 AND k2 = 'b'").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Timestamp Boundaries
    public void testTimestampBoundaries() {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.timestamp_bounds (k timestamp PRIMARY KEY, v text)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.timestamp_bounds (k, v) VALUES ('1970-01-01 00:00:00', 'epoch')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.timestamp_bounds (k, v) VALUES ('9999-12-31 23:59:59', 'far_future')");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.timestamp_bounds WHERE k = '1970-01-01 00:00:00'").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.timestamp_bounds WHERE k = '9999-12-31 23:59:59'").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test UUID Partition Key Behavior
    public void testUUIDPartitionKeyBehavior()
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.uuid_test (k uuid, v text, PRIMARY KEY (k))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.uuid_test (k, v) VALUES (uuid(), 'random_uuid')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.uuid_test (k, v) VALUES (uuid(), 'another_uuid')");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.uuid_test").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Boolean Partition Key Behavior
    public void testBooleanPartitionKeyBehavior()
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.boolean_test (k boolean, v text, PRIMARY KEY (k))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.boolean_test (k, v) VALUES (true, 'true_value')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.boolean_test (k, v) VALUES (false, 'false_value')");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.boolean_test WHERE k = true").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.boolean_test WHERE k = false").makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Floating-Point Partition Boundaries
    public void testFloatingPointPartitionBoundaries()
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.float_test (k double, v text, PRIMARY KEY (k))");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.float_test (k, v) VALUES (-1.7976931348623157E308, 'min_double')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.float_test (k, v) VALUES (0.0, 'zero_double')");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.float_test (k, v) VALUES (1.7976931348623157E308, 'max_double')");
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.float_test WHERE k = -1.7976931348623157E308").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.float_test WHERE k = 0.0").makeSlices(QueryOptions.DEFAULT));
        Assert.assertNotNull(parseSelect("SELECT * FROM ks.float_test WHERE k = 1.7976931348623157E308").makeSlices(QueryOptions.DEFAULT));
    }

    /**
     *  Functional State Machine Tests  
     */
    @Test // New Test: Test Successful Query Flow
    public void testSuccessfulQuery() 
    {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.fsm_test (id int PRIMARY KEY, value text)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.fsm_test (id, value) VALUES (1, 'test')");
        SelectStatement stmt = parseSelect("SELECT * FROM ks.fsm_test WHERE id = 1");
        Assert.assertNotNull(stmt.makeSlices(QueryOptions.DEFAULT));
    }

    @Test // New Test: Test Syntax Error
    public void testSyntaxError() 
    {
        try {
            parseSelect("SELECT * test_table");
            Assert.fail("Expected SyntaxException was not thrown");
        } catch (SyntaxException e) {
            // expected to throw SyntaxException
        }
    }
}
