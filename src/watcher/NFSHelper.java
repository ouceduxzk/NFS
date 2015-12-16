package watcher;
import java.nio.file.*;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.acplt.oncrpc.OncRpcClientAuth;
import org.acplt.oncrpc.OncRpcClientAuthUnix;
import org.acplt.oncrpc.OncRpcClientStub;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.OncRpcProtocols;

import client.mount.*;
import client.nfs.*;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import nfsv1.NFSClient;


/*
 *  1.d restore  on demand.
 *  read files from server and write them locally
 */

public class NFSHelper {
    private final String host;
    private final String mountDir;
    private final int uid;
    private final int gid;
    private final String username;
	private final NFSClient nfsc;
	public NFSHelper(String host, String mountDir, int uid, int gid, String username, String key) throws Exception {
		this.host     = host;
		this.mountDir = mountDir;
		this.uid      = uid;
		this.gid      = gid;
		this.username = username;
		byte[] keyData = NFSClient.readOrGenerateKey(key);
		
		nfsc = new NFSClient(host, mountDir, uid, gid, username, keyData);
	}

	public void restore(String remotePath, String localPath) throws IOException, OncRpcException {
	    if (!localPath.startsWith("/")) {
	        System.out.println("Please provide the path as an absolute path (starting with '/'), rooted at the NFS mount!");
	        return;
	    }
	    System.out.format("Restore %s at %s\n", remotePath, localPath);
	    fattr attributes = nfsc.getAttr(remotePath);
	    if (attributes == null) {
	        System.err.println("No such path!");
	        return;
	    }
	    List<String> parts = Arrays.asList(remotePath.split("/"));
	    Path dir = Paths.get(localPath).resolve(String.join("/", parts.subList(1, parts.size() -1)));
	    Path entity = Paths.get(localPath).resolve(String.join("/", parts.subList(1, parts.size())));
	    System.out.format("Creating '%s' directory\n", dir);
	    Files.createDirectories(dir);
	    switch (attributes.type) {
	        case ftype.NFREG:
	            String contents = nfsc.readFile(remotePath);
	            Files.write(entity, contents.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	            break;
	        case ftype.NFDIR:
	            Files.createDirectories(entity);
	            break;
	        default:
	            System.err.format("Unsupported file type: %d\n", attributes.type);
	    }
	}


	public static void main(String[] args) throws Exception {
	    ArgumentParser parser = ArgumentParsers.newArgumentParser("Watcher")
                .description("This program watches a local folder and propagates all changes to an NFSServer");
        parser.addArgument("-H", "--host").help("This is the NFSServer host").setDefault("localhost");
        parser.addArgument("-r", "--remote").help("This is the remote directory, to which you propagate changes")
                                            .setDefault("/exports");
        parser.addArgument("-l", "--local").help("This is the local directory, from which you propagate changes to the server")
                                           .setDefault("test");
        parser.addArgument("-k", "--key").help("This is the AES key: generates key to that filename, if it doesn't exist yet")
                                         .setDefault("KEY");
        parser.addArgument("path").help("This/these is/are the path(s) to restore").nargs("+");
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException ex) {
            parser.handleError(ex);
            System.exit(1);
        }
	    
	    String host       = ns.getString("host");
        String remoteDir  = ns.getString("remote");
        String localDir   = ns.getString("local");
        int uid           = NFSClient.getUID();
        int gid           = NFSClient.getGID();
        String username   = System.getProperty("user.name");
        String key        = ns.getString("key");
        
        NFSHelper nfsh   = new NFSHelper(host, remoteDir, uid, gid, username, key);
        for (String path : ns.<String> getList("path")) {
            nfsh.restore(path, localDir);
        }
    }
}
