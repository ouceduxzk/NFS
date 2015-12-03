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

public class Watcher{
	private String _address;
	private String _remoteDir;
	private String _localDir;
	private final WatchService _watcher;
	private Path _localPath;
	private HashMap<WatchKey, Path> _keys;
	private boolean _recursive = true ;
	private boolean _trace = true;
	private ArrayList<Watcher> _listofWatcher;
	public Watcher(String address, String remoteDir, String localDir, boolean recursive) throws IOException{
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
		WatchKey key = _localPath.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		
		 _listofWatcher = new ArrayList<Watcher>();
		initRegister();
	}
	public void initRegister() throws IOException {
	        if (_recursive) {
	            System.out.format("Scanning %s ...\n", _localDir);
	            registerAll(_localDir);
	            System.out.println("Done.");
	        } else {
	            register(_localDir);
	        }

	        // enable trace after initial registration
	        _trace = true;
	}
	
	
//	
//	public void Register() throws IOException{
//		File file = _localPath.toFile();
//		
//		for(File f : file.listFiles() ){
//			if(f.isDirectory()){
//				Watcher newwatcher = new Watcher(_address, _remoteDir, f.getName());
//				_listofWatcher.add(newwatcher);
//				newwatcher.startWatch();	
//			}
//		}
//	}
	
	private void register(String _localDir2) throws IOException{
		// TODO Auto-generated method stub
		
	}

	private void registerAll(String _localDir2) throws IOException{
		// TODO Auto-generated method stub
		
	}

	public void startWatch(){
		
	}

}