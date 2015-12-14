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
import nfsv1.NFSClient;


/*
 *  1.d restore  on demand.
 *  read files from server and write them locally
 */

public class NFSHelper {
//	private String _address;
//	private String _remoteDir;
	//private String _localPath;
	private NFSClient _nfsc;
	public NFSHelper(NFSClient nfsc) throws IOException, OncRpcException {
		// TODO Auto-generated constructor stub
		//_localPath = localPath;
		_nfsc = nfsc;
	}

	private void writeLocal(String localDir, String content, String fn) throws IOException {
		String joinedPath = new File(localDir, fn).toString();
		PrintWriter out = new PrintWriter(joinedPath);
		out.write(content);
		out.close();
	}
	
	public void restore(String localDir, String fn ) throws IOException, OncRpcException{
		String content = _nfsc.readFile(fn);
		writeLocal(localDir, content, fn);
	}
	
	
}