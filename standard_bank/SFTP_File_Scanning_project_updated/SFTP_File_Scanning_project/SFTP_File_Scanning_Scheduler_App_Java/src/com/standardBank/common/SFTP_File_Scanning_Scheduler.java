package com.standardBank.common;


import com.jcraft.jsch.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;



public class SFTP_File_Scanning_Scheduler {


    public static String getfiledetails(String sftphost, String sftpport, String sftpuser, String sftppassword, String sftprootdirectory) {
        String result = "";

        // SFTP connection details
        
        /*
        String sftpHost = "10.245.87.200";
        int sftpPort = 22; // Default SFTP port
        String sftpUser = "sftp-ace-metnet";
        String sftpPassword = "H*uphz3dnbcAh3ET";
        String sftpRootDirectory = "/sftp-meticalnet";
        */
        
        /*
        String sftpHost = "192.168.1.115";
        int sftpPort = 22; // Default SFTP port
        String sftpUser = "mqm";
        String sftpPassword = "sarasu10";
        String sftpRootDirectory = "/home/mqm/sftp-meticalnet";
        */
        
        
        String sftpHost = sftphost;
        int sftpPort =Integer.parseInt(sftpport); // Default SFTP port
        String sftpUser = sftpuser;
        String sftpPassword = sftppassword;
        String sftpRootDirectory = sftprootdirectory;
        
        

        // Subdirectories to fetch files from
       String[] targetDirectories = { "creditGuarantees", "creditMasterData", "creditOperations", "warranties" };
        
        
        StringBuilder filenames = new StringBuilder();
        StringBuilder missingDirectories = new StringBuilder();

        // List to store files last modified 24 hours or more ago
        List<String> filesModifiedMoreThanOrExactlyHourAgo = new ArrayList<>();

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            // Set up the JSch instance
            JSch jsch = new JSch();

            // Create a session
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPassword);

            // Disable strict host key checking (not recommended for production)
            session.setConfig("StrictHostKeyChecking", "no");

            // Connect to the server
            session.connect();

            // Open the SFTP channel
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            
            // check whether the root directory exists or not
            try {
                channelSftp.cd(sftpRootDirectory);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    return "FNE"; // Folder Not Exists
                } else {
                	 return "FAIL: " + e.getMessage(); // Handle other exceptions as they are
                }
            }
            

            // Fetch files only from specified subdirectories
            for (String subdirectory : targetDirectories) {
                String fullPath = sftpRootDirectory + "/" + subdirectory;
                try {
                    fetchFilesFromDirectory(channelSftp, fullPath, filesModifiedMoreThanOrExactlyHourAgo);
                } catch (SftpException e) {
                    // If directory does not exist, mark it as missing
                	if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE && fullPath.equals(sftpRootDirectory)) {
                        return "FNE"; // Folder Not Exists
                    }
                	
                    if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                        missingDirectories.append(subdirectory).append(", ");
                    } else {
                        //throw e;  // Handle other SFTP exceptions if necessary
                        return "FAIL: " + e.getMessage();
                    }
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

            // Output the count and list of SFTP files last modified 24 hours or exactly 24 hours ago
            if (!filesModifiedMoreThanOrExactlyHourAgo.isEmpty()) {
                // Number of files last modified 24 hours or exactly 24 hours ago on SFTP
                String countf = Integer.toString(filesModifiedMoreThanOrExactlyHourAgo.size());

                for (int i = 0; i < filesModifiedMoreThanOrExactlyHourAgo.size(); i++) {
                    String fileName = filesModifiedMoreThanOrExactlyHourAgo.get(i);

                    // Append file name to StringBuilder
                    filenames.append(fileName);

                    // Add a comma and space, but not after the last file name
                    if (i < filesModifiedMoreThanOrExactlyHourAgo.size() - 1) {
                        filenames.append(", ");
                    }
                }

                result = "SFTP files location is: " + sftpRootDirectory + "\nSFTP files Count is: " + countf + "\nSFTP file Names: " + filenames;
            } else {
                result = "NFP"; // No files present that are last modified 24 hours or exactly 24 hours ago on SFTP
            }

            // Add missing directory information if any
            if (missingDirectories.length() > 0) {
                // Remove the last comma and space
                missingDirectories.setLength(missingDirectories.length() - 2);
                result += "\nMissing directories: " + missingDirectories;
            }

        } catch (JSchException e) {
            // e.printStackTrace();
            return "FAIL: " + e.getMessage();
        } finally {
            // Close the SFTP channel and session
            if (channelSftp != null) {
                channelSftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }

        return result;
    }

    // Recursive method to fetch files from the directory and its sub-directories
    private static void fetchFilesFromDirectory(ChannelSftp channelSftp, String directoryPath, List<String> filesModifiedMoreThanOrExactlyHourAgo) throws SftpException {
        // List all files and directories in the given SFTP directory
        Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls(directoryPath);

        // Get the current time minus 24 hours for SFTP files
        Instant twentyfourHourAgo = Instant.now().minus(2, ChronoUnit.MINUTES);

        for (ChannelSftp.LsEntry entry : fileList) {
            String fullPath = directoryPath + "/" + entry.getFilename();
            
            if (entry.getAttrs().isDir()) {
                // Ignore "." and ".." directories
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    // Recursively fetch files from the sub-directory
                   fetchFilesFromDirectory(channelSftp, fullPath, filesModifiedMoreThanOrExactlyHourAgo);
                }
            } else {
                // Get the last modification time of the file (in seconds, converted to Instant)
                Instant lastModifiedTime = Instant.ofEpochSecond(entry.getAttrs().getMTime());

                // Check if the file was last modified 24 hours ago or exactly 24 hours ago
                if (!lastModifiedTime.isAfter(twentyfourHourAgo)) {
                    // Add the file name to the list
                    filesModifiedMoreThanOrExactlyHourAgo.add(fullPath);
                }
            }
        }
    }

}



