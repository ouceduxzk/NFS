package watcher;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.LinkOption.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.HashMap;
import java.util.Scanner;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;

import org.acplt.oncrpc.OncRpcException;
import nfsv1.NFSClient;

public class Watcher{
	private final String host;
	private final Path remoteDir;
	private final Path localDir;
	private final WatchService watcher;
	private final HashMap<WatchKey, Path> keys;
	private final boolean recursive;
	private final String username;
	private final NFSClient nfsc;

	public Watcher(String host, String remoteDir, String localDir, boolean recursive, int uid, int gid, String username, String key) throws Exception {
		this.host      = host;
		this.remoteDir = Paths.get(remoteDir);
		this.localDir  = Paths.get(localDir);
		this.watcher   = FileSystems.getDefault().newWatchService();
		this.keys      = new HashMap<WatchKey,Path>();
		this.recursive = recursive;
		this.username  = username;
		byte[] keyData = NFSClient.readOrGenerateKey(key);
		
		if (recursive) {
		    registerAll(this.localDir);
		} else {
		    register(this.localDir);
		}
		
		nfsc = new NFSClient(host, remoteDir, uid, gid, username, keyData);
	}

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        System.out.format("Registering %s\n", dir);
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(Path start) throws IOException {
        // register directory and sub-directories
        Files.walk(start).forEach(path -> {
            try {
                register(path);
            } catch (Exception e) {}
        });
    }

    /**
     * Process all events for keys queued to the watcher
     * @throws OncRpcException 
     */
    void processEvents() throws IOException, OncRpcException {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // Context for directory entry event is the file name of entry
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                Path localDir = (Path)(key.watchable());
                Path localPath = localDir.resolve(name);
                String remotePath = "/" + this.localDir.relativize(localPath);
                System.err.format("Saw %s on %s\n\tlocalpath=%s\n\tremotepath=%s\n", kind.name(), name, localPath, remotePath);
                
                switch (kind.name()) {
                    case "ENTRY_CREATE":
                        if (Files.isDirectory(localPath)) {
                            registerAll(localPath);
                            nfsc.makeDirs(remotePath);
                            // make sure we create all sub-dirs too
                            Files.walk(localPath).forEachOrdered(p -> {
                                try {
                                    nfsc.makeDir("/" + this.localDir.relativize(p));
                                } catch (Exception e) {}
                            });
                        } else if (Files.isRegularFile(localPath)) {
                            nfsc.createFile(remotePath);
                            String contents = readFile(localPath);
                            nfsc.writeFile(remotePath, contents);
                        }
                        break;
                    case "ENTRY_DELETE":
                        System.err.println("Delete event ignored");
                        break;
                    case "ENTRY_MODIFY":
                        if (Files.isDirectory(localPath)) {
                            nfsc.makeDirs(remotePath);
                        } else if (Files.isRegularFile(localPath)) {
                            String contents = readFile(localPath);
                            nfsc.writeFile(remotePath, contents);
                        }
                        break;
                    case "OVERFLOW":
                        System.err.println("Event overflow detected");
                        break;
                    default:
                        System.err.format("Unknown event %s\n", kind.name());
                        break;
                }                
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

	String readFile(Path path) throws IOException {
	    return new String(Files.readAllBytes(path));
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
	    Namespace ns = null;
	    try {
	        ns = parser.parseArgs(args);
	    } catch (ArgumentParserException ex) {
	        parser.handleError(ex);
	        System.exit(1);
	    }
        // parse arguments
        String host      = ns.getString("host");
        String remoteDir = ns.getString("remote");
        String localDir  = ns.getString("local");
        String key       = ns.getString("key");
        int uid          = NFSClient.getUID();
        int gid          = NFSClient.getGID();
        String username  = System.getProperty("user.name");
        
        new Watcher(host, remoteDir, localDir, true, uid, gid, username, key).processEvents();
    
    }
};