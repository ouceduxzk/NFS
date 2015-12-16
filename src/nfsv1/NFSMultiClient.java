package nfsv1;

import java.io.IOException;

import org.acplt.oncrpc.OncRpcException;

public class NFSMultiClient implements NFSClientInterface {
    private NFSClient[] clients;
    
    public NFSMultiClient(String[] hosts, String[] mntPoints, int uid, int gid, String username, byte[] key) throws Exception {
        assert hosts.length == mntPoints.length;
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
    
}
