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
package org.onlab.util;

import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Timer;

import static org.junit.Assert.*;
import static org.onlab.junit.TestTools.delay;

/**
 * Tests the operation of the accumulator.
 */
public class AbstractAccumulatorTest {

    private final Timer timer = new Timer();

    @Test
    public void basics() throws Exception {
        TestAccumulator accumulator = new TestAccumulator();
        assertEquals("incorrect timer", timer, accumulator.timer());
        assertEquals("incorrect max events", 5, accumulator.maxItems());
        assertEquals("incorrect max ms", 100, accumulator.maxBatchMillis());
        assertEquals("incorrect idle ms", 70, accumulator.maxIdleMillis());
    }

    @Test
    public void eventTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.add(new TestItem("a"));
        accumulator.add(new TestItem("b"));
        accumulator.add(new TestItem("c"));
        accumulator.add(new TestItem("d"));
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("e"));
        delay(20);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "abcde", accumulator.batch);
    }

    @Ignore("FIXME: timing sensitive test failing randomly.")
    @Test
    public void timeTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.add(new TestItem("a"));
        delay(30);
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("b"));
        delay(30);
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("c"));
        delay(30);
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("d"));
        delay(60);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "abcd", accumulator.batch);
    }

    @Test
    public void idleTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.add(new TestItem("a"));
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("b"));
        delay(80);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "ab", accumulator.batch);
    }

    @Test
    public void readyIdleTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.ready = false;
        accumulator.add(new TestItem("a"));
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("b"));
        delay(80);
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.ready = true;
        delay(80);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "ab", accumulator.batch);
    }

    @Test
    public void readyLongTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.ready = false;
        delay(120);
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.add(new TestItem("a"));
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.ready = true;
        delay(80);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "a", accumulator.batch);
    }

    @Test
    public void readyMaxTrigger() {
        TestAccumulator accumulator = new TestAccumulator();
        accumulator.ready = false;
        accumulator.add(new TestItem("a"));
        accumulator.add(new TestItem("b"));
        accumulator.add(new TestItem("c"));
        accumulator.add(new TestItem("d"));
        accumulator.add(new TestItem("e"));
        accumulator.add(new TestItem("f"));
        assertTrue("should not have fired yet", accumulator.batch.isEmpty());
        accumulator.ready = true;
        accumulator.add(new TestItem("g"));
        delay(5);
        assertFalse("should have fired", accumulator.batch.isEmpty());
        assertEquals("incorrect batch", "abcdefg", accumulator.batch);
    }


    private class TestItem {
        private final String s;

        public TestItem(String s) {
            this.s = s;
        }
    }

    private class TestAccumulator extends AbstractAccumulator<TestItem> {

        String batch = "";
        boolean ready = true;

        protected TestAccumulator() {
            super(timer, 5, 100, 70);
        }

        @Override
        public void processItems(List<TestItem> items) {
            for (TestItem item : items) {
                batch += item.s;
            }
        }

        @Override
        public boolean isReady() {
            return ready;
        }
    }

}
