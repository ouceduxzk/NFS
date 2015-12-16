package nfsv1;

import java.io.IOException;
import client.nfs.*;
import org.acplt.oncrpc.OncRpcException;

public interface NFSClientInterface {
    public boolean createFile(String path) throws IOException, OncRpcException;
    public boolean writeFile(String path, String contents) throws IOException, OncRpcException;
    public boolean makeDir(String path) throws IOException, OncRpcException;
    public boolean makeDirs(String dirPath) throws IOException, OncRpcException;
    public fattr   getAttr(String path) throws IOException, OncRpcException;
    public String  readFile(String path) throws IOException, OncRpcException;
}
