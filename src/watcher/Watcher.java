package watcher;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.LinkOption.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.HashMap;

import org.acplt.oncrpc.OncRpcException;
import nfsv1.NFSClient;

public class Watcher{
	private final String address;
	private final Path remoteDir;
	private final Path localDir;
	private final WatchService watcher;
	private final HashMap<WatchKey, Path> keys;
	private final boolean recursive;
	private final NFSClient nfsc;
	private final String username;

	public Watcher(String address, String remoteDir, String localDir, boolean recursive, int uid, int gid, String username) throws Exception {
		this.address = address;
		this.remoteDir = Paths.get(remoteDir);
		this.localDir = Paths.get(localDir);
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey,Path>();
		this.recursive = recursive;
		this.username = username;
		
		if (recursive) {
		    registerAll(this.localDir);
		} else {
		    register(this.localDir);
		}
		
		nfsc = new NFSClient(address, remoteDir, uid, gid, username, null);
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
        Files.walk(start, FileVisitOption.FOLLOW_LINKS).forEach(path -> {
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
        // parse arguments
        String host      = args.length > 1 ? args[0] : "localhost";
        String localDir  = args.length > 1 ? args[1] :"/Users/cornelius/Dropbox/USI courses/Eclipse work space/DS_project/NFS/test";
        String remoteDir = args.length > 1 ? args[2] : "/exports";
        int uid          = args.length > 1 ? Integer.parseInt(args[3]) : 502;
        int gid          = args.length > 1 ? Integer.parseInt(args[4]) : 20;
        String username  = System.getProperty("user.name");
        
        new Watcher(host, remoteDir, localDir, true, uid, gid, username).processEvents();
    
    }
};