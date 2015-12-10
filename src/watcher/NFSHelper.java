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

/*
 * this implements 
 * 		1.b :
 * 			A MOUNT client able to retrieve the file handle of the share root
		1.c :
			i.   Create, read, and navigate folders
			ii.  Read and set attributes
			iii. Create, read and write files
 */

public class NFSHelper {
	private String _hostAddr;
	private String _remoteDir;
	private Path _localPath;
	private nfsClient _nfsClient;
	private mountClient _mountClient;
	private fhandle _thisFH;
	public NFSHelper(String hostAddr, String remoteDir, Path localPath) {
		// TODO Auto-generated constructor stub
		_hostAddr = hostAddr;
		_remoteDir = remoteDir;
		_localPath = localPath;
		
	}

	public void mount() throws UnknownHostException, OncRpcException, IOException{
		//create an object of mountClient
        mountClient mnt = new mountClient(InetAddress.getByName(_hostAddr), OncRpcProtocols.ONCRPC_UDP);
        //TODO Authentification later
        
        // mount point of remote directory
        dirpath mntP = new dirpath(_remoteDir);
        fhstatus fh = null;
        try {
        	fh = mnt.MOUNTPROC_MNT_1(mntP);
        }catch(OncRpcException e){
        	e.printStackTrace();
        }finally{
        	System.out.println("mounting remtoe director " + _remoteDir);
        }
        
        // check the status of fh is 0 or not 
        if (fh.status == 0){
        	// get the fhandel byte[] value
        	byte[] value = fh.directory.value;
        	_thisFH  = new fhandle(value);
        	_nfsClient = new nfsClient(InetAddress.getByName(_hostAddr), OncRpcProtocols.ONCRPC_UDP);
        }else{
        	System.exit(0);
        	System.out.println("Can not get the right filehandle");
        }
        
	}
	
	
	/*
	 *  method crate a directory
	 *  return : a boolean value of success or not.
	 *  @params : 
	 *  	1. Path localPath
	 *  	2. directory name
	 */
	public boolean makeDir(Path localPath, String name){
		
		return false;
	}
	
	/*
	 * method write to a file
	 * @param : 
	 * 		1. Path path
	 * 		2. String filename 
	 * return : a boolean value of success or not
	 */
	public boolean writeFile(Path path, String fn){
		return false;
		
	}
	
	public boolean readFile(Path path, String fn){
		return false;
		
	}
	
	public boolean createFile(Path path, String fn){
		return false;
	}
	
	
	/*
	 *  1.d restore the server on demand.
	 *  read files from server and write them locally
	 */
	public boolean restore(Path remotePath, Path localPath, String fn ){
		return false;
	}
}