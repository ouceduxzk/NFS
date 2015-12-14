package sharing;

import java.math.BigInteger;
import java.util.Random;

public class ShamirSecret{

	 public ShamirSecret(int k, int n){
	 	ShamirSecret.k = k;
	 	ShamirSecret.n = n;
	 }
	 private static int k;
	 private static int n ;
	
	 private static BigInteger randomPrime;

	 public static BigInteger[] split(String secret ){
	    assert k <= n;
		byte[] tmp = secret.getBytes();
		BigInteger secretBi = new BigInteger(tmp);
		// generate a random prime
		int len = secretBi.bitLength() + 1 ;
		Random rnd = new Random();
		randomPrime = BigInteger.probablePrime(len, rnd);
		BigInteger[] coff = new BigInteger[k-1];
		for(int i = 0; i < k-1; i++){
			// the coefficient should be smaller than the prime we used.
			coff[i] = primeClip(randomPrime);
		}
		
		// generates n points, each point is calculated by summing up the polynomial 
		BigInteger[] points = new BigInteger[n];
		for(int i = 0; i < n ; i++){
			BigInteger a0 = secretBi;
			for(int j = 1; j < k; j++){
				// f(x) = a0 + a1 * x + a2 * x^2 + ... ;
				BigInteger tmpi = BigInteger.valueOf(i+1);
				BigInteger tmpj = BigInteger.valueOf(j);
				BigInteger part =  coff[j-1].multiply(tmpi.modPow(tmpj, randomPrime)).mod(randomPrime);
				a0 = a0.add(part).mod(randomPrime);
			}
			points[i] = a0;
		}
		return points;

	}

	/*
	 * https://en.wikipedia.org/wiki/Shamir%27s_Secret_Sharing
	 * generate a prime that is bounded by (0,p)
	 */
	private static BigInteger primeClip(BigInteger upperBound) {
		// TODO Auto-generated method stub
		while(true){
			BigInteger bi = new BigInteger(upperBound.bitLength(), new Random());
			if(bi.compareTo(BigInteger.ZERO) >0 && bi.compareTo(upperBound) < 0){
				return bi;
			}
		}
	}
	
	/*
	 * Given n points
	 */

	public static String recover(BigInteger[] points){
		//int n = points.length;
		BigInteger rec = BigInteger.ZERO;
		for(int i = 0; i < k ; i++){
			// given k points, calculate back the coff 
			BigInteger num = BigInteger.ONE;
			BigInteger de = BigInteger.ONE;
			
			// calcuate the lagrangian basis function
			for(int j = 0; j < k ; j++){
				if(i != j){
					num = num.multiply(BigInteger.valueOf((-1 - j))).mod(randomPrime);
					de =  de.multiply(BigInteger.valueOf((i-j))).mod(randomPrime);
				}
			}
			// f(x) = \sum( y * basis)
			BigInteger tmp  = points[i].multiply(num).multiply(de.modInverse(randomPrime)).mod(randomPrime);
			rec = rec.add(tmp).mod(randomPrime);
		}
		
		return new String(rec.toByteArray());
	}

//	public static void main(String[] args){
//		ShamirSecret ss = new ShamirSecret(3,10);
//		BigInteger[] tmp = ss.split("hi, i m zaikun, coming from Mars! Where is titus? ");
//		String secret = ss.recover(tmp);
//		System.out.println(secret);
//	}
}