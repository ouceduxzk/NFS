package watcher;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

import org.acplt.oncrpc.OncRpcException;

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
                //System.out.println("debug  " + child.toString());
                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (_recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                            //############ create the directory on server
                            ///////////////////////////////////////////
                            
                            
                            
                           _nfsc.makeDir(child.toString());
                           
                           
                           
                           
                           
                           
                           
                           
                           
                        }else{
                        	// a single file
                        	_nfsc.createFile(child.toString());
                        	// do not know what the content is                         	
                        	//_nfsc.writeFile(file, contents);
                        	
                        	register(child);
                        }
                        
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    	x.printStackTrace();
                    }
                }
                

                if(_recursive && (kind == ENTRY_MODIFY)){
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                           _nfsc.makeDir(child.toString());
                        }else{
                        	//_nfsc.writeFile(child.toString());
                        	register(child);
                        }
                        
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    	x.printStackTrace();
                    }
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

    
	@SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
   
	public static void main(String[] args) throws IOException, OncRpcException {
        // parse arguments
        String host = "localhost";
        String localDir = "/Users/zaikunxu/Desktop/local";
        String remoteDir = "/exports";
    
        boolean recursive = true;
        new Watcher(host, remoteDir, localDir, recursive).processEvents();
    
    }
}