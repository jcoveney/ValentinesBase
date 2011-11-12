package com.jcoveney.valentinesbase;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

//TODO in general this is not optimized for speed
//TODO should probably make some tests and stuff...
public class ValentinesBase {
    //when we pass things to encode, pass the raw bytes, will ensure it's most efficiently encoded
    Map<SizeSensitiveBitSet,String> encodingMap = new HashMap<SizeSensitiveBitSet,String>(21844,1.0f);
    Map<String,SizeSensitiveBitSet> decodingMap = new HashMap<String,SizeSensitiveBitSet>(21844,1.0f);

    public ValentinesBase() throws FileNotFoundException,IOException {
        //TODO need a better way to specify this file
        BufferedReader br = new BufferedReader(new FileReader("/Users/jcoveney/unicode/thebigone"));
        for (int bitwidth=2;bitwidth<=14;bitwidth+=2) {
            for (int val=0;val<(1<<bitwidth);val++) {
                SizeSensitiveBitSet bs=new SizeSensitiveBitSet(bitwidth);
                int curval=val;
                for (int j=0;j<bitwidth;j++) {
                    bs.set(j,curval%2==1);
                    curval=curval>>1;
                }
                String s = br.readLine();
                encodingMap.put(bs,s);
                decodingMap.put(s,bs);
            }
        }
    }

    //this makes a mask of n bits
    public static int getMask(int bits) {
        int mask=1;
        for (int i=1;i<bits;i++) {
            mask=mask<<1;
            mask++;
        }
        return mask;
    }

    public String encode(byte[] buf) {
        //expected size: size*8 bits per byte/14 bits per encoded character, rounded up
        StringBuffer encoding=new StringBuffer(buf.length*8/14+1);

        //This will maintain the position in the byte array
        int position=0;

        /* This will store the bit remainder. Because bytes have 8 bits,
         * but we are encoding characters that represent 14 bits, there
         * will be a remainder which is the difference. We store the remainder,
         * as well as the number of bits in the remainder (since an int is 32 bits).
         */
        int remainder=0;
        int remainderBits=0;

        /* We will add bytes to the remainder until it is 2 or more bytes.
         * When that occurs, we'll encode 14 bits, save the remainder, and continue.
         */
        while (position<buf.length) {
            // This gets the value of the next byte
            int val=buf[position]&0xff;
            position++;
            remainder=remainder<<8+val;
            remainderBits+=8;
            // We only need to encode if there are 14 or more bits
            if (remainderBits>=14) {
                //we need the leftmost 14 bits, as they are the oldest
                remainderBits-=14;
                remainder=remainder&getMask(remainderBits);
                int toEncode=remainder>>>remainderBits;
                encoding.append(encodeInt(toEncode,14));
            }
        }
        // If there are bits remaining, they need to be encoded
        if (remainderBits>0) encoding.append(encodeInt(remainder,remainderBits));

        return encoding.toString();
    }

    // This allows you to encode an integer with a specified number of bits
    public String encodeInt(int toEncode, int nbits) {
        //TODO can take advantage of the BitSet functions to improve this
        SizeSensitiveBitSet bits = new SizeSensitiveBitSet(nbits);
        for (int i=0;i<nbits;i++) {
            bits.set(i,toEncode%2==1);
            toEncode=toEncode>>>1;
        }
        return encodingMap.get(bits);
    }

    public byte[] decode(String str) {
        //Expected size: characters*14 bits of info per char/8 bits in a byte, rounded up
        ByteArrayOutputStream baos=new ByteArrayOutputStream(str.length()*14/8+1);

        //We go through the characters and decode
        //Every time we accumulate more than 8 bits of info, we'll output it
        int remainder=0;
        int remainderBits=0;
        for (int i=0;i<str.length();i++) {
            SizeSensitiveBitSet ssbs=decodingMap.get(str.substring(i,i+1));
            int shift=ssbs.getBits();
            int val=0;
            for (int j=shift;j>=0;j--) {
                val=val<<1;
                val+=ssbs.get(j) ? 1 : 0;
            }

            remainder=(remainder<<shift)+val;
            remainderBits+=shift;

            //As long as the remainder has more than 8 bits, we'll output a byte
            //We need to be careful, though. If there are 16 or more, want the highest 8 in order
            while (remainderBits>=8) {
                remainderBits-=8;
                baos.write((byte)(remainder>>>remainderBits));
                remainder=remainder&getMask(remainderBits);
            }
        }

        //If the string was properly encoded by this class, then it is impossible for it not to decode perfectly to bytes

        return baos.toByteArray();
    }

    /* This seeks to encapsulate a BitSet. The key difference being that
     * in order for two SizeSensitiveBitSets to be equal, their sizes must be
     * equal as well as their values. Example:
     * BitSet bs1 = new BitSet(10);
     * bs1.set(0,true);
     * bs1.set(2,true);
     * BitSet bs2 = new BitSet(100);
     * bs2.set(0,true);
     * bs2.set(2,true);
     * System.out.println(bs1.equals(bs2)); //returns true
     *
     * However,
     *
     * SizeSensitiveBitSet ssbs1 = new SizeSensitiveBitSet(10);
     * ssbs1.set(0,true);
     * ssbs1.set(2,true);
     * SizeSensitiveBitSet ssbs2 = new SizeSensitiveBitSet(100);
     * ssbs2.set(0,true);
     * ssbs2.set(2,true);
     * System.out.println(ssbs1.equals(ssbs2)); //returns false
     *
     * In fact, the latter two can never be equal, as their sizes are different.
     */
    static class SizeSensitiveBitSet {
        private BitSet bs;
        private int nbits;

        public SizeSensitiveBitSet() { bs=new BitSet(); }

        public SizeSensitiveBitSet(int nbits) { bs=new BitSet(nbits); this.nbits=nbits; }

        //returns the number of bits this SizeSensitiveBitSet stores
        public int getBits() { return nbits; }

        public BitSet getUnderlying() { return bs; }

        public void and(BitSet set) { bs.and(set); }

        public void andNot(BitSet set) { bs.andNot(set); }

        public int cardinality() { return bs.cardinality(); }

        public void clear() { bs.clear(); }

        public void clear(int fromIndex, int toIndex) { bs.clear(fromIndex, toIndex); }

        public void clear(int bitIndex) { bs.clear(bitIndex); }

        public Object clone() { return bs.clone(); }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SizeSensitiveBitSet))
                    return false;
            SizeSensitiveBitSet theirs=(SizeSensitiveBitSet)obj;
            if (nbits!=theirs.getBits() || bs.length()!=theirs.length() || bs.size()!=theirs.size())
                return false;
            for (int i=0;i<bs.length();i++)
                if (bs.get(i)!=theirs.get(i))
                    return false;
            return true;

        }

        public void flip(int fromIndex, int toIndex) { bs.flip(fromIndex, toIndex); }

        public void flip(int bitIndex) { bs.flip(bitIndex); }

        public BitSet get(int fromIndex, int toIndex) { return bs.get(fromIndex, toIndex); }

        public boolean get(int bitIndex) { return bs.get(bitIndex); }

        @Override
        public int hashCode() {
            int result=31*bs.hashCode()+17;
            result=31*result+bs.size();
            result=31*result+bs.length();
            result=31*result+nbits;
            return result;
        }

        public boolean intersects(BitSet set) { return bs.intersects(set); }

        public boolean isEmpty() { return bs.isEmpty(); }

        public int length() { return bs.length(); }

        public int nextClearBit(int fromIndex) { return bs.nextClearBit(fromIndex); }

        public int nextSetBit(int fromIndex) { return bs.nextSetBit(fromIndex); }

        public void or(BitSet set) { bs.or(set); }

        public void set(int bitIndex, boolean value) { bs.set(bitIndex, value); }

        public void set(int fromIndex, int toIndex, boolean value) { bs.set(fromIndex, toIndex, value); }

        public void set(int fromIndex, int toIndex) { bs.set(fromIndex, toIndex); }

        public void set(int bitIndex) { bs.set(bitIndex); }

        public int size() { return bs.size(); }

        public String toString() { return bs.toString(); }

        public void xor(BitSet set) { bs.xor(set); }
    }

}
