/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

import static java.nio.file.StandardOpenOption.READ;
import static sbt.internal.inc.zip.ZipConstants.*;
import static sbt.internal.inc.zip.ZipUtils.*;

/**
 * A FileSystem built on a zip file
 *
 * @author Xueming Shen
 */

/**
 * Modified implementation of [[com.sun.nio.zipfs.ZipFileSystem]] that allows to:
 * read index (central directory), modify it and write at specified offset.
 *
 * The changes focus on making public whatever is required and remove what is not.
 * It is possible to use unmodified ZipFileSystem to implement operations required
 * for Straight to Jar but it does not work in place (has to recreate zips) and does
 * not allow to disable compression that makes it not efficient enough.
 */
public class ZipCentralDir {

    private final byte[] cen; // CEN & ENDHDR
    private END end;
    private final SeekableByteChannel ch;
    private LinkedHashMap<IndexNode, IndexNode> inodes;
    private static byte[] ROOTPATH = new byte[0];
    private final IndexNode LOOKUPKEY = IndexNode.keyOf(null);
    private List<Entry> elist;
    private static final boolean isWindows =
            System.getProperty("os.name").startsWith("Windows");

    public ZipCentralDir(Path zfpath) throws IOException {
        this.ch = Files.newByteChannel(zfpath, READ);
        this.cen = initCEN();
        elist = readEntries();
        ch.close();
    }

    public long getCentralDirStart() {
        return end.cenoff;
    }

    public void setCentralDirStart(long value) {
        end.cenoff = value;
    }

    public List<Entry> getHeaders() {
        return elist;
    }

    public void setHeaders(List<Entry> value) {
        elist = value;
    }

    public void dump(OutputStream os) throws IOException {
        long written = 0;
        for (Entry entry : elist) {
            written += entry.writeCEN(os);
        }
        end.centot = elist.size();
        end.cenlen = written;
        end.write(os, written);
    }

    private List<Entry> readEntries() throws IOException {
        List<Entry> elist = new ArrayList<>();
        for (IndexNode inode : inodes.values()) {
            if (inode.pos == -1) {
                continue;               // pseudo directory node
            }
            Entry e = Entry.readCEN(this, inode.pos);
            elist.add(e);
        }
        return elist;
    }

    // Reads zip file central directory. Returns the file position of first
    // CEN header, otherwise returns -1 if an error occurred. If zip->msg != NULL
    // then the error was a zip format error and zip->msg has the error text.
    // Always pass in -1 for knownTotal; it's used for a recursive call.
    private byte[] initCEN() throws IOException {
        end = findEND();
        // position of first LOC header (usually 0)
        long locpos;
        if (end.endpos == 0) {
            inodes = new LinkedHashMap<>(10);
            locpos = 0;
            buildNodeTree();
            return null;         // only END header present
        }
        if (end.cenlen > end.endpos)
            zerror("invalid END header (bad central directory size)");
        long cenpos = end.endpos - end.cenlen;     // position of CEN table

        // Get position of first local file (LOC) header, taking into
        // account that there may be a stub prefixed to the zip file.
        locpos = cenpos - end.cenoff;
        if (locpos < 0)
            zerror("invalid END header (bad central directory offset)");

        // read in the CEN and END
        byte[] cen = new byte[(int)(end.cenlen + ENDHDR)];
        if (readFullyAt(cen, 0, cen.length, cenpos) != end.cenlen + ENDHDR) {
            zerror("read CEN tables failed");
        }
        // Iterate through the entries in the central directory
        inodes = new LinkedHashMap<>(end.centot + 1);
        int pos = 0;
        int limit = cen.length - ENDHDR;
        while (pos < limit) {
            if (CENSIG(cen, pos) != CENSIG)
                zerror("invalid CEN header (bad signature)");
            int method = CENHOW(cen, pos);
            int nlen   = CENNAM(cen, pos);
            int elen   = CENEXT(cen, pos);
            int clen   = CENCOM(cen, pos);
            if ((CENFLG(cen, pos) & 1) != 0)
                zerror("invalid CEN header (encrypted entry)");
            if (method != METHOD_STORED && method != METHOD_DEFLATED)
                zerror("invalid CEN header (unsupported compression method: " + method + ")");
            if (pos + CENHDR + nlen > limit)
                zerror("invalid CEN header (bad header size)");
            byte[] name = Arrays.copyOfRange(cen, pos + CENHDR, pos + CENHDR + nlen);
            IndexNode inode = new IndexNode(name, pos);
            inodes.put(inode, inode);
            // skip ext and comment
            pos += (CENHDR + nlen + elen + clen);
        }
        if (pos + ENDHDR != cen.length) {
            zerror("invalid CEN header (bad header size)");
        }
        buildNodeTree();
        return cen;
    }

    private END findEND() throws IOException
    {
        byte[] buf = new byte[READBLOCKSZ];
        long ziplen = ch.size();
        long minHDR = (ziplen - END_MAXLEN) > 0 ? ziplen - END_MAXLEN : 0;
        long minPos = minHDR - (buf.length - ENDHDR);

        for (long pos = ziplen - buf.length; pos >= minPos; pos -= (buf.length - ENDHDR))
        {
            int off = 0;
            if (pos < 0) {
                // Pretend there are some NUL bytes before start of file
                off = (int)-pos;
                Arrays.fill(buf, 0, off, (byte)0);
            }
            int len = buf.length - off;
            if (readFullyAt(buf, off, len, pos + off) != len)
                zerror("zip END header not found");

            // Now scan the block backwards for END header signature
            for (int i = buf.length - ENDHDR; i >= 0; i--) {
                if (buf[i+0] == (byte)'P'    &&
                        buf[i+1] == (byte)'K'    &&
                        buf[i+2] == (byte)'\005' &&
                        buf[i+3] == (byte)'\006' &&
                        (pos + i + ENDHDR + ENDCOM(buf, i) == ziplen)) {
                    // Found END header
                    buf = Arrays.copyOfRange(buf, i, i + ENDHDR);
                    END end = new END();
                    end.endsub = ENDSUB(buf);
                    end.centot = ENDTOT(buf);
                    end.cenlen = ENDSIZ(buf);
                    end.cenoff = ENDOFF(buf);
                    end.comlen = ENDCOM(buf);
                    end.endpos = pos + i;
                    if (end.cenlen == ZIP64_MINVAL ||
                            end.cenoff == ZIP64_MINVAL ||
                            end.centot == ZIP64_MINVAL32)
                    {
                        // need to find the zip64 end;
                        byte[] loc64 = new byte[ZIP64_LOCHDR];
                        if (readFullyAt(loc64, 0, loc64.length, end.endpos - ZIP64_LOCHDR)
                                != loc64.length) {
                            return end;
                        }
                        long end64pos = ZIP64_LOCOFF(loc64);
                        byte[] end64buf = new byte[ZIP64_ENDHDR];
                        if (readFullyAt(end64buf, 0, end64buf.length, end64pos)
                                != end64buf.length) {
                            return end;
                        }
                        // end64 found, re-calcualte everything.
                        end.cenlen = ZIP64_ENDSIZ(end64buf);
                        end.cenoff = ZIP64_ENDOFF(end64buf);
                        end.centot = (int)ZIP64_ENDTOT(end64buf); // assume total < 2g
                        end.endpos = end64pos;
                    }
                    return end;
                }
            }
        }
        zerror("zip END header not found");
        return null; //make compiler happy
    }

    // Internal node that links a "name" to its pos in cen table.
    // The node itself can be used as a "key" to lookup itself in
    // the HashMap inodes.
    static class IndexNode {
        byte[] name;
        int    hashcode;  // node is hashable/hashed by its name
        int    pos = -1;  // position in cen table, -1 menas the
        String nameAsString;
        // entry does not exists in zip file
        IndexNode(byte[] name, int pos) {
            setName(name);
            this.pos = pos;
        }

        static IndexNode keyOf(byte[] name) { // get a lookup key;
            return new IndexNode(name, -1);
        }

        public final void setName(byte[] name) {
            this.name = name;
            this.hashcode = Arrays.hashCode(name);
        }

        public final String getName() {
            if (nameAsString == null) {
                this.nameAsString = new String(name);
            }
            return this.nameAsString;
        }

        final IndexNode as(byte[] name) {           // reuse the node, mostly
            setName(name);                             // as a lookup "key"
            return this;
        }

        public boolean equals(Object other) {
            if (!(other instanceof IndexNode)) {
                return false;
            }
            return Arrays.equals(name, ((IndexNode)other).name);
        }

        public int hashCode() {
            return hashcode;
        }

        IndexNode() {}
        IndexNode sibling;
        IndexNode child;  // 1st child
    }

    private static void zerror(String msg) {
        throw new ZipError(msg);
    }

    private void buildNodeTree() {
        HashSet<IndexNode> dirs = new HashSet<>();
        IndexNode root = new IndexNode(ROOTPATH, -1);
        inodes.put(root, root);
        dirs.add(root);
        for (IndexNode node : inodes.keySet().toArray(new IndexNode[0])) {
            addToTree(node, dirs);
        }
    }

    // ZIP directory has two issues:
    // (1) ZIP spec does not require the ZIP file to include
    //     directory entry
    // (2) all entries are not stored/organized in a "tree"
    //     structure.
    // A possible solution is to build the node tree ourself as
    // implemented below.
    private void addToTree(IndexNode inode, HashSet<IndexNode> dirs) {
        if (dirs.contains(inode)) {
            return;
        }
        IndexNode parent;
        byte[] name = inode.name;
        byte[] pname = getParent(name);
        if (inodes.containsKey(LOOKUPKEY.as(pname))) {
            parent = inodes.get(LOOKUPKEY);
        } else {    // pseudo directory entry
            parent = new IndexNode(pname, -1);
            inodes.put(parent, parent);
        }
        addToTree(parent, dirs);
        inode.sibling = parent.child;
        parent.child = inode;
        if (name[name.length -1] == '/')
            dirs.add(inode);
    }

    private static byte[] getParent(byte[] path) {
        int off = path.length - 1;
        if (off > 0 && path[off] == '/')  // isDirectory
            off--;
        while (off > 0 && path[off] != '/') { off--; }
        if (off <= 0)
            return ROOTPATH;
        return Arrays.copyOf(path, off + 1);
    }

    // Reads len bytes of data from the specified offset into buf.
    // Returns the total number of bytes read.
    // Each/every byte read from here (except the cen, which is mapped).
    private long readFullyAt(byte[] buf, int off, long len, long pos) throws IOException
    {
        ByteBuffer bb = ByteBuffer.wrap(buf);
        bb.position(off);
        bb.limit((int)(off + len));
        return readFullyAt(bb, pos);
    }

    private long readFullyAt(ByteBuffer bb, long pos) throws IOException
    {
        return ch.position(pos).read(bb);
    }

    // End of central directory record
    static class END {
        int  endsub;     // endsub
        int  centot;     // 4 bytes
        long cenlen;     // 4 bytes
        long cenoff;     // 4 bytes
        int  comlen;     // comment length
        byte[] comment;

        /* members of Zip64 end of central directory locator */
        long endpos;

        void write(OutputStream os, long offset) throws IOException {
            boolean hasZip64 = false;
            long xlen = cenlen;
            long xoff = cenoff;
            if (xlen >= ZIP64_MINVAL) {
                xlen = ZIP64_MINVAL;
                hasZip64 = true;
            }
            if (xoff >= ZIP64_MINVAL) {
                xoff = ZIP64_MINVAL;
                hasZip64 = true;
            }
            int count = centot;
            if (count >= ZIP64_MINVAL32) {
                count = ZIP64_MINVAL32;
                hasZip64 = true;
            }
            if (hasZip64) {
                long off64 = offset;
                //zip64 end of central directory record
                writeInt(os, ZIP64_ENDSIG);       // zip64 END record signature
                writeLong(os, ZIP64_ENDHDR - 12); // size of zip64 end
                writeShort(os, 45);               // version made by
                writeShort(os, 45);               // version needed to extract
                writeInt(os, 0);                  // number of this disk
                writeInt(os, 0);                  // central directory start disk
                writeLong(os, centot);            // number of directory entires on disk
                writeLong(os, centot);            // number of directory entires
                writeLong(os, cenlen);            // length of central directory
                writeLong(os, cenoff);            // offset of central directory

                //zip64 end of central directory locator
                writeInt(os, ZIP64_LOCSIG);       // zip64 END locator signature
                writeInt(os, 0);                  // zip64 END start disk
                writeLong(os, off64);             // offset of zip64 END
                writeInt(os, 1);                  // total number of disks (?)
            }
            writeInt(os, ENDSIG);                 // END record signature
            writeShort(os, 0);                    // number of this disk
            writeShort(os, 0);                    // central directory start disk
            writeShort(os, count);                // number of directory entries on disk
            writeShort(os, count);                // total number of directory entries
            writeInt(os, xlen);                   // length of central directory
            writeInt(os, xoff);                   // offset of central directory
            if (comment != null) {            // zip file comment
                writeShort(os, comment.length);
                writeBytes(os, comment);
            } else {
                writeShort(os, 0);
            }
        }
    }

    public static class Entry extends IndexNode {

        // entry attributes
        int    version;
        int    flag;
        int    method = -1;    // compression method
        long   mtime  = -1;    // last modification time (in DOS time)
        long   atime  = -1;    // last access time
        long   ctime  = -1;    // create time
        long   crc    = -1;    // crc-32 of entry data
        long   csize  = -1;    // compressed size of entry data
        long   size   = -1;    // uncompressed size of entry data
        byte[] extra;

        // cen
        int    versionMade;
        int    disk;
        int    attrs;
        long   attrsEx;
        long   locoff;
        byte[] comment;

        Entry() {}

        public final long getLastModifiedTime() {
            return mtime;
        }

        public final long getEntryOffset() {
            return locoff;
        }

        public final  void setEntryOffset(long value) {
            this.locoff = value;
        }

        int version() throws ZipException {
            if (method == METHOD_DEFLATED)
                return 20;
            else if (method == METHOD_STORED)
                return 10;
            throw new ZipException("unsupported compression method");
        }

        ///////////////////// CEN //////////////////////
        static Entry readCEN(ZipCentralDir zipfs, int pos)
                throws IOException
        {
            return new Entry().cen(zipfs, pos);
        }

        private Entry cen(ZipCentralDir zipfs, int pos)
                throws IOException
        {
            byte[] cen = zipfs.cen;
            if (CENSIG(cen, pos) != CENSIG)
                zerror("invalid CEN header (bad signature)");
            versionMade = CENVEM(cen, pos);
            version     = CENVER(cen, pos);
            flag        = CENFLG(cen, pos);
            method      = CENHOW(cen, pos);
            mtime       = dosToJavaTime(CENTIM(cen, pos));
            crc         = CENCRC(cen, pos);
            csize       = CENSIZ(cen, pos);
            size        = CENLEN(cen, pos);
            int nlen    = CENNAM(cen, pos);
            int elen    = CENEXT(cen, pos);
            int clen    = CENCOM(cen, pos);
            disk        = CENDSK(cen, pos);
            attrs       = CENATT(cen, pos);
            attrsEx     = CENATX(cen, pos);
            locoff      = CENOFF(cen, pos);

            pos += CENHDR;
            setName(Arrays.copyOfRange(cen, pos, pos + nlen));

            pos += nlen;
            if (elen > 0) {
                extra = Arrays.copyOfRange(cen, pos, pos + elen);
                pos += elen;
                readExtra(zipfs);
            }
            if (clen > 0) {
                comment = Arrays.copyOfRange(cen, pos, pos + clen);
            }
            return this;
        }

        int writeCEN(OutputStream os) throws IOException
        {
            int version0 = version();
            long csize0  = csize;
            long size0   = size;
            long locoff0 = locoff;
            int elen64   = 0;                // extra for ZIP64
            int elenNTFS = 0;                // extra for NTFS (a/c/mtime)
            int elenEXTT = 0;                // extra for Extended Timestamp
            boolean foundExtraTime = false;  // if time stamp NTFS, EXTT present

            // confirm size/length
            int nlen = (name != null) ? name.length : 0;
            int elen = (extra != null) ? extra.length : 0;
            int eoff = 0;
            int clen = (comment != null) ? comment.length : 0;
            if (csize >= ZIP64_MINVAL) {
                csize0 = ZIP64_MINVAL;
                elen64 += 8;                 // csize(8)
            }
            if (size >= ZIP64_MINVAL) {
                size0 = ZIP64_MINVAL;        // size(8)
                elen64 += 8;
            }
            if (locoff >= ZIP64_MINVAL) {
                locoff0 = ZIP64_MINVAL;
                elen64 += 8;                 // offset(8)
            }
            if (elen64 != 0) {
                elen64 += 4;                 // header and data sz 4 bytes
            }
            while (eoff + 4 < elen) {
                int tag = SH(extra, eoff);
                int sz = SH(extra, eoff + 2);
                if (tag == EXTID_EXTT || tag == EXTID_NTFS) {
                    foundExtraTime = true;
                }
                eoff += (4 + sz);
            }
            if (!foundExtraTime) {
                if (isWindows) {             // use NTFS
                    elenNTFS = 36;           // total 36 bytes
                } else {                     // Extended Timestamp otherwise
                    elenEXTT = 9;            // only mtime in cen
                }
            }
            writeInt(os, CENSIG);            // CEN header signature
            if (elen64 != 0) {
                writeShort(os, 45);          // ver 4.5 for zip64
                writeShort(os, 45);
            } else {
                writeShort(os, version0);    // version made by
                writeShort(os, version0);    // version needed to extract
            }
            writeShort(os, flag);            // general purpose bit flag
            writeShort(os, method);          // compression method
            // last modification time
            writeInt(os, (int)javaToDosTime(mtime));
            writeInt(os, crc);               // crc-32
            writeInt(os, csize0);            // compressed size
            writeInt(os, size0);             // uncompressed size
            writeShort(os, name.length);
            writeShort(os, elen + elen64 + elenNTFS + elenEXTT);

            if (comment != null) {
                writeShort(os, Math.min(clen, 0xffff));
            } else {
                writeShort(os, 0);
            }
            writeShort(os, 0);              // starting disk number
            writeShort(os, 0);              // internal file attributes (unused)
            writeInt(os, 0);                // external file attributes (unused)
            writeInt(os, locoff0);          // relative offset of local header
            writeBytes(os, name);
            if (elen64 != 0) {
                writeShort(os, EXTID_ZIP64);// Zip64 extra
                writeShort(os, elen64 - 4); // size of "this" extra block
                if (size0 == ZIP64_MINVAL)
                    writeLong(os, size);
                if (csize0 == ZIP64_MINVAL)
                    writeLong(os, csize);
                if (locoff0 == ZIP64_MINVAL)
                    writeLong(os, locoff);
            }
            if (elenNTFS != 0) {
                writeShort(os, EXTID_NTFS);
                writeShort(os, elenNTFS - 4);
                writeInt(os, 0);            // reserved
                writeShort(os, 0x0001);     // NTFS attr tag
                writeShort(os, 24);
                writeLong(os, javaToWinTime(mtime));
                writeLong(os, javaToWinTime(atime));
                writeLong(os, javaToWinTime(ctime));
            }
            if (elenEXTT != 0) {
                writeShort(os, EXTID_EXTT);
                writeShort(os, elenEXTT - 4);
                if (ctime == -1)
                    os.write(0x3);          // mtime and atime
                else
                    os.write(0x7);          // mtime, atime and ctime
                writeInt(os, javaToUnixTime(mtime));
            }
            if (extra != null)              // whatever not recognized
                writeBytes(os, extra);
            if (comment != null)            //TBD: 0, Math.min(commentBytes.length, 0xffff));
                writeBytes(os, comment);
            return CENHDR + nlen + elen + clen + elen64 + elenNTFS + elenEXTT;
        }

        ///////////////////// LOC //////////////////////
        // read NTFS, UNIX and ZIP64 data from cen.extra
        void readExtra(ZipCentralDir zipfs) throws IOException {
            if (extra == null)
                return;
            int elen = extra.length;
            int off = 0;
            int newOff = 0;
            while (off + 4 < elen) {
                // extra spec: HeaderID+DataSize+Data
                int pos = off;
                int tag = SH(extra, pos);
                int sz = SH(extra, pos + 2);
                pos += 4;
                if (pos + sz > elen)         // invalid data
                    break;
                switch (tag) {
                    case EXTID_ZIP64 :
                        if (size == ZIP64_MINVAL) {
                            if (pos + 8 > elen)  // invalid zip64 extra
                                break;           // fields, just skip
                            size = LL(extra, pos);
                            pos += 8;
                        }
                        if (csize == ZIP64_MINVAL) {
                            if (pos + 8 > elen)
                                break;
                            csize = LL(extra, pos);
                            pos += 8;
                        }
                        if (locoff == ZIP64_MINVAL) {
                            if (pos + 8 > elen)
                                break;
                            locoff = LL(extra, pos);
                            pos += 8;
                        }
                        break;
                    case EXTID_NTFS:
                        if (sz < 32)
                            break;
                        pos += 4;    // reserved 4 bytes
                        if (SH(extra, pos) !=  0x0001)
                            break;
                        if (SH(extra, pos + 2) != 24)
                            break;
                        // override the loc field, datatime here is
                        // more "accurate"
                        mtime  = winToJavaTime(LL(extra, pos + 4));
                        atime  = winToJavaTime(LL(extra, pos + 12));
                        ctime  = winToJavaTime(LL(extra, pos + 20));
                        break;
                    case EXTID_EXTT:
                        // spec says the Extened timestamp in cen only has mtime
                        // need to read the loc to get the extra a/ctime
                        byte[] buf = new byte[LOCHDR];
                        if (zipfs.readFullyAt(buf, 0, buf.length , locoff)
                                != buf.length)
                            throw new ZipException("loc: reading failed");
                        if (LOCSIG(buf) != LOCSIG)
                            throw new ZipException("loc: wrong sig ->"
                                    + Long.toString(LOCSIG(buf), 16));

                        int locElen = LOCEXT(buf);
                        if (locElen < 9)    // EXTT is at lease 9 bytes
                            break;
                        int locNlen = LOCNAM(buf);
                        buf = new byte[locElen];
                        if (zipfs.readFullyAt(buf, 0, buf.length , locoff + LOCHDR + locNlen)
                                != buf.length)
                            throw new ZipException("loc extra: reading failed");
                        int locPos = 0;
                        while (locPos + 4 < buf.length) {
                            int locTag = SH(buf, locPos);
                            int locSZ  = SH(buf, locPos + 2);
                            locPos += 4;
                            if (locTag  != EXTID_EXTT) {
                                locPos += locSZ;
                                continue;
                            }
                            int flag = CH(buf, locPos++);
                            if ((flag & 0x1) != 0) {
                                mtime = unixToJavaTime(LG(buf, locPos));
                                locPos += 4;
                            }
                            if ((flag & 0x2) != 0) {
                                atime = unixToJavaTime(LG(buf, locPos));
                                locPos += 4;
                            }
                            if ((flag & 0x4) != 0) {
                                ctime = unixToJavaTime(LG(buf, locPos));
                                locPos += 4;
                            }
                            break;
                        }
                        break;
                    default:    // unknown tag
                        System.arraycopy(extra, off, extra, newOff, sz + 4);
                        newOff += (sz + 4);
                }
                off += (sz + 4);
            }
            if (newOff != 0 && newOff != extra.length)
                extra = Arrays.copyOf(extra, newOff);
            else
                extra = null;
        }
    }

}
