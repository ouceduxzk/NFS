package nfsv1;

import java.io.IOException;

import org.acplt.oncrpc.OncRpcException;

import client.nfs.fattr;

public class NFSMultiClient implements NFSClientInterface {
    private NFSClient[] clients;
    private int[] nums;
    
    public NFSMultiClient(int[] nums, String[] hosts, String[] mntPoints, int uid, int gid, String username, byte[] key) throws Exception {
        assert hosts.length == mntPoints.length;
        this.nums = nums;
        clients = new NFSClient[hosts.length];
        for (int i=0; i<hosts.length; i++) {
            clients[i] = new NFSClient(hosts[i], mntPoints[i], uid, gid, username, key);
        }
    }
    public boolean createFile(String path) throws IOException, OncRpcException {
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].createFile(path)) return false;
        }
        return true;
    }
    
    public boolean writeFile(String path, String contents) throws IOException, OncRpcException  {
        for (int i=0; i<clients.length; i++) {
            if (!clients[i].writeFile(path, contents)) return false;
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
            if ((attributes[i].size == attributes[0].size)
             || (attributes[i].type == attributes[0].type)) {
            System.err.println("Inconsistency: Data may be lost!");
            return null;
            }
            
        }
        return attributes[0];
    }
    
    public String readFile(String path) throws IOException, OncRpcException {
        String[] contents = new String[clients.length];
        for (int i=0; i<clients.length; i++) {
            contents[i] = clients[i].readFile(path);
        }
        for (int i=1; i<contents.length; i++) {
            if ((contents[i].equals(contents[0]))) {
            System.err.println("Inconsistency: Data may be lost!");
            return null;
            }
            
        }
        return contents[0];
    }
    
}
