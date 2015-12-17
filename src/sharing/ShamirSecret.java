package sharing;

import java.math.BigInteger;
import java.nio.file.*;

import javax.xml.bind.DatatypeConverter;
import java.util.Random;

public class ShamirSecret{
    private final int k;
    private final int n;
    private BigInteger randomPrime;
    
    public ShamirSecret(int k, int n, BigInteger prime){
        this.k = k;
        this.n = n;
        if (prime == null) {
        	System.out.println("Generating new prime");
        	prime = generatePrime();
        }
        this.randomPrime = prime;
    }
	 
    public static BigInteger generatePrime() {
    	return generatePrime(128);
    }
    
    public static BigInteger generatePrime(int length) {
		Random rnd = new Random();
		return BigInteger.probablePrime(length, rnd);
    }
    
	 public String[] split(String secret){
	    assert k <= n;
	    assert secret.length() < 256; // hardcode prime length 256
		byte[] tmp = secret.getBytes();
		BigInteger secretBi = new BigInteger(tmp);
		// generate a random prime
		BigInteger[] coff = new BigInteger[k-1];
		for(int i = 0; i < k-1; i++){
			// the coefficient should be smaller than the prime we used.
			coff[i] = primeClip(randomPrime);
		}
		
		// generates n points, each point is calculated by summing up the polynomial 
		BigInteger[] points = new BigInteger[n];
		for (int i = 0; i < n ; i++) {
			BigInteger a0 = secretBi;
			for (int j = 1; j < k; j++) {
				// f(x) = a0 + a1 * x + a2 * x^2 + ... ;
				BigInteger tmpi = BigInteger.valueOf(i+1);
				BigInteger tmpj = BigInteger.valueOf(j);
				BigInteger part =  coff[j-1].multiply(tmpi.modPow(tmpj, randomPrime)).mod(randomPrime);
				a0 = a0.add(part).mod(randomPrime);
			}
			points[i] = a0;
		}
		String[] rv = new String[points.length];
		for (int i=0; i<rv.length; i++) {
		    rv[i] = DatatypeConverter.printHexBinary((points[i].toByteArray()));
		}
		return rv;

	}

	/*
	 * https://en.wikipedia.org/wiki/Shamir%27s_Secret_Sharing
	 * generate a prime that is bounded by (0,p)
	 */
	private static BigInteger primeClip(BigInteger upperBound) {
		while (true) {
			BigInteger bi = new BigInteger(upperBound.bitLength(), new Random());
			if (bi.compareTo(BigInteger.ZERO) >0 && bi.compareTo(upperBound) < 0) {
				return bi;
			}
		}
	}
	
	/*
	 * Given n points
	 */
	public String recover(String[] parts, int[] which) {
	    assert parts.length == which.length;
	    if (parts.length < k) {
	        System.err.format("Need at least %dk parts to reconstruct\n", k);
	        return "";
	    }
	    BigInteger[] points = new BigInteger[parts.length];
	    for (int i=0; i<parts.length; i++) {
	        points[i] = new BigInteger(DatatypeConverter.parseHexBinary(parts[i]));
	    }
		BigInteger rec = BigInteger.ZERO;
		for (int i = 0; i < k ; i++) {
			// given k points, calculate back the coff 
			BigInteger num = BigInteger.ONE;
			BigInteger de = BigInteger.ONE;
			
			// calcuate the lagrangian basis function
			for (int j = 0; j < k ; j++) {
				if (i != j) {
					num = num.multiply(BigInteger.valueOf(-which[j] - 1)).mod(randomPrime);
					de  =  de.multiply(BigInteger.valueOf( which[i] - which[j])).mod(randomPrime);
				}
			}
			// f(x) = \sum( y * basis)
			BigInteger tmp  = points[i].multiply(num).multiply(de.modInverse(randomPrime)).mod(randomPrime);
			rec = rec.add(tmp).mod(randomPrime);
		}
		
		return new String(rec.toByteArray());
	}

	public static void main(String[] args) throws Exception {
	    int length = 150;
		ShamirSecret ss = new ShamirSecret(3, 10, null);
		String contents = "test string"; // new String(Files.readAllBytes(Paths.get("/usr/share/dict/words"))).substring(0, length); 
		String[] parts  = ss.split(contents);
		String[] tmp    = new String[] {parts[5], parts[3], parts[7]};
		int[] which     = new int[] {5,3,7};
		String secret   = ss.recover(tmp, which);
		System.out.println(secret);
	}
}