package nfsv1;

import java.io.*;
import java.net.InetAddress;
import java.util.*;

import org.acplt.oncrpc.OncRpcClientAuth;
import org.acplt.oncrpc.OncRpcClientAuthUnix;
import org.acplt.oncrpc.OncRpcClientStub;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.OncRpcProtocols;

import client.mount.*;
import client.nfs.*;

public class NFSClient {
	private final int uid;
	private final int gid;
	private final String username;
    private final fhandle root;
    private final nfsClient nfs;
  
    public NFSClient(String host, String mntPoint, int uid, int gid, String username) throws IOException, OncRpcException {
    		this.uid = uid;
    		this.gid = gid;
    		this.username = username;
        mountClient mnt = new mountClient(InetAddress.getByName(host), OncRpcProtocols.ONCRPC_UDP);
        OncRpcClientAuth auth = new OncRpcClientAuthUnix(username, uid, gid);
        mnt.getClient().setAuth(auth);
        fhstatus fh = mnt.MOUNTPROC_MNT_1(new dirpath(mntPoint));
        if (fh.status == 0) {
            byte[] fhandle = fh.directory.value;
            root = new fhandle(fhandle);
            nfs = new nfsClient(InetAddress.getByName(host), OncRpcProtocols.ONCRPC_UDP);
            nfs.getClient().setAuth(auth);
        } else {
            root = null;
            nfs = null;
        }
    }

    public fhandle getRoot() {
        return root;
    }

    public nfsClient getNfs() {
        return nfs;
    }

//    diropres NFSPROC_LOOKUP(diropargs)
//    union diropres switch (stat status) {
//    case NFS_OK:
//        struct {
//            fhandle file;
//            fattr   attributes;
//        } diropok;
//    default:
//        void;
//	  };
//    diropargs
//    struct diropargs {
//        fhandle  dir;
//        filename name;
//    };
    public fhandle lookup(fhandle dir, filename name) throws IOException, OncRpcException {
    		diropargs args = new diropargs();
    		args.dir = dir;
    		args.name = name;
    		diropres out = nfs.NFSPROC_LOOKUP_2(args);
    		if (out.status != stat.NFS_OK) {
    			errorMessage(out.status);
    			return null;
    		}
        return out.diropok.file;
    }

//    attrstat NFSPROC_GETATTR (fhandle)
	//	union attrstat switch (stat status) {
	//    case NFS_OK:
	//    		fattr attributes;
	//    default:
	//    		void; 
	//    	};

    public fattr getAttr(fhandle file) throws IOException, OncRpcException {
    		attrstat out = nfs.NFSPROC_GETATTR_2(file);
    		if (out.status != stat.NFS_OK) {
			errorMessage(out.status);
    		}
        return out.attributes;
    }
    
    public synchronized void errorMessage(int status) {
	    	switch (status) {
			case stat.NFS_OK:
				break;
			case stat.NFSERR_PERM:
				System.out.println("Bad permissions.");
				break;
			case stat.NFSERR_EXIST:
				System.out.println("Directory already exists.");
				break;
			default:
				System.out.format("Other error: %d\n", status);
	    	}
    }

//  createFile NFSPROC_CREATE_2
    public synchronized boolean createFile(fhandle folder, filename filename) throws IOException, OncRpcException {
    		diropargs where = new diropargs();
    		where.dir = folder;
    		where.name = filename;
    		sattr attributes = new sattr();
    		attributes.uid = uid;
    		attributes.gid = gid;
    		attributes.size = -1;
    		attributes.mode = 0777; // rw- rw- rw-
    		timeval now = new timeval();
    		now.seconds = (int)(System.currentTimeMillis() / 1000);
    		now.useconds = 0;
    		attributes.atime = attributes.mtime = now;
    		
    		createargs args = new createargs();
		args.where = where;
		args.attributes = attributes;
		
		diropres out = nfs.NFSPROC_CREATE_2(args);
		errorMessage(out.status);
    		return out.status == stat.NFS_OK;
    }
    
//  removeFile stat NFSPROC_REMOVE_2
    public synchronized boolean removeFile(fhandle folder, filename filename) throws IOException, OncRpcException {
		diropargs args = new diropargs();
		args.dir = folder;
		args.name = filename;
		
		int status = nfs.NFSPROC_REMOVE_2(args);
		
		errorMessage(status);
    		return status == stat.NFS_OK;
    }
    
//  readFile   NFSPROC_READ_2
    public synchronized String readFile(fhandle file) throws IOException, OncRpcException {
		
    	
    		return "";
    }
    
//  writeFile  NFSPROC_WRITE_2
    public synchronized boolean writeFile(fhandle file, String contents) throws IOException, OncRpcException {
		return false;
    }
    
//  removeDir  NFSPROC_RMDIR_2
    public synchronized boolean removeDir(fhandle folder, filename dirname) throws IOException, OncRpcException {
    	diropargs args = new diropargs();
    	args.dir = folder;
    	args.name = dirname;
    	
    	int status = nfs.NFSPROC_RMDIR_2(args);	
    	
    	errorMessage(status);
	return status == stat.NFS_OK;
    }
    
    public synchronized boolean makeDir(fhandle folder, filename dirname) throws IOException, OncRpcException {
    		diropargs where = new diropargs();
    		where.dir = folder;
    		where.name = dirname;
    		
    		sattr attributes = new sattr();
    		attributes.uid = uid;
    		attributes.gid = gid;
    		attributes.size = -1;
    		attributes.mode = 0777;
    		timeval mtime = new timeval();
    		mtime.seconds = (int)(System.currentTimeMillis() / 1000);
    		mtime.useconds = 0;
    		attributes.atime = attributes.mtime = mtime;
    	
    		createargs args = new createargs();
    		args.where = where;
    		args.attributes = attributes;
    		
    		diropres out = nfs.NFSPROC_MKDIR_2(args);
    		errorMessage(out.status);
    		return out.status == stat.NFS_OK;
    }

    public synchronized List<entry> readDir(fhandle folder) throws IOException, OncRpcException {
        List<entry> entries = new ArrayList<entry>();

        readdirargs in = new readdirargs();
        in.dir = folder;
        in.cookie = new nfscookie(new byte[] {0,0,0,0});
        in.count = 8000;

        readdirres out = nfs.NFSPROC_READDIR_2(in);

        if (out.status == stat.NFS_OK) {
            entry e = out.readdirok.entries;

            while ( e != null) {
                entries.add(e);
                e = e.nextentry;
            }
        }

        return entries;
    }
    
    public static void main(String[] args) throws IOException, OncRpcException {
    		NFSClient client = new NFSClient("192.168.0.16", "/exports", 502, 20, "cornelius");
        assert(client.nfs != null);
        assert(client.root != null);
        fhandle dir = client.getRoot();
        List<entry> ls = client.readDir(dir);
        
        System.out.println("--- Listing contents of root directory ---");
        ls.stream().forEach(e -> {
	        	try {
	        		if (e.name.value.equals("..")) return;
		        	fhandle file = client.lookup(dir, e.name); // get fhandle to get attributes below
		        	if (file == null) return;
		        	fattr attr = client.getAttr(file); // to get attributes
		        	if (attr == null) return;
		            switch (attr.type) {
		                 // use attributes to differentiate, e.g. if directory or not
		            		case ftype.NFREG:
		            			System.out.println(e.name.value);
		            			break;
		            		case ftype.NFDIR:
		            			System.out.format("%s/\n", e.name.value);
		            			break;
		            		default:
		            			System.out.println(e.name.value);
		            }
	        	} catch (IOException | OncRpcException ex) {
	        		
	        	}
        	});
        
        System.out.println("-- Removing file newfile (if it exists) ---");
        if (client.removeFile(dir, new filename("newfile"))) {
        		System.out.println("Successfully removed");
        } else {
        		System.out.println("Could not remove");
        }
        
        System.out.println("--- Creating a new file newfile ---");
        if (client.createFile(dir, new filename("newfile"))) {
        		System.out.println("Successfully created");
    		} else {
    			System.out.println("Could not create");
    		}
        
        System.out.println("--- Removing directory newdir (if it exists) ---");
        if (client.removeDir(dir, new filename("newdir"))) {
        		System.out.println("Successfully removed");
        } else {
        		System.out.println("Could not remove");
        }
        
        System.out.println("--- Creating directory newdir ---");
        if (client.makeDir(dir, new filename("newdir"))) {
        		System.out.println("Successfully created");
        } else {
        		System.out.println("Could not create");
        }
    }
    
    //	read by spliting and save
    // if a file is missing, remove them
    // compile java -cp lib/sss-0.1.jar:
	//    sss.join(size, ret, bbVector.toarray)())
	//    
	//    
	//    ByteBuffer inb = sss.readFile(fName);
	//    int[] ids = new int[splits];
	//    ByteBuffer[] out = new ByteBuffer[splits];
	//    	
	//    inb.rewind();
	//    sss.split(inb.capacity(), inb, minSplits, ids, out);
}