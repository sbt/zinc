/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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


/**
 *
 * @author Xueming Shen
 */

class ZipConstants {
    /*
     * Compression methods
     */
    static final int METHOD_STORED     = 0;
    static final int METHOD_DEFLATED   = 8;

    /*
     * Header signatures
     */
    static long LOCSIG = 0x04034b50L;   // "PK\003\004"
    static long CENSIG = 0x02014b50L;   // "PK\001\002"
    static long ENDSIG = 0x06054b50L;   // "PK\005\006"

    /*
     * Header sizes in bytes (including signatures)
     */
    static final int LOCHDR = 30;       // LOC header size
    static final int CENHDR = 46;       // CEN header size
    static final int ENDHDR = 22;       // END header size

    /*
     * ZIP64 constants
     */
    static final long ZIP64_ENDSIG = 0x06064b50L;  // "PK\006\006"
    static final long ZIP64_LOCSIG = 0x07064b50L;  // "PK\006\007"
    static final int  ZIP64_ENDHDR = 56;           // ZIP64 end header size
    static final int  ZIP64_LOCHDR = 20;           // ZIP64 end loc header size

    static final int  ZIP64_MINVAL32 = 0xFFFF;
    static final long ZIP64_MINVAL = 0xFFFFFFFFL;

    /*
     * Extra field header ID
     */
    static final int  EXTID_ZIP64 = 0x0001;      // ZIP64
    static final int  EXTID_NTFS  = 0x000a;      // NTFS
    static final int  EXTID_EXTT  = 0x5455;      // Info-ZIP Extended Timestamp

    /*
     * fields access methods
     */
    ///////////////////////////////////////////////////////
    static final int CH(byte[] b, int n) {
        return Byte.toUnsignedInt(b[n]);
    }

    static final int SH(byte[] b, int n) {
        return Byte.toUnsignedInt(b[n]) | (Byte.toUnsignedInt(b[n + 1]) << 8);
    }

    static final long LG(byte[] b, int n) {
        return ((SH(b, n)) | (SH(b, n + 2) << 16)) & 0xffffffffL;
    }

    static final long LL(byte[] b, int n) {
        return (LG(b, n)) | (LG(b, n + 4) << 32);
    }

    static final long GETSIG(byte[] b) {
        return LG(b, 0);
    }

    // local file (LOC) header fields
    static final long LOCSIG(byte[] b) { return LG(b, 0); } // signature
    static final int  LOCNAM(byte[] b) { return SH(b, 26);} // filename length
    static final int  LOCEXT(byte[] b) { return SH(b, 28);} // extra field length

    // end of central directory header (END) fields
    static final int  ENDSUB(byte[] b) { return SH(b, 8); }  // number of entries on this disk
    static final int  ENDTOT(byte[] b) { return SH(b, 10);}  // total number of entries
    static final long ENDSIZ(byte[] b) { return LG(b, 12);}  // central directory size
    static final long ENDOFF(byte[] b) { return LG(b, 16);}  // central directory offset
    static final int  ENDCOM(byte[] b) { return SH(b, 20);}  // size of zip file comment
    static final int  ENDCOM(byte[] b, int off) { return SH(b, off + 20);}

    // zip64 end of central directory recoder fields
    static final long ZIP64_ENDTOD(byte[] b) { return LL(b, 24);}  // total number of entries on disk
    static final long ZIP64_ENDTOT(byte[] b) { return LL(b, 32);}  // total number of entries
    static final long ZIP64_ENDSIZ(byte[] b) { return LL(b, 40);}  // central directory size
    static final long ZIP64_ENDOFF(byte[] b) { return LL(b, 48);}  // central directory offset
    static final long ZIP64_LOCOFF(byte[] b) { return LL(b, 8);}   // zip64 end offset

    // central directory header (CEN) fields
    static final long CENSIG(byte[] b, int pos) { return LG(b, pos + 0); }
    static final int  CENVEM(byte[] b, int pos) { return SH(b, pos + 4); }
    static final int  CENVER(byte[] b, int pos) { return SH(b, pos + 6); }
    static final int  CENFLG(byte[] b, int pos) { return SH(b, pos + 8); }
    static final int  CENHOW(byte[] b, int pos) { return SH(b, pos + 10);}
    static final long CENTIM(byte[] b, int pos) { return LG(b, pos + 12);}
    static final long CENCRC(byte[] b, int pos) { return LG(b, pos + 16);}
    static final long CENSIZ(byte[] b, int pos) { return LG(b, pos + 20);}
    static final long CENLEN(byte[] b, int pos) { return LG(b, pos + 24);}
    static final int  CENNAM(byte[] b, int pos) { return SH(b, pos + 28);}
    static final int  CENEXT(byte[] b, int pos) { return SH(b, pos + 30);}
    static final int  CENCOM(byte[] b, int pos) { return SH(b, pos + 32);}
    static final int  CENDSK(byte[] b, int pos) { return SH(b, pos + 34);}
    static final int  CENATT(byte[] b, int pos) { return SH(b, pos + 36);}
    static final long CENATX(byte[] b, int pos) { return LG(b, pos + 38);}
    static final long CENOFF(byte[] b, int pos) { return LG(b, pos + 42);}

    /* The END header is followed by a variable length comment of size < 64k. */
    static final long END_MAXLEN = 0xFFFF + ENDHDR;
    static final int READBLOCKSZ = 128;
}
