README
======
Group Members: Zaikun Xu
			   Titus von Köller

BUILD INSTRUCTIONS
------------------
build with Eclipse:
	in Eclipse do File->Import->Project->FromGit
	should build without errors

dependencies:
	OncRpc
	ArgParse4j.1.6.0

RUN INSTRUCTIONS
----------------
from the project directory:
	- use this to start the Watcher, watching a directory called test into ten servers at ip-address 192.168.56.101, with mount points /exports/s0 to /exports/s9. This will use Shamir's Secret Sharing; a prime number will be stored to a file called PRIME. 
	$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.Watcher $(for i in $(seq 0 9); do echo -s 192.168.56.101:/exports/s$i; done) -l test -u 1000 -g 1000 -n $(whoami)

	-use this to restore a file with the Helper, reconstructing it from 3 randomly chosen servers at ip-address 192.168.56.101 with mount points /exports/s0 to /exports/s0. This will use Shamir's secret sharing and load the prime number stored in the file called PRIME. Note: The path to which the FILE will be stored must start with a '/'.
	$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper $(for i in $(seq 0 9 | shuf | head -n3); do echo -s $i:192.168.56.101:/exports/s$i; done) -l restore -u 1000 -g 1000 -n $(whoami) /FILE

COMMAND HELP
------------
$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.Watcher -h
usage: Watcher [-h] [-H HOST] [-r REMOTE] [-l LOCAL] [-k KEY] [-p SSSPRIME] [-s SSSHOST] [-u UID] [-g GID] [-n USERNAME]

This program watches a local folder and propagates all changes to an NFSServer

optional arguments:
  -h, --help             show this help message and exit
  -H HOST, --host HOST   This is the NFSServer host
  -r REMOTE, --remote REMOTE
                         This is the remote directory, to which you propagate changes
  -l LOCAL, --local LOCAL
                         This is the local directory, from which you propagate changes to the server
  -k KEY, --key KEY      This is the AES key: generates key to that filename, if it doesn't exist yet
  -p SSSPRIME, --sssprime SSSPRIME
                         These are the Shamir's Secret Sharing prime number filename; writes generated prime, if it doesn't exist yet
  -s SSSHOST, --ssshost SSSHOST
                         These are the Shamir's Secret Sharing host specs (hostname:remoteDir)
  -u UID, --uid UID      The user ID
  -g GID, --gid GID      The group ID
  -n USERNAME, --username USERNAME
                         The username

$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper -h
usage: Watcher [-h] [-H HOST] [-r REMOTE] [-l LOCAL] [-k KEY] [-p SSSPRIME] [-s SSSHOST] [-u UID] [-g GID] [-n USERNAME] path [path ...]

This program watches a local folder and propagates all changes to an NFSServer

positional arguments:
  path                   This/these is/are the path(s) to restore

optional arguments:
  -h, --help             show this help message and exit
  -H HOST, --host HOST   This is the NFSServer host
  -r REMOTE, --remote REMOTE
                         This is the remote directory, to which you propagate changes
  -l LOCAL, --local LOCAL
                         This is the local directory, from which you propagate changes to the server
  -k KEY, --key KEY      This is the AES key: generates key to that filename, if it doesn't exist yet
  -p SSSPRIME, --sssprime SSSPRIME
                         These are the Shamir's Secret Sharing prime number filename; writes generated prime, if it doesn't exist yet
  -s SSSHOST, --ssshost SSSHOST
                         These are the Shamir's Secret Sharing host specs (hostno:hostname:remoteDir)
  -u UID, --uid UID      The user ID
  -g GID, --gid GID      The group ID
  -n USERNAME, --username USERNAME
                         The username

SERVER SETUP
============

To simulate multiple servers, we can export multiple mount points from a single physical server. Note: We do not know how to do this on OSX, on linux input the following commands into the terminal and set /etc/exports to the following

$ sudo mkdir /exports
$ sudo mkdir /exports/s{0..9}
$ ip addr ### change ipaddr accordingly
$ vim /etc/exports >(put this in file)>
		/exports     192.168.56.0/24(rw,fsid=0,insecure,no_subtree_check,async)
		/exports/s0  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s1  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s2  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s3  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s4  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s5  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s6  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s7  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s8  192.168.56.0/24(rw,insecure,no_subtree_check,async)
		/exports/s9  192.168.56.0/24(rw,insecure,no_subtree_check,async)
$ sudo apt-get install tree
$ sudo apt-get install nfs-common nfs-kernel-server	
$ cat /etc/exports
$ showmount -e
$ cd /tmp && mkdir nfs-mount && sudo mount 192.168.56.101:/exports/s0 nfs-mount && sudo touch /exports/s0/abc && tree nfs-mount
$ sudo chown -R $(whoami):$(whoami) /exports && sudo chmod -R ugo+rwX /exports
$ ls -1al /exports/*  ### have to all be owned by user

SHAMIR'S SECRET SHARING
=======================
For Shamir Secret Sharing, you need to split a single data item into multiple pieces, and give one piece each to multiple recipients. 
In this case, you take a single file, encrypt it with an AES key, then split that AES key into >3 parts. You give of the encrypted file and one piece of the AES key to multiple servers.
Each server has one piece of the key and a copy of the encrypted file. All copies of the encrypted file are identical. Each piece of the key is different. You need 3 pieces of the key to reconstruct the original whole AES key, and you cannot decrypt any of the copies of the file without the intact, whole AES key. You can simulate multiple NFS servers by having one physical computer export multiple NFS mount points.
From the perspective of the Watcher or the Helper, there is no difference if you have multiple NFSClients connected to [192.168.1.100:/exports, 192.168.1.101:/exports, 192.168.1.102:/exports, 192.168.1.103:/exports, 192.168.1.104:/exports] or to [192.168.1.100:/exports0, 192.168.1.100:/exports1, 192.168.1.100:/exports2, 192.168.1.100:/exports3, 192.168.1.100:/exports4]
In the former case, you have four physical servers connected to the network with four unique IP addresses, each exporting the /exports path.
With five unique IP addresses, that is.
In the latter case, you have one physical server connected to the network with one unique IP address, exporting five /exports{0..4} paths. When you backup a file using SSS with the latter setup, you AES encrypt the file to create AES(file). You send AES(file) to each NFS mount and end up with five copies of the exact same data in each of the /exports{0..4} directories.
You take the key and use SSS to split it into {key0, key1, ..., key4} and send these to each NFS mount. You end up with key0 stored in /exports0, key1 stored in /exports1, ..., key4 stored in /exports4.

TESTING
=======
Watcher, Client, and Helper were tested in SSS-mode as following:
	1. $ cd NFS

	2. $ mkdir test restore

	3. $ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.Watcher $(for i in $(seq 0 9); do echo -s 192.168.56.101:/exports/s$i; done) -l test -u 1000 -g 1000 -n $(whoami)

	4. ### simple test
    	$ gshuf /usr/share/dict/words | head -n 100 > words

	5. ### large file test
	    $ gshuf /usr/share/dict/words | head -n 10000 > words.2

	6. ### multi file test
	    $ for i in $(gseq 1 100); do gshuf /usr/share/dict/words | head -n 10000 > words.multi.$i; done

	7. ### directory test
	    $ mkdir -p a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p
	    $ for i in $(gseq 1 100); do gshuf /usr/share/dict/words | head -n 10000 > a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/words.$i; done

	8. ### check steps
		# simple test #
			$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper $(for i in $(seq 0 9 | shuf | head -n3); do echo -s $i:192.168.56.101:/exports/s$i; done) -l restore /words && diff -s {test,restore}/words 
		
		# large file test #
			$ java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper $(for i in $(seq 0 9 | shuf | head -n3); do echo -s $i:192.168.56.101:/exports/s$i; done) -l restore /words && diff -s {test,restore}/words.2 
		
		# multi file test #
			$ for i in $(gseq 1 100); do java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper $(for i in $(seq 0 9 | shuf | head -n3); do echo -s $i:192.168.56.101:/exports/s$i; done) -l restore /words.multi.$i && diff -s {test,restore}/words.multi.$i; done 
		
		# directory test #
			$ for i in $(gseq 1 100); do java -classpath bin:lib/oncrpc.jar:lib/argparse4j-0.6.0.jar watcher.NFSHelper $(for i in $(seq 0 9 | shuf | head -n3); do echo -s $i:192.168.56.101:/exports/s$i; done) -l restore /a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/words.$i && diff -s {test,restore}/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/words.$i; done 






















