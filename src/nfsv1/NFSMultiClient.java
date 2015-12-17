package nfsv1;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.*;
import java.nio.file.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.DatatypeConverter;

import org.acplt.oncrpc.OncRpcException;

import client.nfs.fattr;
import sharing.ShamirSecret;

public class NFSMultiClient implements NFSClientInterface {
    private NFSClient[] clients;
    private int[] nums;
    private final BigInteger prime;
    
    public NFSMultiClient(int[] nums, String[] hosts, String[] mntPoints, int uid, int gid, String username, String prime) throws Exception {
        assert hosts.length == mntPoints.length;
        Path primePath = Paths.get(prime);
        if (!Files.exists(primePath)) {
        	BigInteger new_prime = ShamirSecret.generatePrime();
        	Files.write(primePath, DatatypeConverter.printHexBinary(new_prime.toByteArray()).getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        this.prime = new BigInteger(DatatypeConverter.parseHexBinary(new String(Files.readAllBytes(primePath))));
        this.nums = nums;
        clients = new NFSClient[hosts.length];
        for (int i=0; i<hosts.length; i++) {
            clients[i] = new NFSClient(hosts[i], mntPoints[i], uid, gid, username, null);
            clients[i].useAES = false;
        }
    }
    public boolean createFile(String path) throws IOException, OncRpcException {
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].createFile(path)) return false;
        }
        return true;
    }
    
    public boolean writeFile(String path, String contents) throws IOException, OncRpcException  {
        byte[] rawKey = NFSClient.generateKey();
        SecretKeySpec key = new SecretKeySpec(rawKey, "AES");
        IvParameterSpec iv  = new IvParameterSpec(DatatypeConverter.parseHexBinary("0123456789abcdef0123456789abcdef"));
        Cipher encCipher = null;
        String cipherText = null;
        try {
            encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            encCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            cipherText = new String(encCipher.doFinal(contents.getBytes()));
        } catch (Exception e) {
            return false;
        }
        
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].writeFile(path, cipherText)) return false;
        }
        ShamirSecret ss = new ShamirSecret(3, clients.length, prime);
        String[] keyParts = ss.split(DatatypeConverter.printHexBinary(rawKey));
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].createFile(path + ".key")) return false;
            if (!clients[i].writeFile(path + ".key", keyParts[i])) return false;
        }
        return true;
    }
    
    public boolean makeDir(String dirPath) throws IOException, OncRpcException  {
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].makeDir(dirPath)) return false;
        }
        return true;
    }
    
    public boolean makeDirs(String dirPath) throws IOException, OncRpcException  {
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].makeDirs(dirPath)) return false;
        }
        return true;
    }
    
    public fattr getAttr(String path) throws IOException, OncRpcException {
        fattr[] attributes = new fattr[clients.length];
        for (int i=0; i<clients.length; i++) {
            attributes[i] = clients[i].getAttr(path);
        }
        for (int i=1; i<attributes.length; i++) {
            if ((attributes[i].size != attributes[0].size)
             || (attributes[i].type != attributes[0].type)) {
            System.err.println("Inconsistency: Data may be lost!");
            return null;
            }
            
        }
        return attributes[0];
    }
    
    public String readFile(String path) throws IOException, OncRpcException {
        String[] keyParts = new String[clients.length];
        for (int i=0; i<clients.length; i++) {
            keyParts[i] = clients[i].readFile(path + ".key");
        }
        String[] contents = new String[clients.length];
        for (int i=0; i<clients.length; i++) {
            contents[i] = clients[i].readFile(path);
        }
        for (int i=1; i<contents.length; i++) {
            if (!contents[i].equals(contents[0])) {
            	System.err.println("Inconsistency: Data may be lost!");
            	return null;
            }
        }
        ShamirSecret ss = new ShamirSecret(3, clients.length, prime);
        String rawKey = ss.recover(keyParts, nums);
        SecretKeySpec key = new SecretKeySpec(DatatypeConverter.parseHexBinary(rawKey), "AES");
        IvParameterSpec iv  = new IvParameterSpec(DatatypeConverter.parseHexBinary("0123456789abcdef0123456789abcdef"));
        Cipher decCipher = null;
        String plainText = null;
        try {
            decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            decCipher.init(Cipher.DECRYPT_MODE, key, iv);
            plainText = new String(decCipher.doFinal(contents[0].getBytes()));
        } catch (Exception e) {
            return null;
        }
        return plainText;
    }
    
}
