package org.ardnimahc.maven.plugin;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.DirectoryScanner;
import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class FormattingHelper {

    private static MessageDigest sha;

    static {
        try {
            sha = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException ignored) {}
    }


    private final Log log;
    private final Integer indentSize;
    private final boolean useTabs;

    public FormattingHelper(Log log, Integer indentSize, boolean useTabs) {
        this.log = log;
        this.indentSize = indentSize;
        this.useTabs = useTabs;
    }

    /**
     * Scans the given directory for files to format, and returns them in an
     * array.  The files are only added to the array if they match a pattern
     * in the <tt>includes</tt> array, and <strong>do not</strong> match any
     * pattern in the <tt>excludes</tt> array.
     *
     * @param directory - Base directory from which we start scanning for files.
     *        Note that this must be the root directory of the project in order
     *        to obtain the pom.xml as part of the XML files. This is one other
     *        differentiator when we were looking for tools, anything we found
     *        remotely like this did not start at the root directory.
     * @param includes - A string array containing patterns that are used to
     *                   search for files that should be formatted.
     * @param excludes - A string array containing patterns that are used to
     *                   filter out files so that they are <strong>not</strong>
     *                   formatted.
     * @return - A string array containing all the files that should be
     *            formatted.
     **/
    public String[] getIncludedFiles(File directory,
                                     String[] includes,
                                     String[] excludes) {

        DirectoryScanner dirScanner = new DirectoryScanner();
        dirScanner.setBasedir(directory);
        dirScanner.setIncludes(includes);
        dirScanner.setExcludes(excludes);
        dirScanner.scan();

        String[] filesToFormat = dirScanner.getIncludedFiles();

        if (log.isDebugEnabled()) {

            if (useTabs) {
                log.debug("[xml formatter] Formatting with tabs...");
            } else {
                log.debug("[xml formatter] Formatting with spaces...");
            }

            log.debug("[xml formatter] Files:");
            for (String file : filesToFormat) {
                log.debug("[xml formatter] file<" + file
                        + "> is scheduled for formatting");
            }
        }

        return filesToFormat;
    }

    /**
     * Formats the provided file, writing it back to it's original location.
     * @param formatFile - File to be formatted. The output file is the same as
     *        the input file. Please be sure that you have your files in
     *        a revision control system (and saved before running this plugin).
     **/
    public void format(File formatFile) {

        if (formatFile.exists() && formatFile.isFile()) {

            InputStream inputStream = null;
            Document xmlDoc = null;
            XMLWriter xmlWriter = null;

            try {
                inputStream = new FileInputStream(formatFile);

                SAXReader reader = new SAXReader();
                xmlDoc = reader.read(inputStream);

                log.debug("[xml formatter] Successfully parsed file: " + formatFile);

            } catch(Throwable t) {
                throw new RuntimeException("[xml formatter] Failed to parse..." + t.getMessage(), t);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch(Throwable tr) {
                        // intentially exception hiding for failures on close....
                    }
                }
            }

            FileOutputStream fos = null;
            File tmpFile = null;

            try {
                tmpFile = File.createTempFile("xmlFormatter", ".xml");
                fos = new FileOutputStream(tmpFile);
                final OutputFormat outputFormat = OutputFormat.createPrettyPrint();
                outputFormat.setIndentSize(indentSize);
                outputFormat.setNewLineAfterDeclaration(false);
                outputFormat.setPadText(false);
                xmlWriter = new XMLWriter(fos, outputFormat);
                xmlWriter.write(xmlDoc);
                xmlWriter.flush();

            } catch(Throwable t) {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                throw new RuntimeException("[xml formatter] Failed to parse..." + t.getMessage(), t);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch(Throwable t) {
                        // intentially exception hiding for failures on close....
                    }
                }
                if(xmlWriter!=null) {
                    try {
                        xmlWriter.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }

            // Now that we know that the indent is set to four spaces, we can either
            // keep it like that or change them to tabs depending on which 'mode' we
            // are in.

            if (useTabs) {
                indentFile(tmpFile);
            }

            // Copy tmpFile to formatFile, but only if the content has actually changed
            String tmpFileHash = getSha1(tmpFile);
            String formatFileHash = getSha1(formatFile);
            if (tmpFileHash != null && formatFileHash != null && tmpFileHash.equals(formatFileHash)) {
                // Exact match, so skip
                log.info("[xml formatter] File unchanged after formatting: " + formatFile);
                tmpFile.delete();
                return;
            }
            // To get here indicates a hash comparison failure, or the file has modified after formatting. Copy the bytes
            FileInputStream source = null;
            FileOutputStream destination = null;
            try {
                source = new FileInputStream(tmpFile);
                destination = new FileOutputStream(formatFile);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = source.read(buffer)) != -1) {
                    destination.write(buffer, 0, bytesRead); // write
                }
                log.info("[xml formatter] File reformatted: " + formatFile);
            } catch (IOException ioe) {
                log.error("[xml formatter] File copying failed for: " + tmpFile + " -> " + formatFile);
            } finally {
                if (source != null) {
                    try {
                        source.close();
                    } catch (IOException ignored) {}
                }
                if (destination != null) {
                    try {
                        destination.close();
                    } catch (IOException ignored) {}
                }
                tmpFile.delete();
            }
        } else {
            log.info("[xml formatter] File was not valid: " + formatFile + "; skipping");
        }
    }

    /**
     * Indents the file using tabs, writing it back to its original location.  This method
     * is only called if useTabs is set to true.
     * @param file
     *          The file to be indented using tabs.
     **/
    private void indentFile(File file) {

        List<String> temp = new ArrayList<String>();  // a temporary list to hold the lines
        BufferedReader reader = null;
        BufferedWriter writer = null;

        // Read the file, and replace the four spaces with tabs.
        try {
            reader= new BufferedReader(new FileReader(file));
            String line = null;

            while ((line = reader.readLine()) != null) {
                temp.add(line.replaceAll("[\\s]{" + indentSize + "}", "\t"));
            }

            writer = new BufferedWriter(new FileWriter(file));

            for (String ln : temp) {
                writer.write(ln);
                writer.newLine();
            }
        } catch (Throwable t) {
            throw new RuntimeException("[xml formatter] Failed to read file..." + t.getMessage(), t);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable t) {
                    // Intentionally catching exception...
                }
            }

            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (Throwable t) {
                    // Intentionally catching exception...
                }
            }
        }
    }

    private String getSha1(File file) {
        FileInputStream fis = null;
        byte[] dataBytes = new byte[1024];
        byte[] sha1Bytes = null;
        int read = 0;

        if (sha == null) {
            return null;
        }

        try {
            fis = new FileInputStream(file);
            while ((read = fis.read(dataBytes)) != -1) {
                sha.update(dataBytes, 0, read);
            };

            sha1Bytes = sha.digest();
        } catch (IOException ioe) {
            return null;
        } finally {
            sha.reset();
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                    return null;
                }
            }
        }

        StringBuffer sha1AsHex = new StringBuffer("");
        for (int i = 0; i < sha1Bytes.length; i++) {
            sha1AsHex.append(Integer.toString((sha1Bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sha1AsHex.toString();
    }

}
