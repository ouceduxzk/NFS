package nfsv1;

import java.io.*;
import java.net.InetAddress;
import java.util.*;

import org.acplt.oncrpc.*;

import client.mount.*;
import client.nfs.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.DatatypeConverter;

import java.nio.file.*;

public class NFSClient implements NFSClientInterface {
    private final int uid;
    private final int gid;
    private final String username;
    private final fhandle root;
    private final nfsClient nfs;
    private final Cipher encCipher;
    private final Cipher decCipher;
    private final SecretKeySpec key;
    private final IvParameterSpec iv;
    public boolean useAES;
  
    public NFSClient(String host, String mntPoint, int uid, int gid, String username, byte[] key) throws Exception {
        this.uid = uid;
        this.gid = gid;
        this.username = username;
        if (key == null) {
            this.key = new SecretKeySpec(DatatypeConverter.parseHexBinary("0123456789abcdef0123456789abcdef"), "AES");
        } else {
            this.key = new SecretKeySpec(key, "AES");
        }
        this.iv  = new IvParameterSpec(DatatypeConverter.parseHexBinary("0123456789abcdef0123456789abcdef"));
        this.encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        encCipher.init(Cipher.ENCRYPT_MODE, this.key, iv);
        this.decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decCipher.init(Cipher.DECRYPT_MODE, this.key, iv);
        this.useAES = true;
            
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
//      };
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
    
    public fhandle lookup(fhandle dir, String name) throws IOException, OncRpcException {
        return lookup(dir, new filename(name));
    }
    
    public fhandle lookup(String path) throws IOException, OncRpcException {
        assert (path.indexOf(0) == '/'); // path must start with '/'
        String [] parts = path.split("/");
        fhandle dir = root;
        for (int i = 1; i<parts.length; i++) {
            if (dir == null) return null;
            dir = lookup(dir, parts[i]);
        }
        return dir;
    }
    
    public class Parts {
        public final fhandle dir;
        public final filename name;
        public Parts(fhandle dir, filename file) {
            this.dir = dir;
            this.name = file;
        }
        public Parts(fhandle dir, String file) {
            this.dir = dir;
            this.name = new filename(file);
        }
    }
    
    public Parts lookup_parts(String path) throws IOException, OncRpcException {
        assert (path.indexOf(0) == '/'); // path must start with '/'
        String [] parts = path.split("/");
        fhandle dir = root;
        int i = 1;
        for (; i<parts.length-1; i++) {
            dir = lookup(dir, parts[i]);
        }
        return new Parts(dir, parts[i]);    
    }

//    attrstat NFSPROC_GETATTR (fhandle)
    //    union attrstat switch (stat status) {
    //    case NFS_OK:
    //            fattr attributes;
    //    default:
    //            void; 
    //        };

    public fattr getAttr(fhandle file) throws IOException, OncRpcException {
        if (file == null) return null;
        attrstat out = nfs.NFSPROC_GETATTR_2(file);
        if (out.status != stat.NFS_OK) {
            errorMessage(out.status);
        }
        return out.attributes;
    }
    
    public fattr getAttr(fhandle folder, filename filename) throws IOException, OncRpcException {
        return getAttr(lookup(folder, filename));
    }

    public fattr getAttr(fhandle folder, String filename) throws IOException, OncRpcException {
        return getAttr(lookup(folder, filename));
    }
    
    public fattr getAttr(String path) throws IOException, OncRpcException {
        return getAttr(lookup(path));
    }
    

//    struct sattrargs {
//            fhandle file;
//            sattr attributes;
//        };
//    attrstat
//    NFSPROC_SETATTR (sattrargs)
    public synchronized boolean setAttr(fhandle file, sattr attributes) throws IOException, OncRpcException {
        sattrargs args = new sattrargs();
        args.file = file;
        args.attributes = attributes;
        attrstat out = nfs.NFSPROC_SETATTR_2(args);
        if (out.status != stat.NFS_OK) {
            errorMessage(out.status);
        }
        return out.status == stat.NFS_OK;
    }
    
    public synchronized boolean setAttr(fhandle file, Integer uid, Integer gid, Integer mode, timeval atime, timeval mtime) throws IOException, OncRpcException {
        fattr old_attributes = getAttr(file);
        sattr new_attributes = new sattr();
        new_attributes.uid   = old_attributes.uid;
        new_attributes.gid   = old_attributes.gid;
        new_attributes.size  = old_attributes.size;
        new_attributes.atime = old_attributes.atime;
        new_attributes.mtime = old_attributes.mtime;
        new_attributes.mode  = old_attributes.mode;
        
        if (uid != null) {
            new_attributes.uid = uid;
        }
        if (gid != null) {
            new_attributes.gid = gid;
        }
        if (mode != null) {
            new_attributes.mode = mode;
        }
        if (atime != null) {
            new_attributes.atime = atime;
        }
        if (mtime != null) {
            new_attributes.mtime = mtime;
        }
        
        return setAttr(file, new_attributes);
    }
    
    public synchronized void errorMessage(int status) {
        switch (status) {
            case stat.NFS_OK:
                break;
            case stat.NFSERR_PERM:
                System.out.println("Bad permissions.");
                break;
            case stat.NFSERR_NOENT:
            case stat.NFSERR_EXIST:
                break; // small hack, ignore
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
    
    public synchronized boolean createFile(fhandle folder, String filename) throws IOException, OncRpcException {
        return createFile(folder, new filename(filename));
    }
    
    public synchronized boolean createFile(String path) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return createFile(p.dir, p.name);
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
    
    public synchronized boolean removeFile(fhandle folder, String filename) throws IOException, OncRpcException {
        return removeFile(folder, new filename(filename));
    }
    
    public synchronized boolean removeFile(String path) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return removeFile(p.dir, p.name);
    }
    
    public synchronized boolean removeFiles(fhandle folder, filename name) throws IOException, OncRpcException {
        fattr attributes = getAttr(folder, name);
        if (attributes.type != ftype.NFDIR) {
            removeFile(folder, name);
            return true;
        } else {
            List<entry> ls = readDir(folder, name);
            for (entry e: ls) {
                if (e.name.value.equals(".") || e.name.value.equals("..")) {
                    continue;
                } else {
                    removeFiles(lookup(folder, name), e.name);
                }
            }
            removeDir(folder,name);
        }
        return true;
    }
    
    public synchronized boolean removeFiles(String path) throws IOException, OncRpcException {
        Parts parts = lookup_parts(path);
        return removeFiles(parts.dir, parts.name);
    }
    
//  readFile   NFSPROC_READ_2
//    struct readargs {
//        fhandle file;
//        unsigned offset;
//        unsigned count;
//        unsigned totalcount;
//    };
//    union readres switch (stat status) {
//            case NFS_OK:
//                fattr attributes;
//                nfsdata data;
//            default:
//                void;
//    };
//    readres
//    NFSPROC_READ(readargs) = 6;

    public synchronized byte[] readFile(fhandle file) throws IOException, OncRpcException {
        readargs args = new readargs();
        args.file = file;
        args.offset = 0;
        args.count = getAttr(file).size;
        readres out = nfs.NFSPROC_READ_2(args);
        if (out.status != stat.NFS_OK) {
            errorMessage(out.status);
            return null;
        }
        if (useAES) {
            try {
                return decCipher.doFinal(out.read.data.value);
            } catch (Exception ex) {
                System.out.println("Tough luck: Decryption failed, your files might be lost forever!");
                System.exit(1);
            }
        }
        return out.read.data.value;
    }
    
    public synchronized String readFile(fhandle folder, filename filename) throws IOException, OncRpcException {;
        return new String(readFile(lookup(folder, filename)));
    }
    
    public synchronized String readFile(fhandle folder, String filename) throws IOException, OncRpcException {
        return new String(readFile(folder, new filename(filename)));
    }
    
    public synchronized String readFile(String path) throws IOException, OncRpcException {
        return new String(readFile(lookup(path)));
    }
    
    public synchronized byte[] rawReadFile(String path) throws IOException, OncRpcException {
        return readFile(lookup(path));
    }
    
//  writeFile  
//    struct writeargs {
//        fhandle file;
//        unsigned beginoffset;
//        unsigned offset;
//        unsigned totalcount;
//        nfsdata data;
//        };
//    attrstat
//    NFSPROC_WRITE(writeargs) = 8;
    public synchronized boolean writeFile(fhandle file, byte [] contents) throws IOException, OncRpcException {
        if (useAES) {
            try {
                contents = encCipher.doFinal(contents);
            } catch (Exception ex) {
                System.out.println("Encryption failed: Program terminated!");
                System.exit(1);
            }
        }
        writeargs args = new writeargs();
        args.file = file;
        args.offset = 0;
        args.data = new nfsdata(contents);
        attrstat out = nfs.NFSPROC_WRITE_2(args);
        if (out.status != stat.NFS_OK) {
            errorMessage(out.status);
        } 
        return out.status == stat.NFS_OK;
    }
    
    public synchronized boolean writeFile(fhandle folder, filename filename, byte[] contents) throws IOException, OncRpcException {
        return writeFile(lookup(folder,filename), contents);
    }
    
    public synchronized boolean writeFile(fhandle folder, filename filename, String contents) throws IOException, OncRpcException {
        return writeFile(lookup(folder,filename), contents.getBytes());
    }
    
    public synchronized boolean writeFile(fhandle file, String contents) throws IOException, OncRpcException {
        return writeFile(file, contents.getBytes());
    }
    
    public synchronized boolean writeFile(String path, String contents) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return writeFile(p.dir, p.name, contents);
    }
    
    public synchronized boolean rawWriteFile(String path, byte[] contents) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return writeFile(p.dir, p.name, contents);
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
    
    public synchronized boolean removeDir(fhandle folder, String dirname) throws IOException, OncRpcException {
        return removeDir(folder, new filename(dirname));
    }
    
    public synchronized boolean removeDir(String path) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return removeDir(p.dir, p.name);
    }
    
    public static Scanner execute(String[] cmd) throws IOException {
        Runtime r = Runtime.getRuntime();
        Scanner s = new Scanner(r.exec(cmd).getInputStream());
        return s;
    }
    
    public static int getUID () throws IOException {
        return NFSClient.execute(new String[] {"id", "-u"}).nextInt();
    }
    
    public static int getGID () throws IOException {
        return NFSClient.execute(new String[] {"id", "-g"}).nextInt();
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
    
    public synchronized boolean makeDir(fhandle folder, String dirname) throws IOException, OncRpcException {
        return makeDir(folder, new filename(dirname));
    }
    
    public synchronized boolean makeDir(String path) throws IOException, OncRpcException {
        Parts p = lookup_parts(path);
        return makeDir(p.dir, p.name);
    }
    
    public synchronized boolean makeDirs(String dirpath) throws IOException, OncRpcException {
        assert dirpath.indexOf(0) == '/'; // can only give absolute path
        String[] parts = dirpath.split("/");
        
        fhandle dir = root;
        for (int i = 1; i < parts.length; ++i) {
            fhandle subdir = lookup(dir, parts[i]);
            if (subdir != null) {
                fattr attributes = getAttr(subdir);
                if (attributes.type != ftype.NFDIR) {
                    System.out.format("Already exists but is not a directory %s\n", parts[i]);
                    return false;
                }
            } else {
                if (!makeDir(dir, parts[i])) {
                    return false;
                }
                subdir = lookup(dir, parts[i]);
                if (subdir == null) {
                    return false;
                }
            }
            dir = subdir;
        }
        return true;
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
    
    public synchronized List<entry> readDir(fhandle folder, filename filename) throws IOException, OncRpcException {
        return readDir(lookup(folder, filename));
    }
    
    public static byte[] generateKey() {
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
        } catch (Exception ex) {
            System.out.println("Could not generate key!");
            return null;
        }
        keyGenerator.init(128);
        SecretKey key = keyGenerator.generateKey();
        return key.getEncoded();
    }
    
    public static void writeKey(byte[] key, String filename) throws IOException {
        Files.write(Paths.get(filename), key, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    public static byte[] readKey(String filename) throws IOException {
        return Files.readAllBytes(Paths.get(filename));
    }
    
    public static byte[] readOrGenerateKey(String filename) throws IOException {
        if (Files.exists(Paths.get(filename))) {
            return readKey(filename);
        } else {
            byte[] key = generateKey();
            writeKey(key, filename);
            key = readKey(filename);
            return key;
        }
    }
    
//    cmd-line AES (for checking the encrypted files): 
//    encrypt: openssl aes-128-cbc -in file -out /dev/stdout -nosalt -K "0123456789abcdef0123456789abcdef" -iv "0123456789abcdef0123456789abcdef"
//    decrypt: openssl aes-128-cbc -d -in file -out /dev/stdout -nosalt -K "0123456789abcdef0123456789abcdef" -iv "0123456789abcdef0123456789abcdef"
    
    public static void main(String[] args) throws Exception {
        byte[] key;
        String keyPath = "/Users/cornelius/Dropbox/USI courses/Eclipse work space/DS_project/NFS/KEY";
        if (false) {
            key = NFSClient.generateKey();
            NFSClient.writeKey(key, keyPath);
        } else {
            key = NFSClient.readKey(keyPath);
        }            
            
        NFSClient client = new NFSClient("localhost", "/exports", NFSClient.getUID(), NFSClient.getGID(), System.getProperty("user.name"), key);
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
        if (client.removeFile(dir, "newfile")) {
            System.out.println("Successfully removed");
        } else {
            System.out.println("Could not remove");
        }
        
        System.out.println("--- Creating a new file newfile ---");
        if (client.createFile(dir, "newfile")) {
            System.out.println("Successfully created");
        } else {
            System.out.println("Could not create");
        }
    
        System.out.println("--- Writing to file newfile ---");
        if (client.writeFile(client.lookup(dir, "newfile"), "abcdefghijklmnopqrstuvwxyz")) {
            System.out.println("Successfully written to file");
        } else {
            System.out.println("Writing to file failed");
        }
        
        System.out.println("--- Reading from file newfile ---");
        String s = client.readFile(dir, "newfile");    
        if (!s.equals("")) {
            System.out.format("Successfully read from file: '%s'\n", s);
        } else {
            System.out.println("File read failed");
        }
        
        System.out.println("--- Removing directory newdir (if it exists) ---");
        if (client.removeDir(dir, "newdir")) {
            System.out.println("Successfully removed");
        } else {
            System.out.println("Could not remove");
        }
        
        System.out.println("--- Creating directory newdir ---");
        if (client.makeDir(dir, "newdir")) {
            System.out.println("Successfully created");
        } else {
            System.out.println("Could not create");
        }
        
        System.out.println("--- Creating directory from paths ---");
            
        client.makeDir("/a");
        client.makeDir("/a/b");
        client.makeDir("/a/b/c");
        client.makeDir("/a/b/c/d");
        client.createFile("/a/b/c/d/e");
        client.writeFile("/a/b/c/d/e", "12345");
        client.readFile("/a/b/c/d/e").equals("12345");
            
        client.makeDirs("/a/b/c/d/f/g");
        client.createFile("/a/b/c/d/f/g/h");
        client.writeFile("/a/b/c/d/f/g/h", "123abc");
        System.out.format("Contents: %s\n", client.readFile("/a/b/c/d/f/g/h"));
        client.removeFiles("/a"); 
    }
}