/*
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
package com.facebook.presto.accumulo;

import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.tests.AbstractTestDistributedQueries;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.facebook.presto.accumulo.AccumuloQueryRunner.createAccumuloQueryRunner;
import static com.facebook.presto.accumulo.AccumuloQueryRunner.dropTpchTables;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Accumulo requires a unique identifier for the rows.  Any row that has a duplicate
 * row ID is effectively an update, overwriting existing values of the row with
 * whatever the new values are. For the lineitem and partsupp tables, there is no
 * unique identifier, so a generated UUID is used in order to prevent overwriting
 * rows of data. This is the same for any test cases that were creating tables
 * with duplicate rows, so some test cases are overriden from the base class
 * and slightly modified to add an additional UUID column.
 */
public class TestAccumuloDistributedQueries
        extends AbstractTestDistributedQueries
{
    public TestAccumuloDistributedQueries()
            throws Exception
    {
        super(createAccumuloQueryRunner(ImmutableMap.of(), true));
    }

    @AfterClass
    public void cleanup()
    {
        dropTpchTables(queryRunner, getSession());
    }

    @Override
    public void testAddColumn()
            throws Exception
    {
        // Adding columns via SQL are not supported until adding columns with comments are supported
    }

    @Override
    public void testCompatibleTypeChangeForView()
            throws Exception
    {
        // Override because base class error: Must have at least one non-row ID column
        assertUpdate("CREATE TABLE test_table_1 AS SELECT 'abcdefg' a, 1 b", 1);
        assertUpdate("CREATE VIEW test_view_1 AS SELECT a FROM test_table_1");

        assertQuery("SELECT a FROM test_view_1", "VALUES 'abcdefg'");

        // replace table with a version that's implicitly coercible to the previous one
        assertUpdate("DROP TABLE test_table_1");
        assertUpdate("CREATE TABLE test_table_1 AS SELECT 'abc' a, 1 b", 1);

        assertQuery("SELECT a FROM test_view_1", "VALUES 'abc'");

        assertUpdate("DROP VIEW test_view_1");
        assertUpdate("DROP TABLE test_table_1");
    }

    @Override
    public void testCompatibleTypeChangeForView2()
            throws Exception
    {
        assertUpdate("CREATE TABLE test_table_2 AS SELECT BIGINT '1' v, 2 w", 1);
        assertUpdate("CREATE VIEW test_view_2 AS SELECT * FROM test_table_2");

        assertQuery("SELECT v FROM test_view_2", "VALUES 1");

        // replace table with a version that's implicitly coercible to the previous one
        assertUpdate("DROP TABLE test_table_2");
        assertUpdate("CREATE TABLE test_table_2 AS SELECT INTEGER '1' v, 2 w", 1);

        assertQuery("SELECT v FROM test_view_2 WHERE v = 1", "VALUES 1");

        assertUpdate("DROP VIEW test_view_2");
        assertUpdate("DROP TABLE test_table_2");
    }

    @Override
    public void testCreateTableAsSelect()
            throws Exception
    {
        assertUpdate("CREATE TABLE test_create_table_as_if_not_exists (a bigint, b double)");
        assertTrue(queryRunner.tableExists(getSession(), "test_create_table_as_if_not_exists"));
        assertTableColumnNames("test_create_table_as_if_not_exists", "a", "b");

        MaterializedResult materializedRows = computeActual("CREATE TABLE IF NOT EXISTS test_create_table_as_if_not_exists AS SELECT UUID() AS uuid, orderkey, discount FROM lineitem");
        assertEquals(materializedRows.getRowCount(), 0);
        assertTrue(queryRunner.tableExists(getSession(), "test_create_table_as_if_not_exists"));
        assertTableColumnNames("test_create_table_as_if_not_exists", "a", "b");

        assertUpdate("DROP TABLE test_create_table_as_if_not_exists");
        assertFalse(queryRunner.tableExists(getSession(), "test_create_table_as_if_not_exists"));

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                "test_select",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders",
//                "SELECT count(*) FROM orders");

        this.assertCreateTableAsSelect(
                "test_group",
                "SELECT orderstatus, sum(totalprice) x FROM orders GROUP BY orderstatus",
                "SELECT count(DISTINCT orderstatus) FROM orders");

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                "test_join",
//                "SELECT UUID() AS uuid, count(*) x FROM lineitem JOIN orders ON lineitem.orderkey = orders.orderkey",
//                "SELECT 1");

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                "test_limit",
//                "SELECT UUID() AS uuid, orderkey FROM orders ORDER BY orderkey LIMIT 10",
//                "SELECT 10");

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                "test_unicode",
//                "SELECT UUID() AS uuid, '\u2603' unicode",
//                "SELECT 1");

        this.assertCreateTableAsSelect(
                "test_with_data",
                "SELECT * FROM orders WITH DATA",
                "SELECT * FROM orders",
                "SELECT count(*) FROM orders");

        this.assertCreateTableAsSelect(
                "test_with_no_data",
                "SELECT * FROM orders WITH NO DATA",
                "SELECT * FROM orders LIMIT 0",
                "SELECT 0");

//        No sample tables
//        this.assertCreateTableAsSelect(
//                "test_sampled",
//                "SELECT orderkey FROM tpch_sampled.tiny.orders ORDER BY orderkey LIMIT 10",
//                "SELECT orderkey FROM orders ORDER BY orderkey LIMIT 10",
//                "SELECT 10");

        // Tests for CREATE TABLE with UNION ALL: exercises PushTableWriteThroughUnion optimizer

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                "test_union_all",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders WHERE orderkey % 2 = 0 UNION ALL " +
//                        "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders WHERE orderkey % 2 = 1",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders",
//                "SELECT count(*) FROM orders");

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                getSession().withSystemProperty("redistribute_writes", "true"),
//                "test_union_all",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders UNION ALL " +
//                        "SELECT UUID() AS uuid, DATE '2000-01-01', 1234567890, 1.23",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders UNION ALL " +
//                        "SELECT UUID() AS uuid, DATE '2000-01-01', 1234567890, 1.23",
//                "SELECT count(*) + 1 FROM orders");

//        Function "UUID" not found
//        this.assertCreateTableAsSelect(
//                getSession().withSystemProperty("redistribute_writes", "false"),
//                "test_union_all",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders UNION ALL " +
//                        "SELECT UUID() AS uuid, DATE '2000-01-01', 1234567890, 1.23",
//                "SELECT UUID() AS uuid, orderdate, orderkey, totalprice FROM orders UNION ALL " +
//                        "SELECT UUID() AS uuid, DATE '2000-01-01', 1234567890, 1.23",
//                "SELECT count(*) + 1 FROM orders");
    }

    @Override
    public void testDelete()
            throws Exception
    {
        // Deletes are not supported by the connector
    }

    @Override
    public void testInsert()
            throws Exception
    {
        @Language("SQL") String query = "SELECT UUID() AS uuid, orderdate, orderkey FROM orders";

        assertUpdate("CREATE TABLE test_insert AS " + query + " WITH NO DATA", 0);
        assertQuery("SELECT count(*) FROM test_insert", "SELECT 0");

        assertUpdate("INSERT INTO test_insert " + query, "SELECT count(*) FROM orders");

        assertQuery("SELECT orderdate, orderkey FROM test_insert", "SELECT orderdate, orderkey FROM orders");
        // Override because base class error: Cannot insert null row ID
        assertUpdate("INSERT INTO test_insert (uuid, orderkey) VALUES ('000000', -1)", 1);
        assertUpdate("INSERT INTO test_insert (uuid, orderdate) VALUES ('000001', DATE '2001-01-01')", 1);
        assertUpdate("INSERT INTO test_insert (uuid, orderkey, orderdate) VALUES ('000002', -2, DATE '2001-01-02')", 1);
        assertUpdate("INSERT INTO test_insert (uuid, orderdate, orderkey) VALUES ('000003', DATE '2001-01-03', -3)", 1);

        assertQuery("SELECT orderdate, orderkey FROM test_insert",
                "SELECT orderdate, orderkey FROM orders"
                        + " UNION ALL SELECT null, -1"
                        + " UNION ALL SELECT DATE '2001-01-01', null"
                        + " UNION ALL SELECT DATE '2001-01-02', -2"
                        + " UNION ALL SELECT DATE '2001-01-03', -3");

        // UNION query produces columns in the opposite order
        // of how they are declared in the table schema
        assertUpdate(
                "INSERT INTO test_insert (uuid, orderkey, orderdate) " +
                        "SELECT UUID() AS uuid, orderkey, orderdate FROM orders " +
                        "UNION ALL " +
                        "SELECT UUID() AS uuid, orderkey, orderdate FROM orders",
                "SELECT 2 * count(*) FROM orders");

        assertUpdate("DROP TABLE test_insert");
    }

    @Test
    public void testInsertDuplicateRows()
            throws Exception
    {
        // This test case tests the Accumulo connectors override capabilities
        // When a row is inserted into a table where a row with the same row ID already exists,
        // the cells of the existing row are overwritten with the new values
        try {
            assertUpdate("CREATE TABLE test_insert_duplicate AS SELECT 1 a, 2 b, '3' c", 1);
            assertQuery("SELECT a, b, c FROM test_insert_duplicate", "SELECT 1, 2, '3'");
            assertUpdate("INSERT INTO test_insert_duplicate (a, c) VALUES (1, '4')", 1);
            assertUpdate("INSERT INTO test_insert_duplicate (a, b) VALUES (1, 3)", 1);
            assertQuery("SELECT a, b, c FROM test_insert_duplicate", "SELECT 1, 3, '4'");
        }
        finally {
            assertUpdate("DROP TABLE test_insert_duplicate");
        }
    }

    @Override
    public void testRenameColumn()
            throws Exception
    {
        // Override because base class error: Must have at least one non-row ID column
        assertUpdate("CREATE TABLE test_rename_column AS SELECT BIGINT '123' x, 456 a", 1);

        assertUpdate("ALTER TABLE test_rename_column RENAME COLUMN x TO y");
        MaterializedResult materializedRows = computeActual("SELECT y FROM test_rename_column");
        assertEquals(getOnlyElement(materializedRows.getMaterializedRows()).getField(0), 123L);

        assertUpdate("ALTER TABLE test_rename_column RENAME COLUMN y TO Z");
        materializedRows = computeActual("SELECT z FROM test_rename_column");
        assertEquals(getOnlyElement(materializedRows.getMaterializedRows()).getField(0), 123L);

        assertUpdate("DROP TABLE test_rename_column");
        assertFalse(queryRunner.tableExists(getSession(), "test_rename_column"));
    }

    @Override
    public void testRenameTable()
            throws Exception
    {
        // Override because base class error: Must have at least one non-row ID column
        // Casting to BIGINT -- mvn test: integers... intellij: longs?  weird
        assertUpdate("CREATE TABLE test_rename AS SELECT CAST(123 AS BIGINT) x, 456 a", 1);

        assertUpdate("ALTER TABLE test_rename RENAME TO test_rename_new");
        MaterializedResult materializedRows = computeActual("SELECT x FROM test_rename_new");
        assertEquals(getOnlyElement(materializedRows.getMaterializedRows()).getField(0), 123L);

        // provide new table name in uppercase
        assertUpdate("ALTER TABLE test_rename_new RENAME TO TEST_RENAME");
        materializedRows = computeActual("SELECT x FROM test_rename");
        assertEquals(getOnlyElement(materializedRows.getMaterializedRows()).getField(0), 123L);

        assertUpdate("DROP TABLE test_rename");

        assertFalse(queryRunner.tableExists(getSession(), "test_rename"));
        assertFalse(queryRunner.tableExists(getSession(), "test_rename_new"));
    }

    @Override
    public void testBuildFilteredLeftJoin()
            throws Exception
    {
        // Override because of extra UUID column in lineitem table, cannot SELECT *
        assertQuery("SELECT "
                + "lineitem.orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, lineitem.comment "
                + "FROM lineitem LEFT JOIN (SELECT * FROM orders WHERE orderkey % 2 = 0) a ON lineitem.orderkey = a.orderkey");
    }

    @Override
    @Test
    public void testJoinWithAlias()
            throws Exception
    {
        // Override because of extra UUID column in lineitem table, cannot SELECT *
        // Cannot munge test to pass due to aliased data set 'x' containing duplicate orderkey and comment columns
    }

    @Override
    public void testProbeFilteredLeftJoin()
            throws Exception
    {
        // Override because of extra UUID column in lineitem table, cannot SELECT *
        assertQuery("SELECT "
                + "a.orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, a.comment "
                + "FROM (SELECT * FROM lineitem WHERE orderkey % 2 = 0) a LEFT JOIN orders ON a.orderkey = orders.orderkey");
    }

    @Override
    @Test
    public void testJoinWithDuplicateRelations()
            throws Exception
    {
        // Override because of extra UUID column in lineitem table, cannot SELECT *
        // Cannot munge test to pass due to aliased data sets 'x' containing duplicate orderkey and comment columns
    }

    @Override
    public void testScalarSubquery()
            throws Exception
    {
        // Override because of extra UUID column in lineitem table, cannot SELECT *

        // nested
        assertQuery("SELECT (SELECT (SELECT (SELECT 1)))");

        // aggregation
        assertQuery("SELECT "
                + "orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, comment "
                + "FROM lineitem WHERE orderkey = \n"
                + "(SELECT max(orderkey) FROM orders)");

        // no output
        assertQuery("SELECT "
                + "orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, comment "
                + "FROM lineitem WHERE orderkey = \n"
                + "(SELECT orderkey FROM orders WHERE 0=1)");

        // no output matching with null test
        assertQuery("SELECT "
                + "orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, comment "
                + "FROM lineitem WHERE \n"
                + "(SELECT orderkey FROM orders WHERE 0=1) "
                + "is null");
        assertQuery("SELECT "
                + "orderkey, partkey, suppkey, linenumber, quantity, "
                + "extendedprice, discount, tax, returnflag, linestatus, "
                + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, comment "
                + "FROM lineitem WHERE \n"
                + "(SELECT orderkey FROM orders WHERE 0=1) "
                + "is not null");

        // subquery results and in in-predicate
        assertQuery("SELECT (SELECT 1) IN (1, 2, 3)");
        assertQuery("SELECT (SELECT 1) IN (   2, 3)");

        // multiple subqueries
        assertQuery("SELECT (SELECT 1) = (SELECT 3)");
        assertQuery("SELECT (SELECT 1) < (SELECT 3)");
        assertQuery("SELECT COUNT(*) FROM lineitem WHERE " +
                "(SELECT min(orderkey) FROM orders)" +
                "<" +
                "(SELECT max(orderkey) FROM orders)");

        // distinct
        assertQuery("SELECT DISTINCT orderkey FROM lineitem " +
                "WHERE orderkey BETWEEN" +
                "   (SELECT avg(orderkey) FROM orders) - 10 " +
                "   AND" +
                "   (SELECT avg(orderkey) FROM orders) + 10");

        // subqueries with joins
        for (String joinType : ImmutableList.of("INNER", "LEFT OUTER")) {
            assertQuery("SELECT l.orderkey, COUNT(*) " +
                    "FROM lineitem l " + joinType + " JOIN orders o ON l.orderkey = o.orderkey " +
                    "WHERE l.orderkey BETWEEN" +
                    "   (SELECT avg(orderkey) FROM orders) - 10 " +
                    "   AND" +
                    "   (SELECT avg(orderkey) FROM orders) + 10 " +
                    "GROUP BY l.orderkey");
        }

        // subqueries with ORDER BY
        assertQuery("SELECT orderkey, totalprice FROM orders ORDER BY (SELECT 2)");

        // subquery returns multiple rows
        String multipleRowsErrorMsg = "Scalar sub-query has returned multiple rows";
        assertQueryFails("SELECT "
                        + "orderkey, partkey, suppkey, linenumber, quantity, "
                        + "extendedprice, discount, tax, returnflag, linestatus, "
                        + "shipdate, commitdate, receiptdate, shipinstruct, shipmode, comment "
                        + "FROM lineitem WHERE orderkey = (\n"
                        + "SELECT orderkey FROM orders ORDER BY totalprice)",
                multipleRowsErrorMsg);
        assertQueryFails("SELECT orderkey, totalprice FROM orders ORDER BY (VALUES 1, 2)",
                multipleRowsErrorMsg);

        // exposes a bug in optimize hash generation because EnforceSingleNode does not
        // support more than one column from the underlying query
        assertQuery("SELECT custkey, (SELECT DISTINCT custkey FROM orders ORDER BY custkey LIMIT 1) FROM orders");
    }

    @Override
    public void testShowColumns()
            throws Exception
    {
        // Override base class because table descriptions for Accumulo connector include comments
        MaterializedResult actual = computeActual("SHOW COLUMNS FROM orders");

        assertEquals("orderkey", actual.getMaterializedRows().get(0).getField(0));
        assertEquals("bigint", actual.getMaterializedRows().get(0).getField(1));
        assertEquals("custkey", actual.getMaterializedRows().get(1).getField(0));
        assertEquals("bigint", actual.getMaterializedRows().get(1).getField(1));
        assertEquals("orderstatus", actual.getMaterializedRows().get(2).getField(0));
        assertEquals("varchar", actual.getMaterializedRows().get(2).getField(1));
        assertEquals("totalprice", actual.getMaterializedRows().get(3).getField(0));
        assertEquals("double", actual.getMaterializedRows().get(3).getField(1));
        assertEquals("orderdate", actual.getMaterializedRows().get(4).getField(0));
        assertEquals("date", actual.getMaterializedRows().get(4).getField(1));
        assertEquals("orderpriority", actual.getMaterializedRows().get(5).getField(0));
        assertEquals("varchar", actual.getMaterializedRows().get(5).getField(1));
        assertEquals("clerk", actual.getMaterializedRows().get(6).getField(0));
        assertEquals("varchar", actual.getMaterializedRows().get(6).getField(1));
        assertEquals("shippriority", actual.getMaterializedRows().get(7).getField(0));
        assertEquals("integer", actual.getMaterializedRows().get(7).getField(1));
        assertEquals("comment", actual.getMaterializedRows().get(8).getField(0));
        assertEquals("varchar", actual.getMaterializedRows().get(8).getField(1));
    }
}
