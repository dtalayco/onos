/*
 * Copyright 2015 Open Networking Laboratory
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

package org.onosproject.ui.table;

import org.junit.Test;
import org.onosproject.ui.table.TableModel.SortDir;
import org.onosproject.ui.table.cell.DefaultCellFormatter;
import org.onosproject.ui.table.cell.HexFormatter;
import org.onosproject.ui.table.cell.IntComparator;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TableModel}.
 */
public class TableModelTest {

    private static final String UNEX_SORT = "unexpected sort: index ";

    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private static final String ZOO = "zoo";

    private static class ParenFormatter implements CellFormatter {
        @Override
        public String format(Object value) {
            return "(" + value + ")";
        }
    }

    private TableModel tm;
    private TableModel.Row[] rows;
    private TableModel.Row row;
    private CellFormatter fmt;

    @Test(expected = NullPointerException.class)
    public void guardAgainstNull() {
        tm = new TableModel(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void guardAgainstEmpty() {
        tm = new TableModel();
    }

    @Test(expected = IllegalArgumentException.class)
    public void guardAgainstDuplicateCols() {
        tm = new TableModel(FOO, BAR, FOO);
    }

    @Test
    public void basic() {
        tm = new TableModel(FOO, BAR);
        assertEquals("column count", 2, tm.columnCount());
        assertEquals("row count", 0, tm.rowCount());
    }

    @Test
    public void defaultFormatter() {
        tm = new TableModel(FOO);
        fmt = tm.getFormatter(FOO);
        assertTrue("Wrong formatter", fmt instanceof DefaultCellFormatter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void formatterBadColumn() {
        tm = new TableModel(FOO);
        fmt = tm.getFormatter(BAR);
    }

    @Test
    public void altFormatter() {
        tm = new TableModel(FOO, BAR);
        tm.setFormatter(BAR, new ParenFormatter());

        fmt = tm.getFormatter(FOO);
        assertTrue("Wrong formatter", fmt instanceof DefaultCellFormatter);
        assertEquals("Wrong result", "2", fmt.format(2));

        fmt = tm.getFormatter(BAR);
        assertTrue("Wrong formatter", fmt instanceof ParenFormatter);
        assertEquals("Wrong result", "(2)", fmt.format(2));
    }

    @Test
    public void emptyRow() {
        tm = new TableModel(FOO, BAR);
        tm.addRow();
        assertEquals("bad row count", 1, tm.rowCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rowBadColumn() {
        tm = new TableModel(FOO, BAR);
        tm.addRow().cell(ZOO, 2);
    }

    @Test(expected = NullPointerException.class)
    public void rowNullValue() {
        tm = new TableModel(FOO, BAR);
        tm.addRow().cell(FOO, null);
    }

    @Test
    public void simpleRow() {
        tm = new TableModel(FOO, BAR);
        tm.addRow().cell(FOO, 3).cell(BAR, true);
        assertEquals("bad row count", 1, tm.rowCount());
        row = tm.getRows()[0];
        assertEquals("bad cell", 3, row.get(FOO));
        assertEquals("bad cell", true, row.get(BAR));
    }


    private static final String ONE = "one";
    private static final String TWO = "two";
    private static final String THREE = "three";
    private static final String FOUR = "four";
    private static final String ELEVEN = "eleven";
    private static final String TWELVE = "twelve";
    private static final String TWENTY = "twenty";
    private static final String THIRTY = "thirty";

    private static final String[] NAMES = {
            FOUR,
            THREE,
            TWO,
            ONE,
            ELEVEN,
            TWELVE,
            THIRTY,
            TWENTY,
    };
    private static final String[] SORTED_NAMES = {
            ELEVEN,
            FOUR,
            ONE,
            THIRTY,
            THREE,
            TWELVE,
            TWENTY,
            TWO,
    };

    private static final int[] NUMBERS = {
        4, 3, 2, 1, 11, 12, 30, 20
    };

    private static final int[] SORTED_NUMBERS = {
        1, 2, 3, 4, 11, 12, 20, 30
    };

    private static final String[] SORTED_HEX = {
        "0x1", "0x2", "0x3", "0x4", "0xb", "0xc", "0x14", "0x1e"
    };

    @Test
    public void verifyTestData() {
        // not a unit test per se, but will fail if we don't keep
        // the three test arrays in sync
        int nalen = NAMES.length;
        int snlen = SORTED_NAMES.length;
        int nulen = NUMBERS.length;

        if (nalen != snlen || nalen != nulen) {
            fail("test data array size discrepancy");
        }
    }

    private void initUnsortedTable() {
        tm = new TableModel(FOO, BAR);
        for (int i = 0; i < NAMES.length; i++) {
            tm.addRow().cell(FOO, NAMES[i]).cell(BAR, NUMBERS[i]);
        }
    }

    @Test
    public void tableStringSort() {
        initUnsortedTable();

        // sort by name
        tm.sort(FOO, SortDir.ASC);

        // verify results
        rows = tm.getRows();
        int nr = rows.length;
        assertEquals("row count", NAMES.length, nr);
        for (int i = 0; i < nr; i++) {
            assertEquals(UNEX_SORT + i, SORTED_NAMES[i], rows[i].get(FOO));
        }

        // now the other way
        tm.sort(FOO, SortDir.DESC);

        // verify results
        rows = tm.getRows();
        nr = rows.length;
        assertEquals("row count", NAMES.length, nr);
        for (int i = 0; i < nr; i++) {
            assertEquals(UNEX_SORT + i,
                         SORTED_NAMES[nr - 1 - i], rows[i].get(FOO));
        }
    }

    @Test
    public void tableNumberSort() {
        initUnsortedTable();

        // first, tell the table to use an integer-based comparator
        tm.setComparator(BAR, IntComparator.INSTANCE);

        // sort by number
        tm.sort(BAR, SortDir.ASC);

        // verify results
        rows = tm.getRows();
        int nr = rows.length;
        assertEquals("row count", NUMBERS.length, nr);
        for (int i = 0; i < nr; i++) {
            assertEquals(UNEX_SORT + i, SORTED_NUMBERS[i], rows[i].get(BAR));
        }

        // now the other way
        tm.sort(BAR, SortDir.DESC);

        // verify results
        rows = tm.getRows();
        nr = rows.length;
        assertEquals("row count", NUMBERS.length, nr);
        for (int i = 0; i < nr; i++) {
            assertEquals(UNEX_SORT + i,
                         SORTED_NUMBERS[nr - 1 - i], rows[i].get(BAR));
        }
    }

    @Test
    public void sortAndFormat() {
        initUnsortedTable();

        // set integer-based comparator and hex formatter
        tm.setComparator(BAR, IntComparator.INSTANCE);
        tm.setFormatter(BAR, HexFormatter.INSTANCE);

        // sort by number
        tm.sort(BAR, SortDir.ASC);

        // verify results
        rows = tm.getRows();
        int nr = rows.length;
        assertEquals("row count", SORTED_HEX.length, nr);
        for (int i = 0; i < nr; i++) {
            assertEquals(UNEX_SORT + i, SORTED_HEX[i], rows[i].getAsString(BAR));
        }
    }


    @Test
    public void sortDirAsc() {
        assertEquals("asc sort dir", SortDir.ASC, TableModel.sortDir("asc"));
    }

    @Test
    public void sortDirDesc() {
        assertEquals("desc sort dir", SortDir.DESC, TableModel.sortDir("desc"));
    }

    @Test
    public void sortDirOther() {
        assertEquals("other sort dir", SortDir.ASC, TableModel.sortDir("other"));
    }

    @Test
    public void sortDirNull() {
        assertEquals("null sort dir", SortDir.ASC, TableModel.sortDir(null));
    }

}
