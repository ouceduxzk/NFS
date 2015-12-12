package watcher;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;


import org.acplt.oncrpc.OncRpcClientAuth;
import org.acplt.oncrpc.OncRpcClientAuthUnix;
import org.acplt.oncrpc.OncRpcClientStub;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.OncRpcProtocols;

import client.mount.dirpath;
import client.nfs.fhandle;
import client.nfs.filename;
import nfsv1.NFSClient;

public class Watcher{
	private String _address;
	private String _remoteDir;
	private String _localDir;
	private final WatchService _watcher;
	private Path _localPath;
	private HashMap<WatchKey, Path> _keys;
	private boolean _recursive = true ;
	private boolean _trace = true;
	private NFSClient _nfsc;
	private String _username = "zaikunxu";
	//private ArrayList<Watcher> _listofWatcher;
	public Watcher(String address, String remoteDir, String localDir, boolean recursive) throws IOException, OncRpcException{
		_address = address;
		_remoteDir =remoteDir;
		_localDir = localDir;
		_watcher = FileSystems.getDefault().newWatchService();
		_localPath = Paths.get(_localDir);
		_keys = new HashMap<WatchKey,Path>();
		_recursive = recursive;
		//192.168.0.16. my ip address
		// /Users/zaikunxu/Desktop/nfserver remote directory
		// /exports/                        local directory
		
		 //_listofWatcher = new ArrayList<Watcher>();
		
		_nfsc = new NFSClient(_address, _remoteDir, 501, 20, _username);
	
		initRegister();
	}
	public void initRegister() throws IOException {
	        if (_recursive) {
	            System.out.format("Scanning %s ...\n", _localDir);
	            registerAll(_localPath);
	            System.out.println("Done.");
	        } else {
	            register(_localPath);
	        }

	        // enable trace after initial registration
	        _trace = true;
	}

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (_trace) {
            Path prev = _keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        _keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Process all events for keys queued to the watcher
     * @throws OncRpcException 
     */
    void processEvents() throws OncRpcException {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = _watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = _keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (_recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                            //############ create the directory on local
                            //handle root = _nfsc.getRoot();
                            //_nfsc.makeDir(root, new filename(child.toString()));
                        }else{
                        	// a single file
                        	//fhandle root = _nfsc.getRoot();
                        	//_nfsc.createFile(root, new filename(child.toString()));
                        	register(child);
                        }
                        
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
                

                //TODO add ENTRY_CREATE and ENTRY_DELTE
                if(_recursive && (kind == ENTRY_DELETE)){
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            //############ create the directory on local
                            fhandle root = _nfsc.getRoot();
                            _nfsc.removeDir(root, new filename(child.toString()));
                        }else{
                        	// a single file
                        	fhandle root = _nfsc.getRoot();
                        	_nfsc.removeFile(root, new filename(child.toString()));
                        	
                        }
                        
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
                
                if(_recursive && (kind == ENTRY_MODIFY)){
                	
                }
                
              
                
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                _keys.remove(key);

                // all directories are inaccessible
                if (_keys.isEmpty()) {
                    break;
                }
            }
        }
    }
    
    private fhandle getFileHandle(Path _localPath2, String string) {
		// TODO Auto-generated method stub
		return null;
	}
	@SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
        public static void main(String[] args) throws IOException {
        // parse arguments
        String host = "localhost";
        String localDir = "/Users/zaikunxu/Desktop/local/";
        String remoteDir = "/exports";
    
        boolean recursive = true;
        new Watcher(host, remoteDir, localDir, recursive).processEvents();
    
    }
}