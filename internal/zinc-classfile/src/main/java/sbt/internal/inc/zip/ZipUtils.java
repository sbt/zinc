/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package sbt.internal.inc.zip;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Xueming Shen
 */

class ZipUtils {

    /*
     * Writes a 16-bit short to the output stream in little-endian byte order.
     */
    static void writeShort(OutputStream os, int v) throws IOException {
        os.write(v & 0xff);
        os.write((v >>> 8) & 0xff);
    }

    /*
     * Writes a 32-bit int to the output stream in little-endian byte order.
     */
    static void writeInt(OutputStream os, long v) throws IOException {
        os.write((int)(v & 0xff));
        os.write((int)((v >>>  8) & 0xff));
        os.write((int)((v >>> 16) & 0xff));
        os.write((int)((v >>> 24) & 0xff));
    }

    /*
     * Writes a 64-bit int to the output stream in little-endian byte order.
     */
    static void writeLong(OutputStream os, long v) throws IOException {
        os.write((int)(v & 0xff));
        os.write((int)((v >>>  8) & 0xff));
        os.write((int)((v >>> 16) & 0xff));
        os.write((int)((v >>> 24) & 0xff));
        os.write((int)((v >>> 32) & 0xff));
        os.write((int)((v >>> 40) & 0xff));
        os.write((int)((v >>> 48) & 0xff));
        os.write((int)((v >>> 56) & 0xff));
    }

    /*
     * Writes an array of bytes to the output stream.
     */
    static void writeBytes(OutputStream os, byte[] b)
            throws IOException
    {
        os.write(b, 0, b.length);
    }

    /*
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    static long dosToJavaTime(long dtime) {
        ZonedDateTime d = ZonedDateTime.of(
          (int)(((dtime >> 25) & 0x7f) + 1980), // year
          (int)(((dtime >> 21) & 0x0f)), // month
          (int)((dtime >> 16) & 0x1f), // day
          (int)((dtime >> 11) & 0x1f), // hour
          (int)((dtime >> 5) & 0x3f), // min
          (int)((dtime << 1) & 0x3e), // seconds
          0, // nanos
          ZoneId.systemDefault()
        );
        return d.toInstant().toEpochMilli();
    }

    /*
     * Converts Java time to DOS time.
     */
    static long javaToDosTime(long time) {
        Instant i = Instant.ofEpochMilli(time);
        ZonedDateTime d = ZonedDateTime.ofInstant(i, ZoneId.systemDefault());
        return (d.getYear() - 1980) << 25 | (d.getMonthValue()) << 21 |
                d.getDayOfMonth() << 16 | d.getHour() << 11 | d.getMinute() << 5 |
                d.getSecond() >> 1;
    }


    // used to adjust values between Windows and java epoch
    private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;
    static long winToJavaTime(long wtime) {
        return TimeUnit.MILLISECONDS.convert(
                wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS, TimeUnit.MICROSECONDS);
    }

    static long javaToWinTime(long time) {
        return (TimeUnit.MICROSECONDS.convert(time, TimeUnit.MILLISECONDS)
                - WINDOWS_EPOCH_IN_MICROSECONDS) * 10;
    }

    static long unixToJavaTime(long utime) {
        return TimeUnit.MILLISECONDS.convert(utime, TimeUnit.SECONDS);
    }

    static long javaToUnixTime(long time) {
        return TimeUnit.SECONDS.convert(time, TimeUnit.MILLISECONDS);
    }

}
