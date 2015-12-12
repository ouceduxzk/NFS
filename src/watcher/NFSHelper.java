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

		1.c :
			i.   Create, read, and navigate folders
			ii.  Read and set attributes
			iii. Create, read and write files
 */

public class NFSHelper {
	private String _hostAddr;
	private String _remoteDir;
	private String _localPath;
	private nfsClient _nfsc;
	private mountClient _mnc;
	public NFSHelper(String hostAddr, String remoteDir, String localPath) {
		// TODO Auto-generated constructor stub
		_hostAddr = hostAddr;
		_remoteDir = remoteDir;
		_localPath = localPath;
		
	}

	
	/*
	 *  1.d restore the server on demand.
	 *  read files from server and write them locally
	 */
	public boolean restore(Path remotePath, Path localPath, String fn ){
		return false;
	}
}