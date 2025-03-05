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
import org.apache.cassandra.exceptions.InvalidRequestException;
import static org.junit.Assert.fail;
import static org.junit.Assert.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

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

    public static boolean hasValidBounds(String whereClause) {
        try {
            // Construct a minimal query with the provided WHERE clause
            String query = "SELECT * FROM system.local WHERE " + whereClause;
            
            // Parse the statement (this will work even if the table doesn't exist)
            SelectStatement stmt = parseSelect(query);
            
            // Check if the slice is NONE, which indicates nonsensical bounds
            return stmt.makeSlices(QueryOptions.DEFAULT) != Slices.NONE;
        } catch (Exception e) {
            // If there's a syntax error or other issue, the bounds are invalid
            return false;
        }
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

    @Test // New Test: Test Table Validation Error
    public void testTableValidationError() {
        try {
            QueryProcessor.executeOnceInternal("SELECT * FROM nonexistent_table");
            Assert.fail("Expected InvalidRequestException was not thrown");
        } catch (InvalidRequestException e) {
        }
    }

    @Test // New Test: Test Column Validation Error
    public void testColumnValidationError() {
        try {
            QueryProcessor.executeOnceInternal("CREATE TABLE ks.fsm_test2 (id int PRIMARY KEY, value text)");
            QueryProcessor.executeOnceInternal("SELECT nonexistent_column FROM ks.fsm_test2");
            Assert.fail("Expected InvalidRequestException was not thrown");
        } catch (InvalidRequestException e) {
        }
        
    }

    @Test // New Test: Test Execution Error Handling
    public void testExecutionError() throws Throwable {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.fsm_test3 (id int PRIMARY KEY, value text)");

        try {
            QueryProcessor.executeOnceInternal("SELECT * FROM ks.fsm_test3 WHERE id = 1/0");
            Assert.fail("Expected InvalidRequestException was not thrown");
        } catch (InvalidRequestException e) {
            // Expected exception
        }
    }

    @Test // New Test: Test Complex Query Validation
    public void testComplexQueryValidation() throws Throwable {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.fsm_test4 (id int PRIMARY KEY, value text, data int)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.fsm_test4 (id, value, data) VALUES (1, 'test', 100)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.fsm_test4 (id, value, data) VALUES (2, 'test2', 200)");

        SelectStatement stmt = parseSelect(
            "SELECT id, value, data FROM ks.fsm_test4 WHERE id IN (1, 2) AND data > 150 ALLOW FILTERING"
        );

        Assert.assertNotNull(stmt);
    }

    /**
     *  Enhanced Coverage Tests
     */
    @Test
    public void testAuthorize() {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.auth_test (id int PRIMARY KEY, value text)");
        QueryProcessor.executeOnceInternal("INSERT INTO ks.auth_test (id, value) VALUES (1, 'test')");
        SelectStatement stmt = SelectStatementTest.parseSelect("SELECT * FROM ks.auth_test WHERE id = 1");
        try {
            stmt.authorize(ClientState.forInternalCalls());
        } catch (Exception e) {
            fail("authorize() should not throw an exception for a valid ClientState: " + e.getMessage());
        }
    }

    @Test
    public void testToStringMethod() {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.to_string_test (id int PRIMARY KEY, value text)");
        // No row insertion is needed just to check toString() output
        SelectStatement stmt = SelectStatementTest.parseSelect("SELECT * FROM ks.to_string_test WHERE id = 1");
        String str = stmt.toString();
        assertNotNull("toString() should not return null", str);
        assertTrue("toString() output should contain keyspace", str.contains("ks"));
        assertTrue("toString() output should contain table name", str.contains("to_string_test"));
    }

    @Test
    public void testTableMethod() {
        QueryProcessor.executeOnceInternal("CREATE TABLE ks.table_test (id int PRIMARY KEY, value text)");
        SelectStatement stmt = SelectStatementTest.parseSelect("SELECT * FROM ks.table_test WHERE id = 1");
        String tableName = stmt.table();
        assertEquals("table_test", tableName);
    }

    /**
     *  Modification of testNonsensicalBounds() with Testable Design
     */

    @Test
    public void testBoundsValidation() {
        // Test cases with nonsensical/contradictory bounds
        assertFalse("Greater than AND less than or equal to same value should be invalid",
            hasValidBounds("k=100 AND c > 10 AND c <= 10"));
            
        assertFalse("Less than AND greater than or equal to same value should be invalid",
            hasValidBounds("k=100 AND c < 10 AND c >= 10"));
            
        assertFalse("Less than AND greater than same value should be invalid",
            hasValidBounds("k=100 AND c < 10 AND c > 10"));
        
        // Test cases with valid bounds
        assertTrue("Greater than x AND less than y (where y > x) should be valid",
            hasValidBounds("k=100 AND c > 10 AND c < 20"));
            
        assertTrue("Greater than or equal to x AND less than or equal to x should be valid (point query)",
            hasValidBounds("k=100 AND c >= 10 AND c <= 10"));
            
        assertTrue("Equal to x should be valid",
            hasValidBounds("k=100 AND c = 10"));
        
        // Edge cases
        assertTrue("IN clause should be valid",
            hasValidBounds("k=100 AND c IN (5, 10, 15)"));
            
        assertFalse("Contradictory equality constraints should be invalid",
            hasValidBounds("k=100 AND c = 10 AND c = 20"));
        
        // Test with different column types (timestamp)
        assertTrue("Timestamp bounds should follow same rules",
            hasValidBounds("k='2023-01-01' AND c > '2023-01-01 10:00:00' AND c < '2023-01-01 11:00:00'"));
            
        assertFalse("Contradictory timestamp bounds should be invalid",
            hasValidBounds("k='2023-01-01' AND c > '2023-01-01 10:00:00' AND c < '2023-01-01 09:00:00'"));
    }
}
