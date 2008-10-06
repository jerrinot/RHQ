 /*
  * RHQ Management Platform
  * Copyright (C) 2005-2008 Red Hat, Inc.
  * All rights reserved.
  *
  * This program is free software; you can redistribute it and/or modify
  * it under the terms of the GNU General Public License, version 2, as
  * published by the Free Software Foundation, and/or the GNU Lesser
  * General Public License, version 2.1, also as published by the Free
  * Software Foundation.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License and the GNU Lesser General Public License
  * for more details.
  *
  * You should have received a copy of the GNU General Public License
  * and the GNU Lesser General Public License along with this program;
  * if not, write to the Free Software Foundation, Inc.,
  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
  */
package org.rhq.core.system;

import java.util.EnumSet;

/**
 * Checks for memory leaks.
 *
 * @author John Mazzitelli
 */
public class MemoryLeakChecker {
    public static final EnumSet<LeakType> JAVA_ONLY = EnumSet.of(LeakType.JAVA);
    public static final EnumSet<LeakType> NATIVE_ONLY = EnumSet.of(LeakType.NATIVE);
    public static final EnumSet<LeakType> JAVA_AND_NATIVE = EnumSet.of(LeakType.JAVA, LeakType.NATIVE);

    public enum LeakType {
        /** test leaks in Java heap */
        JAVA,
        /** test for leaks in native code */
        NATIVE
    }

    private static final long ALLOWED_TO_LEAK = Long.parseLong(System
        .getProperty("rhq.testng.allowedToLeak", "1000000"));

    /**
     * Runs the given task alot of times and checks to see if we leak memory.
     *
     * @param  task
     * @param  message  an error message to be included in assert exception if a memory leak is detected
     * @param  maxLoop  maximum outer loop - the number of times the inner task loop is run
     * @param  taskLoop number of times the task it run in succession (this is the "inner loop")
     * @param  leakType where to look for leaks - in java heap, in native code or test for both
     *
     * @throws RuntimeException
     */
    public static void checkForMemoryLeak(Runnable task, String message, long maxLoop, long taskLoop,
        EnumSet<LeakType> leakType) {
        if (leakType.contains(LeakType.JAVA)) {
            checkForMemoryLeak(task, message, maxLoop, taskLoop);
        }

        if (leakType.contains(LeakType.NATIVE)) {
            NativeMemoryLeakChecker.checkForMemoryLeak(task, message, maxLoop, taskLoop);
        }
    }

    /**
     * Runs the given task alot of times and checks to see if we leak memory in the {@link LeakType#JAVA Java heap}.
     *
     * @param  task
     * @param  message  an error message to be included in assert exception if a memory leak is detected
     * @param  maxLoop  maximum outer loop - the number of times the inner task loop is run
     * @param  taskLoop number of times the task it run in succession (this is the "inner loop")
     *
     * @throws RuntimeException
     */
    private static void checkForMemoryLeak(Runnable task, String message, long maxLoop, long taskLoop) {
        System.out.println("Checking for java heap memory leak [" + message + "]....");

        // run the task once - to load in initial memory so first memory usage numbers aren't skewed
        task.run();

        long startUsedMemory = -1;
        long maxLoopCounter = maxLoop;

        try {
            int endedUpWithLessUsedThanBefore = 0; // number of times afterUsedMemory is less than beforeUsedMemory
            long lowestAfterUsedMemoryWhenEndingUpWithLess = Long.MAX_VALUE; // when after was less than before, this is the lower "after" value

            while (maxLoopCounter-- > 0) {
                long beforeUsedMemory = getUsedMemory();

                for (int i = 0; i < taskLoop; i++) {
                    task.run();
                }

                long afterUsedMemory = getUsedMemory();

                if (startUsedMemory == -1) {
                    // we'll assign this after the first iteration - so we can get what we think will be steady state memory usage
                    startUsedMemory = afterUsedMemory;
                }

                System.out.println("Memory used before/after: " + beforeUsedMemory + '/' + afterUsedMemory);

                if (afterUsedMemory <= beforeUsedMemory) {
                    // hmm we are using the same or less memory now than before - must have GC'ed some memory
                    // we still might be leaking though - GC might only have cleaned up some but not all
                    if (lowestAfterUsedMemoryWhenEndingUpWithLess >= afterUsedMemory) {
                        lowestAfterUsedMemoryWhenEndingUpWithLess = afterUsedMemory;
                        endedUpWithLessUsedThanBefore++; // memory usage keeps going down, this is good and we might be able to skip the test
                    }

                    if (endedUpWithLessUsedThanBefore > (maxLoop / 2)) {
                        break; // more than half the times we used less memory than we were using at the beginning - no leak here
                    }
                }
            }
        } catch (OutOfMemoryError oom) {
            System.out.println("!!!!! OUT OF MEMORY !!!!! : " + message);
            throw oom;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println();
        }

        if (maxLoopCounter <= 0) {
            // possible leak - our used memory after the tests was always more than before the test
            // see how much memory we used since the start
            long endUsedMemory = getUsedMemory();
            long usedMemoryDiff = endUsedMemory - startUsedMemory;
            if (usedMemoryDiff >= ALLOWED_TO_LEAK) {
                System.out.println("[" + message + "] We leaked too much memory: (end-start=diff)->" + endUsedMemory
                    + '-' + startUsedMemory + '=' + usedMemoryDiff);
                System.out.println("[" + message + "] Going to force a System.gc() to see if we can free some memory");

                System.gc();
                endUsedMemory = getUsedMemory();
                usedMemoryDiff = endUsedMemory - startUsedMemory;
                System.out.println("[" + message + "] After System.gc(): (end-start=diff)->" + endUsedMemory + '-'
                    + startUsedMemory + '=' + usedMemoryDiff);
                assert (usedMemoryDiff < ALLOWED_TO_LEAK) : "[" + message
                    + "] We leaked too much memory: (end-start=diff)->" + endUsedMemory + '-' + startUsedMemory + '='
                    + usedMemoryDiff;
            } else {
                System.out.println("[" + message
                    + "] Went the distance but did not seem to leak memory: (end-start=diff)->" + endUsedMemory + '-'
                    + startUsedMemory + '=' + usedMemoryDiff);
            }
        }
    }

    /**
     * Returns the amount of used memory for this VM.
     *
     * @return amount of total memory minus free memory
     */
    public static long getUsedMemory() {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long usedMemory = totalMemory - freeMemory;
        return usedMemory;
    }
}