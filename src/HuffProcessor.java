import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCount(in); //frequency of every eight bit character
		HuffNode root = makeTreeFromCounts(counts); //create tree to create encodings
		String[] codings = makeCodingsFromTree(root); //creating encodings for each eight bit
		
		out.writeBits(BITS_PER_INT, HUFF_TREE); //magic bit
		writeHeader(root,out); //magic bit
		
		in.reset();
		writeCompressedBits(codings,in,out); //read file again, write encoding, encoding for PSEUDO_EOF
		out.close();
	}
	
	private int[] readForCount(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE+1];
		freq [PSEUDO_EOF] = 1;
		int temp = in.readBits(BITS_PER_WORD);
		while (temp != -1) {
			freq [temp] ++;
			temp = in.readBits(BITS_PER_WORD);
		}
		return freq;
	}
	
	private HuffNode makeTreeFromCounts(int[] cnts) { //index is stored smallest, for the greatest occurence
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
	
		for(int i = 0; i < cnts.length; i++) {
			if (cnts[i] > 0) {
				pq.add(new HuffNode(i,cnts[i],null,null));
			}
		}

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(0,(left.myWeight+right.myWeight),left,right);
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode hf) {
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(hf,"",encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode hf,String ss, String[] ed) {
		if (hf.myLeft == null && hf.myRight == null) {
			ed[hf.myValue] = ss;
			return;
		}
		if (hf.myLeft != null) codingHelper(hf.myLeft,ss.concat("0"),ed);
		if (hf.myRight != null) codingHelper(hf.myRight,ss.concat("1"),ed);
	}
	
	private void writeHeader(HuffNode rt, BitOutputStream out) {
			if (rt == null) return;
			if (rt.myLeft == null && rt.myRight == null) out.writeBits(1, BITS_PER_WORD+1);
			if (rt.myLeft != null) writeHeader(rt.myLeft,out);
			if (rt.myRight != null) writeHeader(rt.myRight,out);
		
	}
	
	private void writeCompressedBits(String[] cd, BitInputStream in, BitOutputStream out) {
		
		int temp = in.readBits(BITS_PER_WORD);
		while (temp >= 0) {
				String code = cd[temp];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				temp = in.readBits(BITS_PER_WORD);
		}
		
		String st = cd[PSEUDO_EOF];
		out.writeBits(st.length(),Integer.parseInt(st,2)); //writeBits to out 
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with" + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	
	
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int check = in.readBits(1);
		if (check == -1) throw new HuffException("not right");
		if (check == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right); //weight is irrelevant for decompression
		}
		
		if (check == 1) {
			int val = in.readBits(BITS_PER_WORD + 1); 
			return new HuffNode(val,0,null,null);
		}
		
		return null;
	}
	
	
	private void readCompressedBits(HuffNode rt, BitInputStream in, BitOutputStream out) {
		
		HuffNode current = rt;
		
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			if (bits == 0) current = current.myLeft;
			if (bits == 1) current = current.myRight;
			if (current.myRight == null && current.myLeft == null) {
					if (current.myValue == PSEUDO_EOF) break;
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = rt;
					
			}
		}
	}
	
	

}