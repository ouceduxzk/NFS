package watcher;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.acplt.oncrpc.*;
import client.nfs.*;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import nfsv1.*;


/*
 *  1.d restore  on demand.
 *  read files from server and write them locally
 */

public class NFSHelper {
	private final NFSClientInterface nfsc;
	public NFSHelper(String host, String mountDir, int uid, int gid, String username, String key) throws Exception {
		byte[] keyData = NFSClient.readOrGenerateKey(key);
		
		nfsc = new NFSClient(host, mountDir, uid, gid, username, keyData);
	}
	public NFSHelper(int[] sssNos, String[] sssHosts, String[] sssRemotes, int uid, int gid, String username, String prime) throws Exception {
	    
	    nfsc = new NFSMultiClient(sssNos, sssHosts, sssRemotes, uid, gid, username, prime);
	}

	public void restore(String remotePath, String localPath) throws IOException, OncRpcException {
	    if (!remotePath.startsWith("/")) {
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
        parser.addArgument("-p", "--sssprime").help("These are the Shamir's Secret Sharing prime number filename; writes generated prime, if it doesn't exist yet")
		  							     .setDefault("PRIME");
        parser.addArgument("-s", "--ssshost").help("These are the Shamir's Secret Sharing host specs (hostno:hostname:remoteDir)")
                                             .action(Arguments.append());
        parser.addArgument("-u", "--uid").help("The user ID").setDefault(NFSClient.getUID()).type(Integer.class);
        parser.addArgument("-g", "--gid").help("The group ID").setDefault(NFSClient.getGID()).type(Integer.class);
        parser.addArgument("-n", "--username").help("The username").setDefault(System.getProperty("user.name"));
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
        int uid           = ns.getInt("uid");
        int gid           = ns.getInt("gid");
        String username   = ns.getString("username");
        String key        = ns.getString("key");
        String prime      = ns.getString("sssprime");
        
        List<String> sssHostSpecs = ns.<String>getList("ssshost");
        int[]    sssNos     = null;
        String[] sssHosts   = null;
        String[] sssRemotes = null;
        if (sssHostSpecs != null) {
            sssNos     = new int[sssHostSpecs.size()];
            sssHosts   = new String[sssHostSpecs.size()];
            sssRemotes = new String[sssHostSpecs.size()];
            for (int i=0; i<sssHostSpecs.size();i++) {
                String hostSpec = sssHostSpecs.get(i);
                if (!hostSpec.contains(":")) {
                    System.err.format("Bad host spec: %s\nMust be of the form 'hostNo:hostname:remoteDir'\nfor example: '0:localhost:/exports'\n", hostSpec);
                    System.exit(1);
                } else {
                    sssNos[i]     = Integer.parseInt(hostSpec.split(":", 3)[0]);
                    sssHosts[i]   = hostSpec.split(":", 3)[1];
                    sssRemotes[i] = hostSpec.split(":", 3)[2];
                }
            }
            System.out.println(String.join("\n", new String[] {
                "","Using Shamir's Secret Sharing with multiple hosts.",
                "--host and --remote arguments are ignored!",
                "Using the following hosts (Important: host numbers must be consistent with numbers provided by watcher),",
                "any three suffice to reconstruct the secret:"
                }));
            for (int i=0; i<sssHosts.length; i++) {
                System.out.format("No: %d, host: %s, remote: %s\n", sssNos[i], sssHosts[i], sssRemotes[i]);
            }
        }
        
        NFSHelper nfsh = null;
        if (sssHostSpecs != null) {
            nfsh = new NFSHelper(sssNos, sssHosts, sssRemotes, uid, gid, username, prime);
        } else {
            nfsh = new NFSHelper(host, remoteDir, uid, gid, username, key);
        }
        
        for (String path : ns.<String> getList("path")) {
            nfsh.restore(path, localDir);
        }
    }
}
